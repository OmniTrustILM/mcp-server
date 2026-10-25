package com.otilm.mcp.security;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class SharedAuthFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {"/mcp", "/mcp/", "/mcp;v=1", "/%6dcp", "//mcp", "/actuator/env", "/error", "/unexpected"})
    void protectsEveryNonPublicPath(String path) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean dispatched = new AtomicBoolean();
            new SharedAuthFilter(f.properties, f.authenticator())
                    .doFilter(request, response, (a, b) -> dispatched.set(true));
            assertThat(dispatched).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate"))
                    .contains("https://mcp.test/.well-known/oauth-protected-resource/mcp");
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(jakarta.servlet.DispatcherType.class)
    void protectsContextPathAcrossEveryServletDispatch(jakarta.servlet.DispatcherType dispatcher) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/shared/mcp");
            request.setContextPath("/shared");
            request.setDispatcherType(dispatcher);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new SharedAuthFilter(f.properties, f.authenticator())
                    .doFilter(request, response, (a, b) -> fail("unauthenticated dispatch"));
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void metadataUsesConfiguredResourceAndNeverCallsIdentityProvider() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET",
                    "/.well-known/oauth-protected-resource/mcp");
            request.addHeader("Host", "attacker.test");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new SharedAuthFilter(f.properties, f.authenticator())
                    .doFilter(request, response, (a, b) -> fail("metadata dispatched"));
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString())
                    .contains("https://mcp.test/mcp", OAuthFixture.ISSUER, "mcp:access", "header")
                    .doesNotContain("attacker");
            assertThat(f.server.getAllServeEvents()).isEmpty();
        }
    }

    @Test
    void metadataAndChallengesAdvertiseConfiguredAuthorizationScopeUnion() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties
                    .setAuthorizationScopes(
                            java.util.List.of("openid", "mcp:access", "delegation:client:ilm-mcp", "openid"));
            SharedAuthFilter filter = new SharedAuthFilter(f.properties, f.authenticator());
            MockHttpServletResponse metadata = new MockHttpServletResponse();
            filter
                    .doFilter(new MockHttpServletRequest("GET", "/.well-known/oauth-protected-resource/mcp"), metadata,
                            (a, b) -> fail("metadata dispatched"));
            Map<String, Object> body = tools.jackson.databind.json.JsonMapper
                    .builder()
                    .build()
                    .readValue(metadata.getContentAsString(),
                            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
            assertThat(body)
                    .containsEntry("scopes_supported",
                            java.util.List.of("mcp:access", "openid", "delegation:client:ilm-mcp"));
            for (boolean missingScope : new boolean[]{false, true}) {
                MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
                if (missingScope) {
                    request
                            .addHeader("Authorization",
                                    "Bearer " + f.token("alice", OAuthFixture.RESOURCE, Map.of("scope", "openid")));
                }
                MockHttpServletResponse response = new MockHttpServletResponse();
                filter.doFilter(request, response, (a, b) -> fail("unauthorized request dispatched"));
                assertThat(response.getStatus()).isEqualTo(missingScope ? 403 : 401);
                assertThat(response.getHeader("WWW-Authenticate"))
                        .contains("scope=\"mcp:access openid delegation:client:ilm-mcp\"");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer", "bEaReR"})
    void obtainsOneCredentialAndClearsRequestAttributeAfterDispatch(String scheme) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setAuthorizationScopes(java.util.List.of("openid", "delegation:client:ilm-mcp"));
            String token = f.token("alice", "ilm", Map.of());
            f.exchange(token);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
            request.addHeader("Authorization", scheme + " " + f.token("alice", OAuthFixture.RESOURCE, Map.of()));
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean dispatched = new AtomicBoolean();
            new SharedAuthFilter(f.properties, f.authenticator()).doFilter(request, response, (a, b) -> {
                dispatched.set(true);
                assertThat(((RequestCredential) a.getAttribute(RequestCredentials.KEY)).token()).isEqualTo(token);
            });
            assertThat(dispatched).isTrue();
            assertThat(request.getAttribute(RequestCredentials.KEY)).isNull();
        }
    }

    @Test
    void originAndPreflightAreDeliberate() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setAllowedOrigins(java.util.List.of("https://client.test"));
            SharedAuthFilter filter = new SharedAuthFilter(f.properties, f.authenticator());
            MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/mcp");
            request.addHeader("Origin", "https://client.test");
            request.addHeader("Access-Control-Request-Method", "POST");
            request.addHeader("Access-Control-Request-Headers", "authorization,content-type,mcp-protocol-version");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (a, b) -> fail("preflight dispatched"));
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://client.test");
            request.removeHeader("Origin");
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, (a, b) -> fail("options dispatched"));
            assertThat(response.getStatus()).isEqualTo(401);
            request.addHeader("Origin", "https://attacker.test");
            response = new MockHttpServletResponse();
            filter.doFilter(request, response, (a, b) -> fail("origin dispatched"));
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "B", "Bearer", "Bearer ", "Bearer\tabc", "Bearer  abc", "Basic abc", "Bearer abc!"})
    void rejectsMalformedBearerHeaderBeforeCallingIdentityProvider(String header) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
            request.addHeader("Authorization", header);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new SharedAuthFilter(f.properties, f.authenticator())
                    .doFilter(request, response, (a, b) -> fail("malformed credential dispatched"));
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(f.server.getAllServeEvents()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "query", "malformed"})
    void rejectsAmbiguousCredentials(String kind) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
            request.addHeader("Authorization", "Bearer " + f.token("alice", OAuthFixture.RESOURCE, Map.of()));
            if (kind.equals("duplicate")) {
                request.addHeader("Authorization", "Bearer second");
            }
            if (kind.equals("query")) {
                request.addParameter("access_token", "secret");
            }
            if (kind.equals("malformed")) {
                request.addHeader("Authorization", "Basic second");
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            new SharedAuthFilter(f.properties, f.authenticator())
                    .doFilter(request, response, (a, b) -> fail("credential dispatched"));
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }
}
