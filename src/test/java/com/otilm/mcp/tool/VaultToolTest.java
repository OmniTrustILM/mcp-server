package com.otilm.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.VaultServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class VaultToolTest {

    private VaultTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        VaultServiceImpl service = new VaultServiceImpl(apiClient, new ObjectMapper());
        tool = new VaultTool(service);
    }

    @Test
    void listVaultInstancesDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/vaults/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vault-1",
                                    "name": "HashiCorp Vault",
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

        String result = tool.listVaultInstances(null, null, null);

        assertThat(result).contains("Found 1 vault instances");
        assertThat(result).contains("HashiCorp Vault");

        verify(postRequestedFor(urlEqualTo("/v1/vaults/list")));
    }

    @Test
    void listVaultProfilesDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/vaultProfiles/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vp-1",
                                    "name": "prod-profile",
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

        String result = tool.listVaultProfiles(null, null, null);

        assertThat(result).contains("Found 1 vault profiles");
        assertThat(result).contains("prod-profile");

        verify(postRequestedFor(urlEqualTo("/v1/vaultProfiles/list")));
    }
}
