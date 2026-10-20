package com.otilm.mcp.config;

import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.security.AuthTokenFilter;
import com.otilm.mcp.security.AuthTokenHolder;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IlmApiProperties.class)
public class IlmApiClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ilm.api.auth.method", havingValue = "oauth-passthrough")
    public FilterRegistrationBean<AuthTokenFilter> authTokenFilter() {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthTokenFilter());
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public RestClient ilmRestClient(IlmApiProperties properties, Optional<SslBundles> sslBundles) {
        RestClient.Builder builder = IlmApiClient.restClientBuilder(properties.getUrl());

        switch (properties.getAuth().getMethod()) {
            case CERTIFICATE -> configureCertificateAuth(builder, properties, sslBundles);
            case OAUTH_PASSTHROUGH -> configureOAuthPassthrough(builder);
            case OAUTH_CLIENT_CREDENTIALS -> configureOAuthClientCredentials(builder, properties);
            case NONE -> {
                /* No auth - used for testing */ }
        }

        return builder.build();
    }

    private void configureCertificateAuth(RestClient.Builder builder, IlmApiProperties properties,
            Optional<SslBundles> sslBundles) {
        SslBundles bundles = sslBundles
                .orElseThrow(() -> new IllegalStateException("SSL bundles required for certificate auth"));
        SslBundle bundle = bundles.getBundle(properties.getAuth().getSslBundle());
        builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings.ofSslBundle(bundle)));
    }

    private void configureOAuthPassthrough(RestClient.Builder builder) {
        builder.requestInterceptor(bearerTokenPassthroughInterceptor());
    }

    private void configureOAuthClientCredentials(RestClient.Builder builder, IlmApiProperties properties) {
        OAuthTokenProvider tokenProvider = new OAuthTokenProvider(properties.getAuth().getOauth());
        builder.requestInterceptor((request, body, execution) -> {
            request.getHeaders().setBearerAuth(tokenProvider.getAccessToken());
            return execution.execute(request, body);
        });
    }

    private ClientHttpRequestInterceptor bearerTokenPassthroughInterceptor() {
        return (request, body, execution) -> {
            String token = AuthTokenHolder.getToken();
            if (token != null) {
                request.getHeaders().setBearerAuth(token);
            }
            return execution.execute(request, body);
        };
    }
}
