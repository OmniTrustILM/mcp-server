package com.otilm.mcp.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

final class OAuthFixture implements AutoCloseable {
    static final String ISSUER = "https://auth.test/realm";
    static final String RESOURCE = "https://mcp.test/mcp";
    static final String SENTINEL = "secret-token-response-must-never-leak";
    final java.util.concurrent.atomic.AtomicReference<BlockedRequest> blocked = new java.util.concurrent.atomic.AtomicReference<>();
    final WireMockServer server = new WireMockServer(options()
            .dynamicPort()
            .extensions(new com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2() {
                @Override
                public String getName() {
                    return "controlled-downstream";
                }

                @Override
                public com.github.tomakehurst.wiremock.http.ResponseDefinition transform(
                        com.github.tomakehurst.wiremock.stubbing.ServeEvent event) {
                    BlockedRequest pending = blocked.get();
                    if (pending != null && event.getRequest().getUrl().equals("/api/v1/groups")
                            && pending.authorization.equals(event.getRequest().getHeader("Authorization"))) {
                        pending.entered.countDown();
                        try {
                            pending.release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    return event.getResponseDefinition();
                }
            }));
    final RSAKey key;
    final SharedSecurityProperties properties = new SharedSecurityProperties();
    final ClientHttpRequestFactory factory;

    OAuthFixture() throws JOSEException {
        key = new RSAKeyGenerator(2048).keyID("test-key").generate();
        server.start();
        properties.setEnabled(true);
        properties.setIssuer(ISSUER);
        properties.setResource(RESOURCE);
        properties.setJwkSetUri("https://auth.test/jwks");
        properties.setTokenUri("https://auth.test/token");
        properties.setClientId("ilm-mcp");
        properties.setClientSecret(SENTINEL);
        JdkClientHttpRequestFactory delegate = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
        factory = (uri, method) -> delegate
                .createRequest(URI
                        .create(server.baseUrl() + uri.getRawPath()
                                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())),
                        method);
        server.stubFor(get("/jwks").willReturn(okJson(new JWKSet(key.toPublicJWK()).toString())));
    }

    SharedAuthenticator authenticator() {
        return new SharedAuthenticator(properties, new RestTemplate(factory),
                RestClient.builder().requestFactory(factory).build());
    }

    String token(String sub, String audience, Map<String, Object> overrides) throws Exception {
        return token(sub, audience, overrides, JWSAlgorithm.RS256);
    }

    String token(String sub, String audience, Map<String, Object> overrides, JWSAlgorithm algorithm) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", sub);
        claims.put("aud", audience);
        claims.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.put("scope", "mcp:access");
        claims.putAll(overrides);
        claims.values().removeIf(java.util.Objects::isNull);
        JWSObject token = new JWSObject(new JWSHeader.Builder(algorithm).keyID("test-key").build(),
                new Payload(claims));
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    void exchange(String token) {
        server
                .stubFor(post("/token")
                        .willReturn(okJson(JsonMapper
                                .builder()
                                .build()
                                .writeValueAsString(Map
                                        .of("access_token", token, "token_type", "Bearer", "issued_token_type",
                                                "urn:ietf:params:oauth:token-type:access_token")))));
    }

    BlockedRequest block(String token) {
        BlockedRequest pending = new BlockedRequest("Bearer " + token);
        blocked.set(pending);
        return pending;
    }

    static final class BlockedRequest implements AutoCloseable {
        final String authorization;
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        BlockedRequest(String authorization) {
            this.authorization = authorization;
        }

        @Override
        public void close() {
            release.countDown();
        }
    }

    @Override
    public void close() {
        server.stop();
    }
}
