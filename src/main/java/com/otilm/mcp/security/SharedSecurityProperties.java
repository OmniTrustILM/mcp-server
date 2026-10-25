package com.otilm.mcp.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ilm.mcp.security")
public class SharedSecurityProperties {
    public enum ExchangeMode {
        STANDARD,
        DELEGATION
    }

    private boolean enabled = false;
    private String issuer = null;
    private String resource = null;
    private String jwkSetUri = null;
    private String tokenUri = null;
    private String clientId = null;
    private String clientSecret = null;
    private String exchangeScope = "";

    public String getExchangeScope() {
        return exchangeScope;
    }

    public void setExchangeScope(String value) {
        exchangeScope = value;
    }

    private String apiAudience = "ilm";
    private ExchangeMode exchangeMode = ExchangeMode.STANDARD;
    private String requiredScope = "mcp:access";
    private List<String> authorizationScopes = List.of();

    public List<String> getAuthorizationScopes() {
        return authorizationScopes;
    }

    public void setAuthorizationScopes(List<String> value) {
        authorizationScopes = value;
    }

    private List<String> allowedOrigins = List.of();
    private Set<String> jwsAlgorithms = Set.of("RS256");
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);
    private Duration clockSkew = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String value) {
        this.issuer = value;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String value) {
        this.resource = value;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String value) {
        this.jwkSetUri = value;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String value) {
        this.tokenUri = value;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String value) {
        this.clientId = value;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String value) {
        this.clientSecret = value;
    }

    public String getApiAudience() {
        return apiAudience;
    }

    public void setApiAudience(String value) {
        this.apiAudience = value;
    }

    public ExchangeMode getExchangeMode() {
        return exchangeMode;
    }

    public void setExchangeMode(ExchangeMode value) {
        this.exchangeMode = value;
    }

    public String getRequiredScope() {
        return requiredScope;
    }

    public void setRequiredScope(String value) {
        this.requiredScope = value;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> value) {
        this.allowedOrigins = value;
    }

    public Set<String> getJwsAlgorithms() {
        return jwsAlgorithms;
    }

    public void setJwsAlgorithms(Set<String> value) {
        this.jwsAlgorithms = value;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration value) {
        this.connectTimeout = value;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration value) {
        this.readTimeout = value;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration value) {
        this.clockSkew = value;
    }

    @Override
    public String toString() {
        return "SharedSecurityProperties[redacted]";
    }
}
