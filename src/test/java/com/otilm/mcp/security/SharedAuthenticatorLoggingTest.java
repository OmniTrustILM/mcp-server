package com.otilm.mcp.security;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

/** What the authenticator tells an operator about a failing key set or token endpoint, and what it keeps quiet. */
@ExtendWith(OutputCaptureExtension.class)
class SharedAuthenticatorLoggingTest {
    private static final String BODY = "endpoint-body-must-not-leak";
    private static final String EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String UNREADABLE = "The token endpoint could not be reached or its answer could not be read "
            + "for the token exchange (";
    private static final String KEY_SET = "The key set could not be retrieved or read (JwtException < "
            + "RemoteKeySourceException < ";

    @ParameterizedTest
    @CsvSource({
            "true, 401, actor-token request",
            "true, 503, actor-token request",
            "false, 401, token exchange",
            "false, 500, token exchange"})
    void warnsOnceWithTheStatusWhenTheTokenEndpointRefuses(boolean actorRequest, int status, String request,
            CapturedOutput output) throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            f.properties.setExchangeMode(SharedSecurityProperties.ExchangeMode.DELEGATION);
            String actor = f.token("service-account", "ilm-mcp", Map.of());
            ResponseDefinitionBuilder refusal = aResponse().withStatus(status).withBody(BODY);
            f.server
                    .stubFor(post("/token")
                            .withFormParam("grant_type", equalTo("client_credentials"))
                            .willReturn(actorRequest ? refusal : okJson(bearer(actor))));
            f.server.stubFor(post("/token").withFormParam("grant_type", equalTo(EXCHANGE)).willReturn(refusal));
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of("may_act", Map.of("client_id", "ilm-mcp")));

            assertFails(f.authenticator(), source, status < 500 ? 401 : 503);

            assertThat(warnings.messages())
                    .containsExactly("The token endpoint answered HTTP " + status + " to the " + request);
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, BODY, source, actor);
        }
    }

    @Test
    void warnsWithTheCauseChainWhenTheTokenEndpointIsUnreachable(CapturedOutput output) throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            SharedAuthenticator authenticator = new SharedAuthenticator(f.properties, new RestTemplate(f.factory),
                    RestClient.builder().requestFactory(closedPort()).build());

            assertFails(authenticator, source, 503);

            assertThat(warnings.messages())
                    .singleElement()
                    .satisfies(message -> assertThat(message)
                            .startsWith(UNREADABLE + "ResourceAccessException < ConnectException")
                            .endsWith(")"));
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, source);
        }
    }

    // The shared client trusts the JDK's certificate authorities only, so a private one fails the TLS handshake.
    @Test
    void warnsWithTheCauseChainWhenTheTokenEndpointCertificateIsNotTrusted(@TempDir Path directory,
            CapturedOutput output) throws Exception {
        try (OAuthFixture f = new OAuthFixture();
                HttpsFixture endpoint = new HttpsFixture(directory, "untrusted-token-endpoint");
                Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            f.properties.setTokenUri(URI.create(endpoint.url()).resolve("/token").toString());
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            SharedAuthenticator authenticator = new SharedAuthenticator(f.properties, new RestTemplate(f.factory),
                    RestClient.builder().requestFactory(sharedFactory()).build());

            assertFails(authenticator, source, 503);

            assertThat(warnings.messages())
                    .singleElement()
                    .satisfies(message -> assertThat(message)
                            .startsWith(UNREADABLE + "ResourceAccessException < SSLHandshakeException < "));
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, source);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/plain"})
    void warnsOnceWhenTheTokenEndpointAnswersWithABodyItCannotRead(String contentType, CapturedOutput output)
            throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            f.server
                    .stubFor(post("/token")
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", contentType)
                                    .withBody(BODY)));
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());

            assertFails(f.authenticator(), source, 503);

            assertThat(warnings.messages())
                    .singleElement()
                    .satisfies(message -> assertThat(message).startsWith(UNREADABLE).endsWith(")"));
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, BODY, source);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "not-bearer | The token endpoint's answer to the token exchange is not a Bearer access token of the "
                    + "expected type",
            "actor-not-bearer | The token endpoint's answer to the actor-token request is not a Bearer access token "
                    + "of the expected type",
            "echo | The exchanged token equals the subject token",
            "invalid | The exchanged token failed validation of its signature, issuer, audience, times or claims",
            "subject | The exchanged token is for another subject",
            "resource-audience | The exchanged token still carries the MCP resource audience",
            "standard-actor | The exchanged token's actor claim does not fit the exchange mode",
            "delegation-actor | The exchanged token's actor claim does not fit the exchange mode"})
    void warnsOnceNamingTheCheckThatRejectedTheExchange(String variant, String message, CapturedOutput output)
            throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            boolean delegation = variant.startsWith("delegation") || variant.startsWith("actor");
            String source = f
                    .token("alice", OAuthFixture.RESOURCE,
                            delegation ? Map.of("may_act", Map.of("client_id", "ilm-mcp")) : Map.of());
            String exchanged = switch (variant) {
                case "echo" -> source;
                case "invalid" -> f.token("alice", "other-audience", Map.of());
                case "subject" -> f.token("bob", "ilm", Map.of());
                case "resource-audience" ->
                    f.token("alice", "ilm", Map.of("aud", List.of("ilm", OAuthFixture.RESOURCE)));
                case "standard-actor" -> f.token("alice", "ilm", Map.of("act", Map.of("client_id", "ilm-mcp")));
                case "delegation-actor" -> f.token("alice", "ilm", Map.of("act", Map.of("client_id", "other")));
                default -> f.token("alice", "ilm", Map.of());
            };
            if (variant.equals("not-bearer")) {
                f.server.stubFor(post("/token").willReturn(okJson(answer(exchanged, "mac"))));
            } else {
                f.exchange(exchanged);
            }
            String actor = f.token("service-account", "ilm-mcp", Map.of());
            if (delegation) {
                f.properties.setExchangeMode(SharedSecurityProperties.ExchangeMode.DELEGATION);
                f.server
                        .stubFor(post("/token")
                                .withFormParam("grant_type", equalTo("client_credentials"))
                                .willReturn(
                                        okJson(answer(actor, variant.equals("actor-not-bearer") ? "mac" : "Bearer"))));
            }

            assertFails(f.authenticator(), source, 401);

            assertThat(warnings.messages()).containsExactly(message);
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, source, exchanged, actor);
        }
    }

    // Spring has no named exception for a 407, so the status is what tells a proxy that refuses MCP from another
    // refusal.
    @ParameterizedTest
    @CsvSource(delimiter = '|',
            value = {
                    "unavailable | InternalServerError, HTTP 500)",
                    "refused | HttpClientErrorException, HTTP 407)",
                    "not-a-key-set | ParseException",
                    "unreachable | ConnectException"})
    void warnsWithTheCauseChainWhenTheKeySetCannotBeUsed(String variant, String expected, CapturedOutput output)
            throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            if (variant.equals("unavailable")) {
                f.server.stubFor(get("/jwks").willReturn(serverError().withBody(BODY)));
            } else if (variant.equals("refused")) {
                f.server.stubFor(get("/jwks").willReturn(aResponse().withStatus(407).withBody(BODY)));
            } else if (variant.equals("not-a-key-set")) {
                f.server.stubFor(get("/jwks").willReturn(okJson("{\"keys\":\"" + BODY + "\"}")));
            }
            SharedAuthenticator authenticator = variant.equals("unreachable")
                    ? new SharedAuthenticator(f.properties, new RestTemplate(closedPort()),
                            RestClient.builder().requestFactory(f.factory).build())
                    : f.authenticator();

            assertFails(authenticator, source, 401);

            assertThat(warnings.messages())
                    .singleElement()
                    .satisfies(message -> assertThat(message).startsWith(KEY_SET).contains(expected).endsWith(")"));
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, BODY, source);
        }
    }

    @Test
    void warnsWithTheCauseChainWhenTheKeySetCertificateIsNotTrusted(@TempDir Path directory, CapturedOutput output)
            throws Exception {
        try (OAuthFixture f = new OAuthFixture();
                HttpsFixture endpoint = new HttpsFixture(directory, "untrusted-key-set");
                Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            f.properties.setJwkSetUri(endpoint.url());
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            SharedAuthenticator authenticator = new SharedAuthenticator(f.properties, new RestTemplate(sharedFactory()),
                    RestClient.builder().requestFactory(f.factory).build());

            assertFails(authenticator, source, 401);

            assertThat(warnings.messages())
                    .singleElement()
                    .satisfies(message -> assertThat(message)
                            .startsWith(KEY_SET + "ResourceAccessException < SSLHandshakeException < "));
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, source);
        }
    }

    // The key set answered the first request and then broke, so the exchanged token's key is unknown to the cache.
    @Test
    void warnsOnlyAboutTheKeySetWhenItBreaksTheCheckOfTheExchangedToken(CapturedOutput output) throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            String keys = new JWKSet(f.key.toPublicJWK()).toString();
            f.server
                    .stubFor(get("/jwks")
                            .inScenario("key set")
                            .whenScenarioStateIs(Scenario.STARTED)
                            .willReturn(okJson(keys))
                            .willSetStateTo("down"));
            f.server
                    .stubFor(get("/jwks")
                            .inScenario("key set")
                            .whenScenarioStateIs("down")
                            .willReturn(serverError().withBody(BODY)));
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            String exchanged = f.token("alice", "ilm", Map.of(), JWSAlgorithm.RS256, "rotated-key");
            f.exchange(exchanged);

            assertFails(f.authenticator(), source, 401);

            assertThat(warnings.messages())
                    .singleElement()
                    .satisfies(message -> assertThat(message).startsWith(KEY_SET));
            assertPrinted(output, warnings);
            assertThat(output).doesNotContain(OAuthFixture.SENTINEL, BODY, source, exchanged);
        }
    }

    @Test
    void logsAtMostOneKeySetWarningPerRetrievalAttempt() throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            f.server.stubFor(get("/jwks").willReturn(serverError().withBody(BODY)));
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            SharedAuthenticator authenticator = f.authenticator();

            for (int request = 0; request < 5; request++) {
                assertFails(authenticator, source, 401);
            }

            int attempts = f.server.findAll(getRequestedFor(urlEqualTo("/jwks"))).size();
            assertThat(attempts).isPositive();
            List<String> messages = warnings.messages();
            assertThat(messages).isNotEmpty().hasSizeLessThanOrEqualTo(attempts);
            assertThat(messages).filteredOn(message -> !message.startsWith(KEY_SET)).isEmpty();
        }
    }

    // Nimbus lets one request retrieve the key set at a time, and another gives up waiting after a while. That request
    // made no attempt of its own, whether it waits to check the user's token or, after a key rotation, the exchanged
    // one.
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void staysSilentForARequestThatMakesNoRetrievalAttempt(int heldRetrieval) throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            RSAKey rotated = new RSAKey.Builder(f.key.toRSAPublicKey()).keyID("rotated-key").build();
            f.server
                    .stubFor(get("/jwks")
                            .inScenario("rotation")
                            .whenScenarioStateIs(Scenario.STARTED)
                            .willReturn(okJson(new JWKSet(f.key.toPublicJWK()).toString()))
                            .willSetStateTo("rotated"));
            f.server
                    .stubFor(get("/jwks")
                            .inScenario("rotation")
                            .whenScenarioStateIs("rotated")
                            .willReturn(okJson(new JWKSet(List.of(f.key.toPublicJWK(), rotated)).toString())));
            AtomicInteger retrievals = new AtomicInteger();
            CountDownLatch retrieving = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ClientHttpRequestFactory held = (uri, method) -> {
                if (uri.getPath().equals("/jwks") && retrievals.incrementAndGet() == heldRetrieval) {
                    retrieving.countDown();
                    waitFor(release);
                }
                return f.factory.createRequest(uri, method);
            };
            SharedAuthenticator authenticator = new SharedAuthenticator(f.properties, new RestTemplate(held),
                    RestClient.builder().requestFactory(f.factory).build());
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            f.exchange(f.token("alice", "ilm", Map.of(), JWSAlgorithm.RS256, "rotated-key"));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<RequestCredential> retrieval = executor.submit(() -> authenticator.authenticate(source));
                assertThat(retrieving.await(10, TimeUnit.SECONDS)).isTrue();
                AtomicReference<Throwable> outcome = new AtomicReference<>();
                Thread waiting = new Thread(
                        () -> outcome.set(catchThrowable(() -> authenticator.authenticate(source))));
                waiting.start();
                await().atMost(Duration.ofSeconds(10)).until(() -> waitsForTheKeySet(waiting));
                waiting.interrupt();
                waiting.join(10_000);
                release.countDown();

                assertThat(retrieval.get(10, TimeUnit.SECONDS)).isNotNull();
                assertThat(outcome.get())
                        .isInstanceOfSatisfying(AuthenticationFailure.class,
                                failure -> assertThat(failure.status()).isEqualTo(401));
                assertThat(warnings.messages()).isEmpty();
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"malformed", "bad-signature", "unknown-key-id", "issuer", "audience", "expired", "scope", "act"})
    void staysSilentForFailuresTheTokenSenderControls(String variant, CapturedOutput output) throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            int status = 401;
            String source;
            switch (variant) {
                case "malformed" -> source = "not-a-jwt-" + BODY;
                case "bad-signature" -> {
                    try (OAuthFixture attacker = new OAuthFixture()) {
                        source = attacker.token("alice", OAuthFixture.RESOURCE, Map.of());
                    }
                }
                case "unknown-key-id" ->
                    source = f.token("alice", OAuthFixture.RESOURCE, Map.of(), JWSAlgorithm.RS256, "unknown");
                case "issuer" -> source = f.token("alice", OAuthFixture.RESOURCE, Map.of("iss", "https://other.test"));
                case "audience" -> source = f.token("alice", "ilm-mcp", Map.of());
                case "expired" -> source = f
                        .token("alice", OAuthFixture.RESOURCE,
                                Map.of("exp", Instant.now().minusSeconds(120).getEpochSecond()));
                case "scope" -> {
                    source = f.token("alice", OAuthFixture.RESOURCE, Map.of("scope", "other"));
                    status = 403;
                }
                default ->
                    source = f.token("alice", OAuthFixture.RESOURCE, Map.of("act", Map.of("client_id", "other")));
            }

            assertFails(f.authenticator(), source, status);

            assertThat(warnings.messages()).isEmpty();
            assertThat(output).doesNotContain(source);
        }
    }

    @Test
    void staysSilentWhenTheExchangeSucceeds() throws Exception {
        try (OAuthFixture f = new OAuthFixture(); Warnings warnings = new Warnings(SharedAuthenticator.class)) {
            f.exchange(f.token("alice", "ilm", Map.of()));

            assertThat(f.authenticator().authenticate(f.token("alice", OAuthFixture.RESOURCE, Map.of()))).isNotNull();

            assertThat(warnings.messages()).isEmpty();
        }
    }

    /** Every line the test counted was printed too, so the checks for leaks cannot pass because nothing was printed. */
    private static void assertPrinted(CapturedOutput output, Warnings warnings) {
        assertThat(output).contains(warnings.messages().toArray(String[]::new));
    }

    private static void assertFails(SharedAuthenticator authenticator, String token, int status) {
        assertThatThrownBy(() -> authenticator.authenticate(token))
                .isInstanceOfSatisfying(AuthenticationFailure.class,
                        failure -> assertThat(failure.status()).isEqualTo(status))
                .hasNoCause();
    }

    private static String bearer(String token) {
        return answer(token, "Bearer");
    }

    private static String answer(String token, String type) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"" + type + "\","
                + "\"issued_token_type\":\"urn:ietf:params:oauth:token-type:access_token\"}";
    }

    /** Whether the thread is parked in Nimbus's wait for another request's key set retrieval. */
    private static boolean waitsForTheKeySet(Thread thread) {
        return thread.getState() == Thread.State.TIMED_WAITING && Arrays
                .stream(thread.getStackTrace())
                .anyMatch(frame -> frame.getClassName().endsWith("CachingJWKSetSource"));
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** The factory of the shared client, with no certificate added. */
    private static ClientHttpRequestFactory sharedFactory() {
        return new SharedSecurityConfiguration.Enabled()
                .sharedHttpRequestFactory(new SharedSecurityProperties(), new OutboundProperties("", "", "", ""));
    }

    /** Sends every request to a loopback port that nothing listens on. */
    private static ClientHttpRequestFactory closedPort() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }
        JdkClientHttpRequestFactory delegate = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        return (uri, method) -> delegate
                .createRequest(URI.create("http://127.0.0.1:" + port + uri.getRawPath()), method);
    }
}
