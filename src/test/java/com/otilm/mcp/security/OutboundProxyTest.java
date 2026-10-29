package com.otilm.mcp.security;

import java.io.IOException;
import java.net.Authenticator;
import java.net.Authenticator.RequestorType;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Arrays;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundProxyTest {
    private static final String HTTP_PROXY = "http://proxy.example.invalid:3128";
    private static final String HTTPS_PROXY = "http://user:p%40ss@secure.example.invalid:3129";
    private static final String UNSUPPORTED = "HTTP_PROXY and HTTPS_PROXY must be http://host:port URLs";
    private static final String SENTINEL = "must-not-leak";

    @Test
    void noneWhenUnset() {
        assertThat(OutboundProxy.from(new OutboundProperties("", "", "", ""))).isEmpty();
        assertThat(OutboundProxy.from(new OutboundProperties(null, null, null, null))).isEmpty();
        // Without a proxy there is nothing to exempt a host from.
        assertThat(OutboundProxy.from(new OutboundProperties("", " ", "\n", "localhost"))).isEmpty();
    }

    @Test
    void routesPerScheme() {
        OutboundProxy both = proxy(HTTP_PROXY, HTTPS_PROXY, "");
        assertRoutedThrough(both, "http://a.example.invalid/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(both, "https://a.example.invalid/x", "secure.example.invalid", 3129);

        OutboundProxy httpOnly = proxy(HTTP_PROXY, "", "");
        assertRoutedThrough(httpOnly, "http://a.example.invalid/x", "proxy.example.invalid", 3128);
        assertDirect(httpOnly, "https://a.example.invalid/x");

        OutboundProxy httpsOnly = proxy("", HTTPS_PROXY, "");
        assertDirect(httpsOnly, "http://a.example.invalid/x");
        assertRoutedThrough(httpsOnly, "https://a.example.invalid/x", "secure.example.invalid", 3129);
    }

    @Test
    void keepsSelectingTheProxyAfterAFailedConnection() {
        OutboundProxy proxy = proxy(HTTP_PROXY, "", "");
        ProxySelector selector = proxy.selector();
        URI uri = URI.create("http://a.example.invalid/x");

        selector.connectFailed(uri, new InetSocketAddress("proxy.example.invalid", 3128), new IOException());

        assertThat(selector.select(uri)).hasSize(1).doesNotContain(Proxy.NO_PROXY);
    }

    // The JDK resolves an unresolved address on each connection, so a change of the proxy's DNS name is followed.
    @Test
    void keepsTheProxyAddressUnresolved() {
        OutboundProxy proxy = proxy("http://127.0.0.1:3128", "", "");

        assertThat(proxy.selector().select(URI.create("http://a.example.invalid/x")))
                .singleElement()
                .satisfies(selected -> assertThat(selected.address())
                        .isInstanceOfSatisfying(InetSocketAddress.class,
                                address -> assertThat(address.isUnresolved()).isTrue()));
    }

    @Test
    void requestsWithoutAnHttpSchemeOrAHostAreDirect() {
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, ""), "ftp://a.example.invalid/x", "mailto:someone@example.invalid",
                "/relative", "http:///no-host");
    }

    @Test
    void matchesRequestSchemesIgnoringCase() {
        assertRoutedThrough(proxy(HTTP_PROXY, HTTPS_PROXY, ""), "HTTP://A.EXAMPLE.INVALID/x", "proxy.example.invalid",
                3128);
    }

    // ILM Core hands NO_PROXY to the JDK as http.nonProxyHosts, so an entry is one whole host name or a pattern in
    // which `*` stands for any characters.
    @ParameterizedTest
    @ValueSource(strings = {
            "http://core:8080/api",
            "http://keycloak.idp.svc:8080/x",
            "http://KEYCLOAK.IDP.SVC/x",
            "http://keycloak.idp.svc.cluster.local:8080/x"})
    void noProxyExemptsListedHostsAndPatterns(String uri) {
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, "core,*.svc,*.cluster.local"), uri);
    }

    // A plain entry is exactly one host, the dot of `*.svc` is a literal dot, and `*.svc` needs it.
    @Test
    void noProxyLeavesOtherHostsOnTheProxy() {
        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, "core,*.svc,*.cluster.local");
        assertRoutedThrough(proxy, "https://idp.example.com/x", "secure.example.invalid", 3129);
        assertRoutedThrough(proxy, "http://api.core/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://svc/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://mysvc/x", "proxy.example.invalid", 3128);
    }

    // The JDK has no domain-suffix form, so a leading dot is an ordinary character and `.svc` matches no host.
    @Test
    void aLeadingDotIsNotSpecial() {
        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, ".svc");
        assertRoutedThrough(proxy, "http://keycloak.idp.svc/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://svc/x", "proxy.example.invalid", 3128);
    }

    @Test
    void aTrailingWildcardMatchesAnAddressPrefix() {
        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, "10.*");
        assertDirect(proxy, "http://10.0.0.1/x");
        assertRoutedThrough(proxy, "http://110.0.0.1/x", "proxy.example.invalid", 3128);
    }

    // As in the JDK, only a leading or a trailing `*` is a wildcard, and a `*` inside an entry is a star.
    @Test
    void aWildcardInTheMiddleIsLiteral() {
        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, "a*b");
        assertRoutedThrough(proxy, "http://axb/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://a.b/x", "proxy.example.invalid", 3128);
    }

    @Test
    void wildcardsOnBothEndsMatchASubstring() {
        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, "*idp*");
        assertDirect(proxy, "http://kc.idp.svc/x", "http://idp/x");
        assertRoutedThrough(proxy, "http://kc.other.svc/x", "proxy.example.invalid", 3128);
    }

    // ILM Core hands a bar inside NO_PROXY to the JDK, where it separates entries like a comma does.
    @Test
    void aBarSeparatesEntriesLikeAComma() {
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, "core|*.svc"), "http://core/x", "http://kc.idp.svc/x");
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, "core,*.svc|10.*"), "http://core/x", "http://kc.idp.svc/x",
                "http://10.0.0.1/x");
    }

    // ILM Core ignores NO_PROXY for https requests when only HTTPS_PROXY is set, because the JDK reads its http setting
    // for them. Here NO_PROXY covers both schemes.
    @Test
    void noProxyAppliesToHttpsRequestsWhenOnlyAnHttpsProxyIsSet() {
        OutboundProxy proxy = proxy("", HTTPS_PROXY, "*.svc");
        assertDirect(proxy, "https://kc.idp.svc/x", "https://localhost/x");
        assertRoutedThrough(proxy, "https://idp.example.com/x", "secure.example.invalid", 3129);
    }

    // The JDK adds its loopback patterns to the nonProxyHosts of ILM Core, so they bypass the proxy whatever NO_PROXY
    // holds.
    @ParameterizedTest
    @ValueSource(strings = {"", "example.org"})
    void loopbackIsAlwaysDirect(String noProxy) {
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, noProxy), "http://localhost/x", "https://LOCALHOST/x",
                "http://127.0.0.1/x", "http://127.1.2.3:8080/x", "http://[::1]/x", "http://0.0.0.0/x",
                "http://[::0]/x");
    }

    @Test
    void otherAddressesStayOnTheProxy() {
        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, "");
        assertRoutedThrough(proxy, "http://128.0.0.1/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://127example.com/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://localhost.example.invalid/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "https://[::2]/x", "secure.example.invalid", 3129);
    }

    @Test
    void noProxyEntriesAreTrimmedAndCaseInsensitive() {
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, "*.SVC"), "http://kc.idp.svc/x");

        OutboundProxy proxy = proxy(HTTP_PROXY, HTTPS_PROXY, " core , ,*.svc ");
        assertDirect(proxy, "http://core/x", "http://kc.idp.svc/x");
        assertRoutedThrough(proxy, "http://a.example.invalid/x", "proxy.example.invalid", 3128);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "**", "core, *"})
    void noProxyStarExemptsAll(String noProxy) {
        assertDirect(proxy(HTTP_PROXY, HTTPS_PROXY, noProxy), "http://a.example.invalid/x",
                "https://b.example.invalid/x", "http://192.0.2.1:8080/x");
    }

    @Test
    void authenticatesOnlyTheProxy() {
        Authenticator authenticator = proxy(HTTP_PROXY, HTTPS_PROXY, "").authenticator();

        PasswordAuthentication granted = ask(authenticator, "secure.example.invalid", 3129, RequestorType.PROXY);
        assertThat(granted.getUserName()).isEqualTo("user");
        assertThat(granted.getPassword()).containsExactly('p', '@', 's', 's');

        assertThat(ask(authenticator, "secure.example.invalid", 3129, RequestorType.SERVER)).isNull();
        assertThat(ask(authenticator, "secure.example.invalid", 3130, RequestorType.PROXY)).isNull();
        assertThat(ask(authenticator, "other.example.invalid", 3129, RequestorType.PROXY)).isNull();
        // The HTTP proxy carries no credentials, so nothing is given to it.
        assertThat(ask(authenticator, "proxy.example.invalid", 3128, RequestorType.PROXY)).isNull();
    }

    // The JDK documents that a caller zeroes the password it gets, which must not blank the proxy's own.
    @Test
    void givesEachRequestItsOwnCopyOfTheCredentials() {
        Authenticator authenticator = proxy(HTTP_PROXY, HTTPS_PROXY, "").authenticator();

        PasswordAuthentication first = ask(authenticator, "secure.example.invalid", 3129, RequestorType.PROXY);
        Arrays.fill(first.getPassword(), '\0');

        PasswordAuthentication second = ask(authenticator, "secure.example.invalid", 3129, RequestorType.PROXY);
        assertThat(second.getPassword()).containsExactly('p', '@', 's', 's');
    }

    @Test
    void acceptsAUserWithoutAPassword() {
        Authenticator authenticator = proxy("http://only-user@proxy.example.invalid:3128", "", "").authenticator();

        PasswordAuthentication granted = ask(authenticator, "proxy.example.invalid", 3128, RequestorType.PROXY);
        assertThat(granted.getUserName()).isEqualTo("only-user");
        assertThat(granted.getPassword()).isEmpty();
    }

    @Test
    void needsNeitherNoProxyNorCredentials() {
        OutboundProxy proxy = OutboundProxy
                .from(new OutboundProperties(null, "http://@proxy.example.invalid:3128", null, null))
                .orElseThrow();

        assertRoutedThrough(proxy, "http://a.example.invalid/x", "proxy.example.invalid", 3128);
        assertRoutedThrough(proxy, "http://null/x", "proxy.example.invalid", 3128);
        assertThat(ask(proxy.authenticator(), "proxy.example.invalid", 3128, RequestorType.PROXY)).isNull();
    }

    @Test
    void acceptsATrailingSlashAnUpperCaseSchemeAndSurroundingWhitespace() {
        assertRoutedThrough(proxy(" HTTP://proxy.example.invalid:3128/\n", "", ""), "http://a.example.invalid/x",
                "proxy.example.invalid", 3128);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://proxy:443",
            "proxy-without-scheme",
            "proxy:3128",
            "http://proxy",
            "http://proxy:0",
            "http://proxy:99999",
            "http://",
            "http://:3128",
            "http://under_score:3128",
            "http://proxy:port",
            "http://user:" + SENTINEL + "@proxy",
            "https://user:" + SENTINEL + "@proxy:443",
            "http://user:p%ss-" + SENTINEL + "@proxy:3128",
            "http://user:p ss-" + SENTINEL + "@proxy:3128",
            "http://user:p@ss-" + SENTINEL + "@proxy:3128"})
    void rejectsUnsupportedProxyUrls(String value) {
        OutboundProperties inHttpProxy = new OutboundProperties("", value, "", "");
        OutboundProperties inHttpsProxy = new OutboundProperties("", "", value, "");

        assertUnsupported(() -> OutboundProxy.from(inHttpProxy));
        assertUnsupported(() -> OutboundProxy.from(inHttpsProxy));
    }

    @Test
    void hidesItsSettingsInToString() {
        assertThat(proxy(HTTP_PROXY, HTTPS_PROXY, "core")).hasToString("OutboundProxy[redacted]");
    }

    private static void assertUnsupported(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(UNSUPPORTED)
                .hasMessageNotContaining(SENTINEL)
                .hasNoCause();
    }

    private static OutboundProxy proxy(String http, String https, String noProxy) {
        return OutboundProxy.from(new OutboundProperties("", http, https, noProxy)).orElseThrow();
    }

    private static PasswordAuthentication ask(Authenticator authenticator, String host, int port, RequestorType type) {
        return authenticator
                .requestPasswordAuthenticationInstance(host, null, port, "http", "realm", "Basic", null, type);
    }

    private static void assertRoutedThrough(OutboundProxy proxy, String uri, String host, int port) {
        assertThat(proxy.selector().select(URI.create(uri))).as(uri).singleElement().satisfies(selected -> {
            assertThat(selected.type()).isEqualTo(Proxy.Type.HTTP);
            assertThat(selected.address())
                    .isInstanceOfSatisfying(InetSocketAddress.class,
                            address -> assertThat(address)
                                    .extracting(InetSocketAddress::getHostString, InetSocketAddress::getPort)
                                    .containsExactly(host, port));
        });
    }

    private static void assertDirect(OutboundProxy proxy, String... uris) {
        for (String uri : uris) {
            assertThat(proxy.selector().select(URI.create(uri))).as(uri).containsExactly(Proxy.NO_PROXY);
        }
    }
}
