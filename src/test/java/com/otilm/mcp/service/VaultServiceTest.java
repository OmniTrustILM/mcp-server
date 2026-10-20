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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class VaultServiceTest {

    private VaultService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        service = new VaultServiceImpl(apiClient, new ObjectMapper());
    }

    @Test
    void shouldFormatVaultInstanceList() {
        stubFor(post(urlEqualTo("/v1/vaults/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vault-uuid-1",
                                    "name": "HashiCorp Vault Production",
                                    "description": "Primary production vault",
                                    "connector": {
                                        "uuid": "conn-1",
                                        "name": "hcv-connector"
                                    }
                                },
                                {
                                    "uuid": "vault-uuid-2",
                                    "name": "AWS Secrets Manager",
                                    "connector": {
                                        "uuid": "conn-2",
                                        "name": "aws-connector"
                                    }
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 2
                        }
                        """)));

        String result = service.listVaultInstances(null, null, null);

        assertThat(result).contains("Found 2 vault instances");
        assertThat(result).contains("HashiCorp Vault Production");
        assertThat(result).contains("AWS Secrets Manager");
        assertThat(result).contains("vault-uuid-1");
        assertThat(result).contains("vault-uuid-2");
        assertThat(result).contains("Connector: hcv-connector");
        assertThat(result).contains("Connector: aws-connector");
        assertThat(result).contains("Description: Primary production vault");
    }

    @Test
    void shouldFormatVaultProfileList() {
        stubFor(post(urlEqualTo("/v1/vaultProfiles/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vp-uuid-1",
                                    "name": "prod-vault-profile",
                                    "description": "Production vault profile",
                                    "vaultInstance": {
                                        "uuid": "vault-1",
                                        "name": "HashiCorp Vault"
                                    },
                                    "enabled": true
                                },
                                {
                                    "uuid": "vp-uuid-2",
                                    "name": "dev-vault-profile",
                                    "vaultInstance": {
                                        "uuid": "vault-2",
                                        "name": "AWS Secrets Manager"
                                    },
                                    "enabled": false
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 2
                        }
                        """)));

        String result = service.listVaultProfiles(null, null, null);

        assertThat(result).contains("Found 2 vault profiles");
        assertThat(result).contains("prod-vault-profile");
        assertThat(result).contains("dev-vault-profile");
        assertThat(result).contains("vp-uuid-1");
        assertThat(result).contains("vp-uuid-2");
        assertThat(result).contains("Vault Instance: HashiCorp Vault");
        assertThat(result).contains("Vault Instance: AWS Secrets Manager");
        assertThat(result).contains("Enabled: true");
        assertThat(result).contains("Enabled: false");
        assertThat(result).contains("Description: Production vault profile");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(post(urlEqualTo("/v1/vaults/list"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody("""
                        {"message": "Internal Server Error"}
                        """)));

        String result = service.listVaultInstances(null, null, null);

        assertThat(result).contains("Error listing vault instances");
    }

    @Test
    void shouldPassFiltersToVaultInstancesRequest() {
        stubFor(post(urlEqualTo("/v1/vaults/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vault-uuid-1",
                                    "name": "filtered-vault",
                                    "connector": {
                                        "uuid": "conn-1",
                                        "name": "hcv-connector"
                                    }
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
        String result = service.listVaultInstances(filters, 10, 1);

        assertThat(result).contains("filtered-vault").contains("1 vault instances");

        verify(postRequestedFor(urlEqualTo("/v1/vaults/list"))
                .withRequestBody(containing("\"fieldIdentifier\":\"name\""))
                .withRequestBody(containing("\"condition\":\"CONTAINS\""))
                .withRequestBody(containing("\"value\":\"filtered\"")));
    }

    @Test
    void shouldReturnErrorForInvalidVaultInstanceFiltersJson() {
        String result = service.listVaultInstances("not valid json", null, null);

        assertThat(result).contains("Invalid filters JSON");
    }

    @Test
    void shouldPassFiltersToVaultProfilesRequest() {
        stubFor(post(urlEqualTo("/v1/vaultProfiles/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vp-uuid-1",
                                    "name": "filtered-profile",
                                    "vaultInstance": {
                                        "uuid": "vault-1",
                                        "name": "HashiCorp Vault"
                                    },
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
        String result = service.listVaultProfiles(filters, 10, 1);

        assertThat(result).contains("filtered-profile").contains("1 vault profiles");

        verify(postRequestedFor(urlEqualTo("/v1/vaultProfiles/list"))
                .withRequestBody(containing("\"fieldIdentifier\":\"name\""))
                .withRequestBody(containing("\"condition\":\"CONTAINS\""))
                .withRequestBody(containing("\"value\":\"filtered\"")));
    }

    @Test
    void shouldReturnErrorForInvalidVaultProfileFiltersJson() {
        String result = service.listVaultProfiles("not valid json", null, null);

        assertThat(result).contains("Invalid filters JSON");
    }
}
