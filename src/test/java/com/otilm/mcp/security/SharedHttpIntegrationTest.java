package com.otilm.mcp.security;

import com.otilm.mcp.McpServerApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {McpServerApplication.class, SharedHttpIntegrationTest.Transport.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.ai.mcp.server.stdio=false",
                "spring.ai.mcp.server.protocol=STATELESS",
                "ilm.api.auth.method=oauth-delegation",
                "ilm.api.url=https://ilm.test/api",
                "ilm.mcp.security.enabled=true",
                "ilm.mcp.security.issuer=https://auth.test/realm",
                "ilm.mcp.security.resource=https://mcp.test/mcp",
                "ilm.mcp.security.jwk-set-uri=https://auth.test/jwks",
                "ilm.mcp.security.token-uri=https://auth.test/token",
                "ilm.mcp.security.client-id=ilm-mcp",
                "ilm.mcp.security.client-secret=secret-token-response-must-never-leak",
                "ilm.mcp.access=read-only"})
@ExtendWith(OutputCaptureExtension.class)
class SharedHttpIntegrationTest {
    static final OAuthFixture FIXTURE;
    static {
        try {
            FIXTURE = new OAuthFixture();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    static final Set<String> API_THREADS = ConcurrentHashMap.newKeySet();
    @Value("${local.server.port}")
    int port;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @TestConfiguration(proxyBeanMethods = false)
    static class Transport {
        @Bean
        @Primary
        ClientHttpRequestFactory fixtureHttpRequestFactory() {
            return (uri, method) -> {
                if (uri.getHost().equals("ilm.test")) {
                    API_THREADS.add(Thread.currentThread().getName());
                }
                return FIXTURE.factory.createRequest(uri, method);
            };
        }
    }

    @AfterAll
    static void close() {
        FIXTURE.close();
    }

    @BeforeEach
    void reset() {
        FIXTURE.server.resetRequests();
        API_THREADS.clear();
    }

    @Test
    void metadataHealthAndAllMcpMethodsHaveIndependentAuthentication() throws Exception {
        assertThat(client
                .send(HttpRequest.newBuilder(endpoint("/.well-known/oauth-protected-resource/mcp")).GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .body()).contains(OAuthFixture.RESOURCE);
        assertThat(client
                .send(HttpRequest.newBuilder(endpoint("/actuator/health")).GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .statusCode()).isEqualTo(200);
        for (String method : new String[]{"initialize", "tools/list", "ping"}) {
            var response = client.send(request(null, method, "{}"), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate"))
                    .hasValueSatisfying(value -> assertThat(value).contains("resource_metadata"));
        }
        assertThat(FIXTURE.server.getAllServeEvents()).isEmpty();
    }

    @Test
    void actuatorInfoRequiresAuthenticationInSharedHttpMode() throws Exception {
        var response = client
                .send(HttpRequest.newBuilder(endpoint("/actuator/info")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .hasValueSatisfying(value -> assertThat(value).contains("resource_metadata"));
        assertThat(FIXTURE.server.getAllServeEvents()).isEmpty();
    }

    @Test
    void realHttpPathVariantsNeverDispatchWithoutAuthentication() throws Exception {
        for (String path : new String[]{"/mcp/", "/mcp;v=1", "/%6dcp", "//mcp", "/actuator/env", "/error"}) {
            var request = HttpRequest
                    .newBuilder(endpoint(path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                    .build();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isBetween(400, 499);
        }
        assertThat(FIXTURE.server.getAllServeEvents()).isEmpty();
    }

    @Test
    void twoInterleavedUsersOnlySendTheirOwnExchangedTokenThroughRealHttp() throws Exception {
        String alice = user("alice", 0);
        String bob = user("bob", 0);
        try (var block = FIXTURE.block(downstream("alice"))) {
            var first = client
                    .sendAsync(request(alice, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = client
                    .sendAsync(request(bob, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                            HttpResponse.BodyHandlers.ofString());
            var bobResult = second.get(10, TimeUnit.SECONDS);
            assertThat(bobResult.statusCode()).isEqualTo(200);
            assertThat(bobResult.body()).contains("bob-group").doesNotContain("alice-group");
            assertThat(first.isDone()).isFalse();
            block.close();
            assertThat(first.get(10, TimeUnit.SECONDS).body()).contains("alice-group").doesNotContain("bob-group");
            assertThat(bobResult.headers().firstValue("Mcp-Session-Id")).isEmpty();
            assertThat(API_THREADS).hasSizeGreaterThanOrEqualTo(2);
            for (int i = 0; i < 3; i++) {
                assertThat(client
                        .send(request(bob, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                                HttpResponse.BodyHandlers.ofString())
                        .body()).contains("bob-group");
            }
        }
        FIXTURE.server.verify(5, postRequestedFor(urlEqualTo("/token")));
        assertThat(FIXTURE.server.findAll(getRequestedFor(urlEqualTo("/api/v1/groups"))))
                .allSatisfy(request -> assertThat(request.getHeader("Authorization")).doesNotContain(alice, bob));
    }

    @Test
    void failuresCancellationAndFreshRequestsCannotReuseAnotherUsersCredentials(CapturedOutput output)
            throws Exception {
        String alice = user("failing-user", 800);
        String bob = user("next-user", 0);
        FIXTURE.server
                .stubFor(get("/api/v1/groups")
                        .withHeader("Authorization", equalTo("Bearer " + downstream("failing-user")))
                        .willReturn(forbidden().withBody(OAuthFixture.SENTINEL)));
        var failed = client
                .send(request(alice, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(failed.body()).doesNotContain(OAuthFixture.SENTINEL);
        assertThat(client
                .send(request(bob, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                        HttpResponse.BodyHandlers.ofString())
                .body()).contains("next-user-group");
        String slow = user("cancelled-user", 0);
        try (var block = FIXTURE.block(downstream("cancelled-user"))) {
            var cancelled = client
                    .sendAsync(request(slow, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(block.entered.await(5, TimeUnit.SECONDS)).isTrue();
            cancelled.cancel(true);
            block.close();
            assertThat(client
                    .send(request(null, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                            HttpResponse.BodyHandlers.ofString())
                    .statusCode()).isEqualTo(401);
            assertThat(client
                    .send(request(bob, "tools/call", "{\"name\":\"list_groups\",\"arguments\":{}}"),
                            HttpResponse.BodyHandlers.ofString())
                    .body()).contains("next-user-group");
        }
        assertThat(output).doesNotContain(OAuthFixture.SENTINEL, alice, bob);
    }

    @Test
    void initializeAndCatalogRemainStatelessAndRespectOperatorToolAccess() throws Exception {
        String source = user("catalog", 0);
        var initialize = client
                .send(request(source, "initialize",
                        "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}"),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(initialize.statusCode()).isEqualTo(200);
        assertThat(initialize.body()).doesNotContain("\"tasks\"");
        var catalog = client.send(request(source, "tools/list", "{}"), HttpResponse.BodyHandlers.ofString());
        assertThat(catalog.body())
                .contains("list_groups")
                .doesNotContain("\"name\":\"create_group\"", "\"name\":\"export_private_key\"");
        assertThat(client.send(request(source, "ping", "{}"), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(200);
        FIXTURE.server.verify(3, postRequestedFor(urlEqualTo("/token")));
        FIXTURE.server.verify(0, getRequestedFor(urlEqualTo("/api/v1/groups")));
    }

    String user(String name, int delay) throws Exception {
        String source = FIXTURE.token(name, OAuthFixture.RESOURCE, Map.of());
        String downstream = downstream(name);
        FIXTURE.server
                .stubFor(post("/token")
                        .withFormParam("subject_token", equalTo(source))
                        .willReturn(okJson("{\"access_token\":\"" + downstream
                                + "\",\"token_type\":\"Bearer\",\"issued_token_type\":\"urn:ietf:params:oauth:token-type:access_token\"}")));
        FIXTURE.server
                .stubFor(get("/api/v1/groups")
                        .withHeader("Authorization", equalTo("Bearer " + downstream))
                        .willReturn(okJson(
                                "[{\"uuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"" + name + "-group\"}]")
                                .withFixedDelay(delay)));
        return source;
    }

    String downstream(String name) throws Exception {
        return FIXTURE.token(name, "ilm", Map.of("exp", 4000000000L));
    }

    URI endpoint(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    HttpRequest request(String token, String method, String params) {
        var builder = HttpRequest
                .newBuilder(endpoint("/mcp"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-06-18");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params
                                + "}"))
                .build();
    }
}
