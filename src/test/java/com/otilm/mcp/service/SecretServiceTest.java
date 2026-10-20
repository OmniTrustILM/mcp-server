package com.otilm.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SecretServiceTest {

    private SecretService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        service = new SecretServiceImpl(apiClient, new ObjectMapper());
    }

    @Test
    void shouldFormatSecretList() {
        stubFor(post(urlEqualTo("/v1/secrets"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "secret-uuid-1",
                                    "name": "db-password",
                                    "type": "basicAuth",
                                    "state": "active",
                                    "version": 2,
                                    "enabled": true,
                                    "owner": {"uuid": "user-1", "name": "admin"},
                                    "sourceVaultProfile": {"uuid": "vp-1", "name": "prod-vault"},
                                    "groups": [{"uuid": "g-1", "name": "production"}],
                                    "complianceStatus": "ok"
                                },
                                {
                                    "uuid": "secret-uuid-2",
                                    "name": "api-token",
                                    "type": "apiKey",
                                    "state": "expired",
                                    "version": 1,
                                    "enabled": false
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 2
                        }
                        """)));

        String result = service.searchSecrets(null, null, null);

        assertThat(result).contains("Found 2 secrets");
        assertThat(result).contains("db-password");
        assertThat(result).contains("api-token");
        assertThat(result).contains("secret-uuid-1");
        assertThat(result).contains("secret-uuid-2");
        assertThat(result).contains("Type: basicAuth");
        assertThat(result).contains("Type: apiKey");
        assertThat(result).contains("State: active");
        assertThat(result).contains("State: expired");
        assertThat(result).contains("Version: 2");
        assertThat(result).contains("Enabled: true");
        assertThat(result).contains("Enabled: false");
        assertThat(result).contains("Owner: admin");
        assertThat(result).contains("Vault Profile: prod-vault");
    }

    @Test
    void shouldFormatSecretDetail() {
        stubFor(get(urlEqualTo("/v1/secrets/secret-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "secret-1",
                            "name": "api-key-prod",
                            "description": "Production API key for external service",
                            "type": "apiKey",
                            "state": "active",
                            "version": 3,
                            "enabled": true,
                            "owner": {"uuid": "user-1", "name": "devops-team"},
                            "sourceVaultProfile": {"uuid": "vp-1", "name": "prod-vault"},
                            "groups": [
                                {"uuid": "g-1", "name": "production"},
                                {"uuid": "g-2", "name": "api-keys"}
                            ],
                            "complianceStatus": "ok",
                            "createdAt": "2026-01-15T08:00:00.000Z",
                            "updatedAt": "2026-03-20T14:30:00.000Z",
                            "syncVaultProfiles": [
                                {"uuid": "svp-1", "name": "backup-vault"}
                            ]
                        }
                        """)));

        String result = service.getSecret("secret-1");

        assertThat(result).contains("Secret: api-key-prod");
        assertThat(result).contains("UUID: secret-1");
        assertThat(result).contains("Description: Production API key for external service");
        assertThat(result).contains("Type: apiKey");
        assertThat(result).contains("State: active");
        assertThat(result).contains("Version: 3");
        assertThat(result).contains("Enabled: true");
        assertThat(result).contains("Owner: devops-team");
        assertThat(result).contains("Source Vault Profile: prod-vault");
        assertThat(result).contains("Groups: production, api-keys");
        assertThat(result).contains("Compliance: ok");
        assertThat(result).contains("Created: 2026-01-15T08:00:00");
        assertThat(result).contains("Updated: 2026-03-20T14:30:00");
        assertThat(result).contains("Sync Vault Profiles:");
        assertThat(result).contains("backup-vault");
    }

    @Test
    void shouldFormatSecretVersions() {
        stubFor(get(urlEqualTo("/v1/secrets/secret-1/versions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "version": 1,
                                "createdAt": "2026-01-15T08:00:00.000Z",
                                "fingerprint": "sha256:abc123def456"
                            },
                            {
                                "version": 2,
                                "createdAt": "2026-02-20T10:00:00.000Z",
                                "fingerprint": "sha256:789ghi012jkl"
                            },
                            {
                                "version": 3,
                                "createdAt": "2026-03-20T14:30:00.000Z",
                                "fingerprint": "sha256:mno345pqr678"
                            }
                        ]
                        """)));

        String result = service.getSecretVersions("secret-1");

        assertThat(result).contains("Secret Versions for secret-1");
        assertThat(result).contains("3 versions");
        assertThat(result).contains("Version: 1");
        assertThat(result).contains("Version: 2");
        assertThat(result).contains("Version: 3");
        assertThat(result).contains("sha256:abc123def456");
        assertThat(result).contains("sha256:mno345pqr678");
        assertThat(result).contains("2026-01-15T08:00:00");
        assertThat(result).contains("2026-03-20T14:30:00");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(post(urlEqualTo("/v1/secrets"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody("""
                        {"message": "Internal Server Error"}
                        """)));

        String result = service.searchSecrets(null, null, null);

        assertThat(result).contains("Error searching secrets");
    }

    @Test
    void shouldPassFiltersToSearchRequest() {
        stubFor(post(urlEqualTo("/v1/secrets"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "secret-uuid-1",
                                    "name": "filtered-secret",
                                    "type": "basicAuth",
                                    "state": "active",
                                    "version": 1,
                                    "enabled": true
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String filters = """
                [{"fieldSource":"property","fieldIdentifier":"name","condition":"CONTAINS","value":"filtered"}]
                """;
        String result = service.searchSecrets(filters, 10, 1);

        assertThat(result).contains("filtered-secret").contains("1 secrets");

        verify(postRequestedFor(urlEqualTo("/v1/secrets"))
                .withRequestBody(containing("\"fieldIdentifier\":\"name\""))
                .withRequestBody(containing("\"condition\":\"CONTAINS\""))
                .withRequestBody(containing("\"value\":\"filtered\"")));
    }

    @Test
    void shouldReturnErrorForInvalidFiltersJson() {
        String result = service.searchSecrets("not valid json", null, null);

        assertThat(result).contains("Invalid filters JSON");
    }
}
