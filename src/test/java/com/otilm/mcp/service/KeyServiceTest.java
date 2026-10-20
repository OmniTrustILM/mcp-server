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
class KeyServiceTest {

    private KeyService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        service = new KeyServiceImpl(apiClient, new ObjectMapper());
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

        assertThat(result).contains("Found 2 cryptographic keys");
        assertThat(result).contains("production-rsa-key");
        assertThat(result).contains("test-ecdsa-key");
        assertThat(result).contains("key-uuid-1");
        assertThat(result).contains("key-uuid-2");
        assertThat(result).contains("Algorithm: RSA");
        assertThat(result).contains("Algorithm: ECDSA");
        assertThat(result).contains("Size: 2048 bits");
        assertThat(result).contains("Size: 256 bits");
        assertThat(result).contains("Token Instance: SoftHSM");
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

        assertThat(result).contains("Key: my-signing-key");
        assertThat(result).contains("UUID: key-1");
        assertThat(result).contains("Description: Used for code signing");
        assertThat(result).contains("Token Instance: SoftHSM");
        assertThat(result).contains("Token Profile: signing-profile");
        assertThat(result).contains("Owner: devops-team");
        assertThat(result).contains("Compliance: ok");
        assertThat(result).contains("Key Items:");
        assertThat(result).contains("my-signing-key-private");
        assertThat(result).contains("Algorithm: RSA");
        assertThat(result).contains("Size: 4096 bits");
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
