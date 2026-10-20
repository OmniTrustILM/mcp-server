package com.otilm.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.KeyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

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
class KeyToolTest {

    private KeyTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        KeyServiceImpl service = new KeyServiceImpl(apiClient, new ObjectMapper());
        tool = new KeyTool(service);
    }

    @Test
    void searchKeysDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/keys"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "cryptographicKeys": [
                                {
                                    "uuid": "key-1",
                                    "name": "rsa-signing-key",
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

        String result = tool.searchKeys(null, 10, 1);

        assertThat(result).contains("Found 1 cryptographic keys");
        assertThat(result).contains("rsa-signing-key");

        verify(postRequestedFor(urlEqualTo("/v1/keys")));
    }

    @Test
    void getKeyDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/keys/key-uuid"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "key-uuid",
                            "name": "my-key",
                            "description": "Test key",
                            "complianceStatus": "ok",
                            "items": []
                        }
                        """)));

        String result = tool.getKey("key-uuid");

        assertThat(result).contains("Key: my-key");
        assertThat(result).contains("UUID: key-uuid");

        verify(getRequestedFor(urlEqualTo("/v1/keys/key-uuid")));
    }
}
