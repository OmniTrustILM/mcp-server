package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
class KeyServiceTest {

    private KeyService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        IlmApiClient apiClient = IlmTestSupport.client(wmInfo);
        service = new KeyServiceImpl(apiClient);
    }

    @Test
    void searchShowsTheKeyUuidThatGetKeyAccepts() {
        stubFor(post(urlEqualTo("/v1/keys"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "cryptographicKeys": [
                                {
                                    "uuid": "6c1f8a2e-3b4d-4e5f-8a9b-0c1d2e3f4a5b",
                                    "keyWrapperUuid": "e7d6c5b4-a392-4817-9654-3f2e1d0c9b8a",
                                    "name": "signing-key",
                                    "keyAlgorithm": "RSA",
                                    "length": 2048,
                                    "type": "Private",
                                    "state": "active",
                                    "enabled": true
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String result = service.searchKeys(null, null, null);

        assertThat(result).contains("  Key UUID: e7d6c5b4-a392-4817-9654-3f2e1d0c9b8a");
    }

    @Test
    void shouldFormatKeyList() {
        stubFor(post(urlEqualTo("/v1/keys"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "cryptographicKeys": [
                                {
                                    "uuid": "key-uuid-1",
                                    "name": "production-rsa-key",
                                    "keyAlgorithm": "RSA",
                                    "length": 2048,
                                    "type": "Private",
                                    "state": "active",
                                    "enabled": true,
                                    "tokenInstanceName": "SoftHSM",
                                    "tokenProfileName": "default-profile",
                                    "owner": "admin"
                                },
                                {
                                    "uuid": "key-uuid-2",
                                    "name": "test-ecdsa-key",
                                    "keyAlgorithm": "ECDSA",
                                    "length": 256,
                                    "type": "Private",
                                    "state": "active",
                                    "enabled": true
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 2
                        }
                        """)));

        String result = service.searchKeys(null, null, null);

        assertThat(result)
                .contains("Found 2 cryptographic keys", "production-rsa-key", "test-ecdsa-key", "key-uuid-1",
                        "key-uuid-2", "Algorithm: RSA", "Algorithm: ECDSA", "Size: 2048 bits", "Size: 256 bits",
                        "Token Instance: SoftHSM");
    }

    @Test
    void shouldFormatKeyDetail() {
        stubFor(get(urlEqualTo("/v1/keys/key-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "key-1",
                            "name": "my-signing-key",
                            "description": "Used for code signing",
                            "creationTime": "2025-01-15T08:00:00.000Z",
                            "tokenInstanceName": "SoftHSM",
                            "tokenProfileName": "signing-profile",
                            "owner": "devops-team",
                            "complianceStatus": "ok",
                            "groups": [],
                            "items": [
                                {
                                    "uuid": "item-uuid-1",
                                    "name": "my-signing-key-private",
                                    "type": "Private",
                                    "keyAlgorithm": "RSA",
                                    "length": 4096,
                                    "format": "PrivateKeyInfo",
                                    "state": "active",
                                    "enabled": true,
                                    "usage": ["sign", "decrypt"]
                                }
                            ]
                        }
                        """)));

        String result = service.getKey("key-1");

        assertThat(result)
                .contains("Key: my-signing-key", "UUID: key-1", "Description: Used for code signing",
                        "Token Instance: SoftHSM", "Token Profile: signing-profile", "Owner: devops-team",
                        "Compliance: ok", "Key Items:", "my-signing-key-private", "Algorithm: RSA", "Size: 4096 bits");
    }

    @Test
    void shouldPassFiltersToSearchRequest() {
        stubFor(post(urlEqualTo("/v1/keys"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "cryptographicKeys": [
                                {
                                    "uuid": "key-1",
                                    "name": "filtered-key",
                                    "keyAlgorithm": "RSA",
                                    "length": 2048,
                                    "type": "Private",
                                    "state": "active",
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
        String result = service.searchKeys(filters, 10, 1);

        assertThat(result).contains("filtered-key").contains("1 cryptographic keys");

        verify(postRequestedFor(urlEqualTo("/v1/keys"))
                .withRequestBody(containing("\"fieldIdentifier\":\"name\""))
                .withRequestBody(containing("\"condition\":\"CONTAINS\""))
                .withRequestBody(containing("\"value\":\"filtered\"")));
    }

    @Test
    void shouldReturnErrorForInvalidFiltersJson() {
        String result = service.searchKeys("not valid json", null, null);

        assertThat(result).contains("Invalid filters JSON");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(post(urlEqualTo("/v1/keys"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody("""
                        {"message": "Internal Server Error"}
                        """)));

        String result = service.searchKeys(null, null, null);

        assertThat(result).contains("Error searching keys");
    }
}
