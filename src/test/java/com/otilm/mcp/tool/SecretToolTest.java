package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.SecretServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SecretToolTest {

    private SecretTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        IlmApiClient apiClient = IlmTestSupport.client(wmInfo);
        SecretServiceImpl service = new SecretServiceImpl(apiClient);
        tool = new SecretTool(service);
    }

    @Test
    void searchSecretsDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/secrets"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "secret-1",
                                    "name": "db-password",
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

        String result = tool.searchSecrets(null, null, null);

        assertThat(result).contains("Found 1 secrets", "db-password");

        verify(postRequestedFor(urlEqualTo("/v1/secrets")));
    }

    @Test
    void getSecretDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/secrets/secret-uuid"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "secret-uuid",
                            "name": "my-secret",
                            "type": "apiKey",
                            "state": "active",
                            "version": 1,
                            "enabled": true
                        }
                        """)));

        String result = tool.getSecret("secret-uuid");

        assertThat(result).contains("Secret: my-secret", "UUID: secret-uuid");

        verify(getRequestedFor(urlEqualTo("/v1/secrets/secret-uuid")));
    }

    @Test
    void getSecretVersionsDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/secrets/secret-uuid/versions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "version": 1,
                                "createdAt": "2026-01-15T08:00:00.000Z",
                                "fingerprint": "sha256:abc123"
                            }
                        ]
                        """)));

        String result = tool.getSecretVersions("secret-uuid");

        assertThat(result).contains("Secret Versions for secret-uuid", "Version: 1");

        verify(getRequestedFor(urlEqualTo("/v1/secrets/secret-uuid/versions")));
    }
}
