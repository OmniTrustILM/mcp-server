package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SearchToolTest {

    private SearchTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient client = new IlmApiClient(restClient);
        SearchServiceImpl service = new SearchServiceImpl(client);
        tool = new SearchTool(service);
    }

    @Test
    void getSearchableFieldsDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/certificates/search"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "filterFieldSource": "property",
                                "searchFieldData": [
                                    {
                                        "fieldIdentifier": "commonName",
                                        "fieldLabel": "Common Name",
                                        "type": "string",
                                        "conditions": ["EQUALS", "CONTAINS"],
                                        "multiValue": false
                                    }
                                ]
                            }
                        ]
                        """)));

        String result = tool.getSearchableFields("certificates");

        assertThat(result)
                .contains("Available search fields for certificates")
                .contains("commonName")
                .contains("STRING")
                .contains("EQUALS");
    }

    @Test
    void getSearchableFieldsReturnsErrorForInvalidType() {
        String result = tool.getSearchableFields("invalid");

        assertThat(result).contains("Unknown resource type");
    }
}
