package com.otilm.mcp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class OAuthTokenProvider {
    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenProvider.class);
    private static final int EXPIRY_BUFFER_SECONDS = 30;

    private final IlmApiProperties.OAuthProperties oauthProperties;
    private final RestClient tokenClient;
    private String cachedToken;
    private Instant tokenExpiry;

    public OAuthTokenProvider(IlmApiProperties.OAuthProperties oauthProperties) {
        this.oauthProperties = oauthProperties;
        this.tokenClient = RestClient.builder().baseUrl(oauthProperties.getTokenUrl()).build();
    }

    public synchronized String getAccessToken() {
        if (cachedToken == null || Instant.now().isAfter(tokenExpiry)) {
            refreshToken();
        }
        return cachedToken;
    }

    private void refreshToken() {
        logger.debug("Requesting new OAuth token from {}", oauthProperties.getTokenUrl());
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", oauthProperties.getClientId());
        formData.add("client_secret", oauthProperties.getClientSecret());
        if (oauthProperties.getScope() != null && !oauthProperties.getScope().isBlank()) {
            formData.add("scope", oauthProperties.getScope());
        }
        if (oauthProperties.getAudience() != null && !oauthProperties.getAudience().isBlank()) {
            formData.add("audience", oauthProperties.getAudience());
        }

        TokenResponse tokenResponse = tokenClient
                .post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(TokenResponse.class);

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("Failed to obtain OAuth token");
        }

        cachedToken = tokenResponse.accessToken();
        tokenExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn() - EXPIRY_BUFFER_SECONDS);
        logger.debug("OAuth token refreshed, expires at {}", tokenExpiry);
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType) {
    }
}
