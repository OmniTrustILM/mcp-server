package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SearchServiceTest {

    private SearchService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        service = new SearchServiceImpl(client);
    }

    @Test
    void shouldFormatSearchableFieldsForCertificates() {
        stubFor(get(urlEqualTo("/v1/certificates/search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        [
                                            {
                                                "filterFieldSource": "property",
                                                "searchFieldData": [
                                                    {
                                                        "fieldIdentifier": "commonName",
                                                        "fieldLabel": "Common Name",
                                                        "type": "string",
                                                        "conditions": ["EQUALS", "NOT_EQUALS", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "EMPTY", "NOT_EMPTY"],
                                                        "multiValue": false
                                                    },
                                                    {
                                                        "fieldIdentifier": "state",
                                                        "fieldLabel": "State",
                                                        "type": "list",
                                                        "conditions": ["EQUALS", "NOT_EQUALS"],
                                                        "value": ["issued", "revoked"],
                                                        "multiValue": false
                                                    }
                                                ]
                                            },
                                            {
                                                "filterFieldSource": "meta",
                                                "searchFieldData": [
                                                    {
                                                        "fieldIdentifier": "metaField1",
                                                        "fieldLabel": "Meta Field 1",
                                                        "type": "number",
                                                        "conditions": ["EQUALS", "GREATER", "LESSER"],
                                                        "multiValue": false
                                                    }
                                                ]
                                            }
                                        ]
                                        """)));

        String result = service.getSearchableFields("certificates");

        assertThat(result)
                .contains("Available search fields for certificates")
                .contains("PROPERTY")
                .contains("commonName")
                .contains("Common Name")
                .contains("STRING")
                .contains("CONTAINS")
                .contains("state")
                .contains("LIST")
                .contains("issued")
                .contains("revoked")
                .contains("META")
                .contains("metaField1")
                .contains("NUMBER");
    }

    @Test
    void shouldReturnErrorForInvalidResourceType() {
        String result = service.getSearchableFields("invalid");

        assertThat(result)
                .contains("Unknown resource type")
                .contains("certificates")
                .contains("keys")
                .contains("secrets");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(get(urlEqualTo("/v1/keys/search")).willReturn(aResponse().withStatus(500)));

        String result = service.getSearchableFields("keys");

        assertThat(result).contains("Error");
    }

    @Test
    void shouldSupportAllResourceTypes() {
        for (String resource : new String[]{
                "certificates",
                "keys",
                "secrets",
                "vaults",
                "vaultProfiles",
                "entities",
                "discoveries"}) {
            stubFor(get(urlPathMatching("/v1/.*/search"))
                    .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[]")));

            String result = service.getSearchableFields(resource);

            assertThat(result).contains("Available search fields for " + resource);
        }
    }
}
