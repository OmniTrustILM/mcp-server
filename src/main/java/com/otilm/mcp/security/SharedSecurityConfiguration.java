package com.otilm.mcp.security;

import com.otilm.mcp.config.IlmApiProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SharedSecurityProperties.class, OutboundProperties.class})
public class SharedSecurityConfiguration {
    @Bean
    InitializingBean sharedSecurityGuard(SharedSecurityProperties security, IlmApiProperties api,
            McpServerProperties server) {
        return () -> validate(security, api, server);
    }

    public static void validate(SharedSecurityProperties security, IlmApiProperties api, McpServerProperties server) {
        boolean delegation = api.getAuth().getMethod() == IlmApiProperties.AuthMethod.OAUTH_DELEGATION;
        if (security.isEnabled() != delegation || delegation
                && (server.isStdio() || server.getProtocol() != McpServerProperties.ServerProtocol.STATELESS
                        || server.getType() != McpServerProperties.ApiType.SYNC)) {
            throw new IllegalStateException(
                    "Shared OAuth requires oauth-delegation, security.enabled=true and SYNC STATELESS HTTP");
        }
        if (!security.isEnabled()) {
            return;
        }
        https(security.getIssuer());
        https(security.getResource());
        // Only the endpoints this server calls may use in-cluster HTTP; the issuer, resource and origins stay HTTPS.
        boolean allowHttp = security.isAllowHttpBackchannel();
        backchannel(security.getJwkSetUri(), allowHttp);
        backchannel(security.getTokenUri(), allowHttp);
        backchannel(api.getUrl(), allowHttp);
        if (security.getResource().equals(security.getApiAudience())) {
            throw new IllegalStateException("Shared OAuth resource and downstream API audiences must differ");
        }
        validateClientAndScopes(security);
        if (security.getJwsAlgorithms() == null || security.getJwsAlgorithms().isEmpty()
                || !Set
                        .of("RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "PS256", "PS384", "PS512")
                        .containsAll(security.getJwsAlgorithms())) {
            throw new IllegalStateException("Shared OAuth requires an explicit asymmetric JWS algorithm allowlist");
        }
        timeout(security.getConnectTimeout());
        timeout(security.getReadTimeout());
        if (security.getClockSkew() == null || security.getClockSkew().isNegative()
                || security.getClockSkew().compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalStateException("Shared OAuth clock skew must be between zero and 60 seconds");
        }
        validateOrigins(security);
    }

    private static void validateOrigins(SharedSecurityProperties security) {
        if (security.getAllowedOrigins() == null) {
            throw new IllegalStateException("Allowed origins must be explicit");
        }
        for (String origin : security.getAllowedOrigins()) {
            URI uri = https(origin);
            if (!uri.getRawPath().isEmpty() || uri.getRawQuery() != null) {
                throw new IllegalStateException("Allowed origins must be HTTPS origins without paths or queries");
            }
        }
    }

    private static void validateClientAndScopes(SharedSecurityProperties security) {
        if (blank(security.getClientId()) || blank(security.getClientSecret()) || blank(security.getApiAudience())
                || security.getExchangeMode() == null || !scope(security.getRequiredScope())
                || !blank(security.getExchangeScope()) && !java.util.Arrays
                        .stream(security.getExchangeScope().split(" ", -1))
                        .allMatch(SharedSecurityConfiguration::scope)) {
            throw new IllegalStateException(
                    "Shared OAuth requires client credentials, audience, exchange mode and valid scopes");
        }
        if (security.getAuthorizationScopes() == null
                || !security.getAuthorizationScopes().stream().allMatch(SharedSecurityConfiguration::scope)) {
            throw new IllegalStateException("Authorization scopes must be valid OAuth scope tokens");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean scope(String value) {
        return value != null && !value.isEmpty()
                && value
                        .chars()
                        .allMatch(character -> character == 0x21 || character >= 0x23 && character <= 0x5b
                                || character >= 0x5d && character <= 0x7e);
    }

    private static URI https(String value) {
        return backchannel(value, false);
    }

    /** Parses an absolute HTTPS URL, or an HTTP one when allowed, without userinfo or fragments. */
    private static URI backchannel(String value, boolean allowHttp) {
        try {
            URI uri = URI.create(value);
            boolean schemeAllowed = "https".equals(uri.getScheme()) || allowHttp && "http".equals(uri.getScheme());
            if (!schemeAllowed || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception ignored) {
            throw new IllegalStateException("Shared OAuth endpoints must be absolute "
                    + (allowHttp ? "HTTP or HTTPS" : "HTTPS") + " URLs without userinfo or fragments");
        }
    }

    private static void timeout(Duration value) {
        if (value == null || value.compareTo(Duration.ofMillis(100)) < 0
                || value.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalStateException(
                    "Shared OAuth HTTP timeouts must be between 100 milliseconds and 60 seconds");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "ilm.mcp.security.enabled", havingValue = "true")
    static class Enabled {
        @Bean
        @ConditionalOnMissingBean(ClientHttpRequestFactory.class)
        ClientHttpRequestFactory sharedHttpRequestFactory(SharedSecurityProperties properties,
                OutboundProperties outbound) {
            HttpClient.Builder builder = HttpClient
                    .newBuilder()
                    .sslContext(OutboundTrust.sslContext(outbound.trustedCertificates()))
                    .connectTimeout(properties.getConnectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER);
            // Without a proxy setting the client keeps the JDK's default selector, which reads the proxy properties.
            OutboundProxy.from(outbound).ifPresent(proxy -> proxy.applyTo(builder));
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(builder.build());
            factory.setReadTimeout(properties.getReadTimeout());
            return factory;
        }

        @Bean
        SharedAuthenticator sharedAuthenticator(SharedSecurityProperties properties, ClientHttpRequestFactory factory,
                IlmApiProperties api, McpServerProperties server) {
            validate(properties, api, server);
            return new SharedAuthenticator(properties, new RestTemplate(factory),
                    RestClient.builder().requestFactory(factory).build());
        }

        @Bean
        FilterRegistrationBean<SharedAuthFilter> sharedAuthFilter(SharedSecurityProperties properties,
                SharedAuthenticator authenticator) {
            FilterRegistrationBean<SharedAuthFilter> registration = new FilterRegistrationBean<>(
                    new SharedAuthFilter(properties, authenticator));
            registration.addUrlPatterns("/*");
            registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        @Bean
        WebMvcStatelessServerTransport webMvcStatelessServerTransport(JsonMapper mapper,
                McpServerStreamableHttpProperties transport) {
            return WebMvcStatelessServerTransport
                    .builder()
                    .jsonMapper(new JacksonMcpJsonMapper(mapper))
                    .messageEndpoint(transport.getMcpEndpoint())
                    .contextExtractor(request -> {
                        Object credential = request.servletRequest().getAttribute(RequestCredentials.KEY);
                        if (!(credential instanceof RequestCredential)) {
                            throw new IllegalStateException("Authenticated transport context is required");
                        }
                        return McpTransportContext.create(Map.of(RequestCredentials.KEY, credential));
                    })
                    .build();
        }
    }
}
