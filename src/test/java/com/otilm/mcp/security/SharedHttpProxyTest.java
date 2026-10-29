package com.otilm.mcp.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.ResponseSpec;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class SharedHttpProxyTest {
    private static final String LOOPBACK = "127.0.0.1";
    // Nothing resolves this name. The JDK client leaves that to the proxy, and the proxy answers from its stubs.
    private static final String ORIGIN = "http://target.invalid:8080";
    private static final String PROXY_USER = "proxy-user-must-not-leak";
    private static final String PROXY_SECRET = "proxy-secret-must-not-leak";
    private static final String USER_INFO = PROXY_USER + ":" + PROXY_SECRET + "@";
    private static final String TUNNELING_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";

    private String tunnelingSchemes;

    // A proxy with credentials clears this process-wide setting, so every test puts it back.
    @BeforeEach
    void rememberTunnelingSchemes() {
        tunnelingSchemes = System.getProperty(TUNNELING_SCHEMES);
    }

    @AfterEach
    void restoreTunnelingSchemes() {
        if (tunnelingSchemes == null) {
            System.clearProperty(TUNNELING_SCHEMES);
        } else {
            System.setProperty(TUNNELING_SCHEMES, tunnelingSchemes);
        }
    }

    /** A forward proxy and a server a client can reach directly. Both bind to the IPv4 loopback address. */
    private static final class Servers implements AutoCloseable {
        final WireMockServer proxy = start(options().enableBrowserProxying(true));
        final WireMockServer target = start(options());

        /** A proxy URL for the given user info, which is empty or ends in an at sign. */
        String proxyUrl(String userInfo) {
            return "http://" + userInfo + LOOPBACK + ":" + proxy.port();
        }

        String targetUrl(String path) {
            return "http://" + LOOPBACK + ":" + target.port() + path;
        }

        @Override
        public void close() {
            proxy.stop();
            target.stop();
        }
    }

    @Test
    void routesRequestsThroughTheProxy() {
        try (Servers servers = new Servers()) {
            servers.proxy.stubFor(get("/ok").willReturn(ok("from-proxy")));
            RestClient client = client(viaProxy(servers.proxyUrl("")));

            assertThat(client.get().uri(ORIGIN + "/ok").retrieve().body(String.class)).isEqualTo("from-proxy");

            assertThat(servers.proxy.getAllServeEvents())
                    .singleElement()
                    .satisfies(event -> assertThat(event.getRequest().getAbsoluteUrl()).isEqualTo(ORIGIN + "/ok"));
        }
    }

    // Loopback hosts bypass the proxy even though NO_PROXY is unset, as in ILM Core's JVM.
    @Test
    void keepsALoopbackTargetDirectWhenNoProxyIsUnset() {
        try (Servers servers = new Servers()) {
            servers.target.stubFor(get("/ok").willReturn(ok("from-target")));
            RestClient client = client(viaProxy(servers.proxyUrl("")));

            assertThat(client.get().uri(servers.targetUrl("/ok")).retrieve().body(String.class))
                    .isEqualTo("from-target");

            assertThat(servers.proxy.getAllServeEvents()).isEmpty();
            servers.target.verify(1, getRequestedFor(urlEqualTo("/ok")));
        }
    }

    @Test
    void answersTheProxyChallengeAndNeverLogsTheCredentials(CapturedOutput output) {
        String basic = "Basic " + Base64
                .getEncoder()
                .encodeToString((PROXY_USER + ":" + PROXY_SECRET).getBytes(StandardCharsets.UTF_8));
        Logger logger = (Logger) LoggerFactory.getLogger("com.otilm.mcp");
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try (Servers servers = new Servers()) {
            servers.proxy
                    .stubFor(get("/secured")
                            .atPriority(1)
                            .withHeader("Proxy-Authorization", equalTo(basic))
                            .willReturn(ok("through-the-proxy")));
            servers.proxy
                    .stubFor(get("/secured")
                            .atPriority(2)
                            .willReturn(aResponse()
                                    .withStatus(407)
                                    .withHeader("Proxy-Authenticate", "Basic realm=\"test\"")));
            RestClient client = client(viaProxy(servers.proxyUrl(USER_INFO)));

            assertThat(client.get().uri(ORIGIN + "/secured").retrieve().body(String.class))
                    .isEqualTo("through-the-proxy");

            servers.proxy.verify(2, getRequestedFor(urlEqualTo("/secured")));
            String logged = events.list
                    .stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining("\n"));
            assertThat(logged)
                    .contains("outbound proxy")
                    .doesNotContain(PROXY_USER, PROXY_SECRET, LOOPBACK, String.valueOf(servers.proxy.port()));
            assertThat(output).contains("outbound proxy").doesNotContain(PROXY_USER, PROXY_SECRET);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    // The JDK client also asks its authenticator about origin servers, and a 401 that the proxy relays reaches it as
    // an origin's own. With an authenticator installed it fails a request whose origin sends a Basic challenge that
    // nobody answers, and it must neither retry nor send credentials.
    @Test
    void neverGivesTheProxyCredentialsToAnOriginServer() {
        try (Servers servers = new Servers()) {
            servers.proxy.stubFor(get("/protected").willReturn(basicChallenge()));
            RestClient client = client(viaProxy(servers.proxyUrl(USER_INFO)));
            ResponseSpec response = client.get().uri(ORIGIN + "/protected").retrieve();

            assertThatThrownBy(response::toBodilessEntity).isInstanceOf(ResourceAccessException.class);

            servers.proxy.verify(1, getRequestedFor(urlEqualTo("/protected")));
            servers.proxy
                    .verify(0, getRequestedFor(urlEqualTo("/protected")).withHeader("Authorization", matching(".+")));
        }
    }

    // A proxy without credentials installs no authenticator, so an origin challenge reaches the caller as a response.
    @Test
    void returnsAnOriginChallengeWhenTheProxyHasNoCredentials() {
        try (Servers servers = new Servers()) {
            servers.proxy.stubFor(get("/protected").willReturn(basicChallenge()));
            RestClient client = client(viaProxy(servers.proxyUrl("")));

            int status = client
                    .get()
                    .uri(ORIGIN + "/protected")
                    .exchange((request, response) -> response.getStatusCode().value());

            assertThat(status).isEqualTo(401);
        }
    }

    // The JDK reads this setting once, when the first HttpClient of the process is built, so a test can only check
    // that it is cleared, not that Basic authentication then works through a tunnel.
    @Test
    void clearsTheTunnelingRestrictionOnlyWhenTheProxyHasCredentials() {
        System.setProperty(TUNNELING_SCHEMES, "Basic");

        client(viaProxy("http://proxy.example.invalid:3128"));
        assertThat(System.getProperty(TUNNELING_SCHEMES)).isEqualTo("Basic");

        client(new OutboundProperties("", "", "http://" + USER_INFO + "proxy.example.invalid:3129", ""));
        assertThat(System.getProperty(TUNNELING_SCHEMES)).isEmpty();
    }

    private static ResponseDefinitionBuilder basicChallenge() {
        return aResponse().withStatus(401).withHeader("WWW-Authenticate", "Basic realm=\"origin\"");
    }

    private static WireMockServer start(WireMockConfiguration options) {
        WireMockServer server = new WireMockServer(options.bindAddress(LOOPBACK).dynamicPort());
        server.start();
        return server;
    }

    private static OutboundProperties viaProxy(String httpProxy) {
        return new OutboundProperties("", httpProxy, "", "");
    }

    private static RestClient client(OutboundProperties outbound) {
        var factory = new SharedSecurityConfiguration.Enabled()
                .sharedHttpRequestFactory(new SharedSecurityProperties(), outbound);
        return RestClient.builder().requestFactory(factory).build();
    }
}
