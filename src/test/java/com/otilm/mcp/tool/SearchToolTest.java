package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.PlatformVersionCheck;
import com.otilm.mcp.service.SearchServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        SearchServiceImpl service = new SearchServiceImpl(client, new PlatformVersionCheck(client));
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
