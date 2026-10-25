package com.otilm.mcp.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedAuthenticatorTest {
    @Test
    void exchangesValidatedUserAndReturnsOnlyDownstreamCredential() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            String downstream = f.token("alice", "ilm", Map.of());
            f.exchange(downstream);
            RequestCredential credential = f.authenticator().authenticate(source);
            assertThat(credential.token()).isEqualTo(downstream).isNotEqualTo(source);
            assertThat(credential.toString()).doesNotContain(downstream, "alice");
            f.server
                    .verify(postRequestedFor(urlEqualTo("/token"))
                            .withFormParam("grant_type", equalTo("urn:ietf:params:oauth:grant-type:token-exchange"))
                            .withFormParam("subject_token", equalTo(source))
                            .withFormParam("subject_token_type",
                                    equalTo("urn:ietf:params:oauth:token-type:access_token"))
                            .withFormParam("requested_token_type",
                                    equalTo("urn:ietf:params:oauth:token-type:access_token"))
                            .withFormParam("audience", equalTo("ilm"))
                            .withFormParam("client_id", equalTo("ilm-mcp"))
                            .withFormParam("client_secret", equalTo(OAuthFixture.SENTINEL))
                            .withoutFormParam("actor_token")
                            .withoutFormParam("scope"));
        }
    }

    @Test
    void exchangeScopeComesOnlyFromOperatorConfiguration() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setExchangeScope("openid ilm-mcp-upstream");
            f.exchange(f.token("alice", "ilm", Map.of()));
            f
                    .authenticator()
                    .authenticate(
                            f.token("alice", OAuthFixture.RESOURCE, Map.of("scope", "mcp:access attacker-scope")));
            f.server
                    .verify(postRequestedFor(urlEqualTo("/token"))
                            .withFormParam("scope", equalTo("openid ilm-mcp-upstream")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "iss",
            "aud",
            "exp",
            "sub",
            "blank-sub",
            "nbf",
            "scope-type",
            "aud-type",
            "act",
            "may_act",
            "signature",
            "expired",
            "exp-type",
            "sub-type",
            "nbf-type"})
    void rejectsInvalidSourceBeforeExchange(String invalid) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            Map<String, Object> claims = new HashMap<>();
            switch (invalid) {
                case "iss" -> claims.put("iss", "https://other.test");
                case "aud" -> claims.put("aud", "ilm-mcp");
                case "exp" -> claims.put("exp", null);
                case "expired" -> claims.put("exp", Instant.now().minusSeconds(120).getEpochSecond());
                case "exp-type" -> claims.put("exp", "not-numeric");
                case "sub-type" -> claims.put("sub", 123);
                case "nbf-type" -> claims.put("nbf", "later");
                case "sub" -> claims.put("sub", null);
                case "blank-sub" -> claims.put("sub", " ");
                case "nbf" -> claims.put("nbf", Instant.now().plusSeconds(300).getEpochSecond());
                case "scope-type" -> claims.put("scope", java.util.List.of("mcp:access"));
                case "aud-type" -> claims.put("aud", java.util.List.of(42));
                case "act", "may_act" -> claims.put(invalid, Map.of("client_id", "other"));
                case "signature" -> {
                    // A separately signed token replaces this fixture token below.
                }
                default -> throw new IllegalArgumentException("Unknown source variant: " + invalid);
            }
            String source = f.token("alice", OAuthFixture.RESOURCE, claims);
            if (invalid.equals("signature")) {
                try (OAuthFixture attacker = new OAuthFixture()) {
                    source = attacker.token("alice", OAuthFixture.RESOURCE, Map.of());
                }
            }
            String input = source;
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(input))
                    .isInstanceOf(AuthenticationFailure.class)
                    .satisfies(e -> assertThat(((AuthenticationFailure) e).status()).isEqualTo(401))
                    .hasNoCause();
            f.server.verify(0, postRequestedFor(urlEqualTo("/token")));
        }
    }

    @Test
    void missingScopeIsForbidden() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of("scope", "other"));
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source))
                    .isInstanceOf(AuthenticationFailure.class)
                    .satisfies(e -> assertThat(((AuthenticationFailure) e).status()).isEqualTo(403));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "subject",
            "audience",
            "expired",
            "missing-exp",
            "act",
            "issuer",
            "scope-type",
            "missing-sub",
            "blank-sub",
            "signature"})
    void rejectsInvalidExchangedToken(String invalid) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            Map<String, Object> claims = new HashMap<>();
            switch (invalid) {
                case "subject" -> claims.put("sub", "bob");
                case "audience" -> claims.put("aud", OAuthFixture.RESOURCE);
                case "expired" -> claims.put("exp", Instant.now().minusSeconds(60).getEpochSecond());
                case "missing-exp" -> claims.put("exp", null);
                case "missing-sub" -> claims.put("sub", null);
                case "blank-sub" -> claims.put("sub", " ");
                case "scope-type" -> claims.put("scope", 42);
                case "issuer" -> claims.put("iss", "https://other.test");
                case "act" -> claims.put("act", Map.of("client_id", "ilm-mcp"));
                case "signature" -> {
                    // A separately signed token replaces this fixture token below.
                }
                default -> throw new IllegalArgumentException("Unknown exchanged-token variant: " + invalid);
            }
            f.exchange(f.token("alice", "ilm", claims));
            if (invalid.equals("signature")) {
                try (OAuthFixture attacker = new OAuthFixture()) {
                    f.exchange(attacker.token("alice", "ilm", Map.of()));
                }
            }
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source))
                    .isInstanceOf(AuthenticationFailure.class)
                    .hasNoCause();
            f.server.verify(1, postRequestedFor(urlEqualTo("/token")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "id_token", "refresh_token", "error", "redirect"})
    void refusesMalformedResponsesAndDropsEndpointBodies(String variant) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            if (variant.equals("error")) {
                f.server.stubFor(post("/token").willReturn(serverError().withBody(OAuthFixture.SENTINEL)));
            } else if (variant.equals("redirect")) {
                f.server.stubFor(post("/token").willReturn(temporaryRedirect("/leak")));
            } else {
                f.server
                        .stubFor(post("/token")
                                .willReturn(
                                        okJson("{\"access_token\":\"" + OAuthFixture.SENTINEL + "\",\"token_type\":\""
                                                + variant + "\",\"issued_token_type\":\"id_token\"}")));
            }
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source))
                    .isInstanceOf(AuthenticationFailure.class)
                    .hasNoCause()
                    .hasMessageNotContaining(OAuthFixture.SENTINEL);
            f.server.verify(0, anyRequestedFor(urlEqualTo("/leak")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "wrong", "nested", "malformed", "subject"})
    void delegationRequiresCorrectImmediateActorAndNeverFallsBack(String variant) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setExchangeMode(SharedSecurityProperties.ExchangeMode.DELEGATION);
            Map<String, Object> claims = new HashMap<>();
            switch (variant) {
                case "wrong" -> claims.put("act", Map.of("client_id", "other"));
                case "nested" -> claims.put("act", Map.of("client_id", "other", "act", Map.of("client_id", "ilm-mcp")));
                case "malformed" -> claims.put("act", "ilm-mcp");
                case "subject" -> {
                    claims.put("sub", "other");
                    claims.put("act", Map.of("client_id", "ilm-mcp"));
                }
                case "missing" -> {
                    // Leave the immediate actor claim absent.
                }
                default -> throw new IllegalArgumentException("Unknown delegation variant: " + variant);
            }
            f.exchange(f.token("alice", "ilm", claims));
            f.server
                    .stubFor(post("/token")
                            .withFormParam("grant_type", equalTo("client_credentials"))
                            .willReturn(okJson("{\"access_token\":\"actor-token\",\"token_type\":\"Bearer\"}")));
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source))
                    .isInstanceOf(AuthenticationFailure.class)
                    .hasNoCause();
            f.server.verify(2, postRequestedFor(urlEqualTo("/token")));
            f.server
                    .verify(1, postRequestedFor(urlEqualTo("/token"))
                            .withFormParam("actor_token", equalTo("actor-token")));
        }
    }

    @Test
    void sourceCanAlsoContainRequesterAudienceButStillRequiresResource() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.exchange(f.token("alice", "ilm", Map.of()));
            assertThat(f
                    .authenticator()
                    .authenticate(f
                            .token("alice", OAuthFixture.RESOURCE,
                                    Map.of("aud", java.util.List.of(OAuthFixture.RESOURCE, "ilm-mcp")))))
                    .isNotNull();
        }
    }

    @Test
    void jwkFailuresDropResponseBodiesAndDoNotFollowRedirects() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            f.server.stubFor(get("/jwks").willReturn(temporaryRedirect("/leak")));
            SharedAuthenticator redirectAuthenticator = f.authenticator();
            assertThatThrownBy(() -> redirectAuthenticator.authenticate(source))
                    .isInstanceOf(AuthenticationFailure.class)
                    .hasNoCause();
            f.server.verify(0, getRequestedFor(urlEqualTo("/leak")));
            f.server.stubFor(get("/jwks").willReturn(serverError().withBody(OAuthFixture.SENTINEL)));
            SharedAuthenticator unavailableAuthenticator = f.authenticator();
            assertThatThrownBy(() -> unavailableAuthenticator.authenticate(source))
                    .isInstanceOf(AuthenticationFailure.class)
                    .hasNoCause()
                    .hasMessageNotContaining(OAuthFixture.SENTINEL);
        }
    }

    @Test
    void rejectsMalformedActorClaimMembersBeforeDelegation() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setExchangeMode(SharedSecurityProperties.ExchangeMode.DELEGATION);
            f.exchange(f.token("alice", "ilm", Map.of("act", Map.of("client_id", "ilm-mcp"))));
            f.server
                    .stubFor(post("/token")
                            .withFormParam("grant_type", equalTo("client_credentials"))
                            .willReturn(okJson("{\"access_token\":\"actor-token\",\"token_type\":\"Bearer\"}")));
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of("may_act", Map.of("client_id", 42)));
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source)).isInstanceOf(AuthenticationFailure.class);
            f.server.verify(0, postRequestedFor(urlEqualTo("/token")));
        }
    }

    @Test
    void asymmetricAlgorithmRequiresExplicitAllowlist() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of(), com.nimbusds.jose.JWSAlgorithm.RS384);
            f.exchange(f.token("alice", "ilm", Map.of(), com.nimbusds.jose.JWSAlgorithm.RS384));
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source)).isInstanceOf(AuthenticationFailure.class);
            f.properties.setJwsAlgorithms(java.util.Set.of("RS384"));
            assertThat(f.authenticator().authenticate(source)).isNotNull();
        }
    }

    @Test
    void sourceMustNotAlreadyAuthorizeTheDownstreamAudience() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f
                    .token("alice", OAuthFixture.RESOURCE,
                            Map.of("aud", java.util.List.of(OAuthFixture.RESOURCE, "ilm")));
            f.exchange(f.token("alice", "ilm", Map.of()));
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source)).isInstanceOf(AuthenticationFailure.class);
            f.server.verify(0, postRequestedFor(urlEqualTo("/token")));
        }
    }

    @Test
    void downstreamMustNotAlsoAuthorizeThePublicMcpResource() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            f.exchange(f.token("alice", "ilm", Map.of("aud", java.util.List.of("ilm", OAuthFixture.RESOURCE))));
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source)).isInstanceOf(AuthenticationFailure.class);
        }
    }

    @Test
    void anExchangeResponseCannotEchoTheIncomingToken() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of());
            f.exchange(source);
            SharedAuthenticator authenticator = f.authenticator();
            assertThatThrownBy(() -> authenticator.authenticate(source)).isInstanceOf(AuthenticationFailure.class);
        }
    }

    @Test
    void delegationObtainsActorAndRequiresImmediateSignedActor() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setExchangeMode(SharedSecurityProperties.ExchangeMode.DELEGATION);
            String actor = f.token("service-account", "ilm-mcp", Map.of());
            String source = f.token("alice", OAuthFixture.RESOURCE, Map.of("may_act", Map.of("client_id", "ilm-mcp")));
            String downstream = f.token("alice", "ilm", Map.of("act", Map.of("client_id", "ilm-mcp")));
            f.exchange(downstream);
            f.server
                    .stubFor(post("/token")
                            .withFormParam("grant_type", equalTo("client_credentials"))
                            .willReturn(okJson("{\"access_token\":\"" + actor + "\",\"token_type\":\"Bearer\"}")));
            assertThat(f.authenticator().authenticate(source).token()).isEqualTo(downstream);
            f.server
                    .verify(postRequestedFor(urlEqualTo("/token"))
                            .withFormParam("actor_token", equalTo(actor))
                            .withFormParam("actor_token_type",
                                    equalTo("urn:ietf:params:oauth:token-type:access_token")));
        }
    }
}
