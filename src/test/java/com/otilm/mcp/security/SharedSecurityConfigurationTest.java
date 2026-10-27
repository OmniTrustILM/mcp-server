package com.otilm.mcp.security;

import com.otilm.mcp.config.IlmApiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedSecurityConfigurationTest {
    private static final String HTTPS_ONLY = "Shared OAuth endpoints must be absolute HTTPS URLs without userinfo or fragments";
    private static final String HTTP_OR_HTTPS = "Shared OAuth endpoints must be absolute HTTP or HTTPS URLs without userinfo or fragments";

    @Test
    void allowsOnlyExplicitStatelessHttpDelegation() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            IlmApiProperties api = api();
            McpServerProperties server = server();
            assertThatCode(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .doesNotThrowAnyException();
            f.properties.setEnabled(false);
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class);
            f.properties.setEnabled(true);
            server.setStdio(true);
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class);
            server.setStdio(false);
            server.setProtocol(McpServerProperties.ServerProtocol.STREAMABLE);
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class);
            server.setProtocol(McpServerProperties.ServerProtocol.STATELESS);
            api.getAuth().setMethod(IlmApiProperties.AuthMethod.NONE);
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "api",
            "jwk",
            "token",
            "resource",
            "issuer",
            "secret",
            "algorithm",
            "timeout",
            "skew",
            "origin",
            "scope",
            "authorization-scope",
            "same-audience"})
    void refusesUnsafeConfiguration(String variant) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            IlmApiProperties api = api();
            switch (variant) {
                case "api" -> api.setUrl("http://ilm.test/api");
                case "jwk" -> f.properties.setJwkSetUri("http://auth.test/jwks");
                case "token" -> f.properties.setTokenUri("https://secret@auth.test/token");
                case "resource" -> f.properties.setResource("https://mcp.test/mcp#fragment");
                case "issuer" -> f.properties.setIssuer("relative");
                case "secret" -> f.properties.setClientSecret(" ");
                case "algorithm" -> f.properties.setJwsAlgorithms(java.util.Set.of("HS256"));
                case "timeout" -> f.properties.setReadTimeout(java.time.Duration.ofMinutes(10));
                case "skew" -> f.properties.setClockSkew(java.time.Duration.ofHours(1));
                case "origin" -> f.properties.setAllowedOrigins(java.util.List.of("*"));
                case "same-audience" -> f.properties.setApiAudience(OAuthFixture.RESOURCE);
                case "scope" -> f.properties.setRequiredScope("bad\"scope");
                case "authorization-scope" -> f.properties.setAuthorizationScopes(java.util.List.of("bad\"scope"));
                default -> throw new IllegalArgumentException("Unknown configuration variant: " + variant);
            }
            McpServerProperties server = server();
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(OAuthFixture.SENTINEL)
                    .hasNoCause();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "jwk", "token"})
    void acceptsHttpBackchannelOnlyWhenAllowed(String endpoint) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            IlmApiProperties api = api();
            switch (endpoint) {
                case "api" -> api.setUrl("http://ilm.test/api");
                case "jwk" -> f.properties.setJwkSetUri("http://auth.test/jwks");
                case "token" -> f.properties.setTokenUri("http://auth.test/token");
                default -> throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
            }
            McpServerProperties server = server();
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(HTTPS_ONLY);
            f.properties.setAllowHttpBackchannel(true);
            assertThatCode(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "resource", "origin"})
    void keepsPublicEndpointsHttpsWhenBackchannelHttpIsAllowed(String endpoint) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setAllowHttpBackchannel(true);
            switch (endpoint) {
                case "issuer" -> f.properties.setIssuer("http://auth.test/realm");
                case "resource" -> f.properties.setResource("http://mcp.test/mcp");
                case "origin" -> f.properties.setAllowedOrigins(java.util.List.of("http://mcp.test"));
                default -> throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
            }
            IlmApiProperties api = api();
            McpServerProperties server = server();
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(HTTPS_ONLY);
        }
    }

    @Test
    void refusesHttpBackchannelWithUserinfoOrFragment() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setAllowHttpBackchannel(true);
            IlmApiProperties api = api();
            McpServerProperties server = server();
            f.properties.setTokenUri("http://user@auth.test/token");
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(HTTP_OR_HTTPS);
            f.properties.setTokenUri("http://auth.test/token");
            f.properties.setJwkSetUri("http://auth.test/jwks#x");
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(HTTP_OR_HTTPS);
        }
    }

    @Test
    void refusesOtherSchemesWhenBackchannelHttpIsAllowed() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setAllowHttpBackchannel(true);
            f.properties.setJwkSetUri("ftp://auth.test/jwks");
            IlmApiProperties api = api();
            McpServerProperties server = server();
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(HTTP_OR_HTTPS);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http:///jwks", "http://auth_test/jwks", "http://auth.test/jw ks"})
    void refusesHttpBackchannelWithoutAUsableHost(String jwkSetUri) throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setAllowHttpBackchannel(true);
            f.properties.setJwkSetUri(jwkSetUri);
            IlmApiProperties api = api();
            McpServerProperties server = server();
            assertThatThrownBy(() -> SharedSecurityConfiguration.validate(f.properties, api, server))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(HTTP_OR_HTTPS);
        }
    }

    static IlmApiProperties api() {
        IlmApiProperties api = new IlmApiProperties();
        api.setUrl("https://ilm.test/api");
        api.getAuth().setMethod(IlmApiProperties.AuthMethod.OAUTH_DELEGATION);
        return api;
    }

    static McpServerProperties server() {
        McpServerProperties server = new McpServerProperties();
        server.setProtocol(McpServerProperties.ServerProtocol.STATELESS);
        return server;
    }
}
