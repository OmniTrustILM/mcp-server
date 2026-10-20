package com.otilm.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ilm.api")
public class IlmApiProperties {

    private String url;
    private AuthProperties auth = new AuthProperties();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public AuthProperties getAuth() {
        return auth;
    }

    public void setAuth(AuthProperties auth) {
        this.auth = auth;
    }

    public static class AuthProperties {
        private AuthMethod method = AuthMethod.NONE;
        private String sslBundle = "ilm-client";
        private OAuthProperties oauth = new OAuthProperties();

        public AuthMethod getMethod() {
            return method;
        }

        public void setMethod(AuthMethod method) {
            this.method = method;
        }

        public String getSslBundle() {
            return sslBundle;
        }

        public void setSslBundle(String sslBundle) {
            this.sslBundle = sslBundle;
        }

        public OAuthProperties getOauth() {
            return oauth;
        }

        public void setOauth(OAuthProperties oauth) {
            this.oauth = oauth;
        }
    }

    public enum AuthMethod {
        NONE,
        CERTIFICATE,
        OAUTH_PASSTHROUGH,
        OAUTH_CLIENT_CREDENTIALS
    }

    public static class OAuthProperties {
        private String clientId;
        private String clientSecret;
        private String tokenUrl;
        private String scope;
        private String audience;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
