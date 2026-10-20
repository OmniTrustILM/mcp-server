package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.stubIlmVersion;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SearchServiceTest {

    private SearchService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        service = new SearchServiceImpl(client, new PlatformVersionCheck(client));
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
                .contains("Group: property")
                .contains("commonName")
                .contains("Common Name")
                .contains("STRING")
                .contains("CONTAINS")
                .contains("state")
                .contains("LIST")
                .contains("issued")
                .contains("revoked")
                .contains("Group: meta")
                .contains("metaField1")
                .contains("NUMBER")
                .doesNotContain("Group: PROPERTY");
    }

    @Test
    void shouldReturnErrorForInvalidResourceType() {
        String result = service.getSearchableFields("invalid");

        assertThat(result)
                .isEqualTo("Unknown resource type: invalid. Supported types: certificates, keys, secrets, vaults, "
                        + "vaultProfiles, entities, discoveries, cryptoAssets, cboms, signingProfiles, signingRecords, "
                        + "tspProfiles");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(get(urlEqualTo("/v1/keys/search")).willReturn(aResponse().withStatus(500)));

        String result = service.getSearchableFields("keys");

        assertThat(result).contains("Error");
    }

    @Test
    void shouldSupportAllResourceTypes() {
        stubIlmVersion("2.20.0");
        for (String resource : new String[]{
                "certificates",
                "keys",
                "secrets",
                "vaults",
                "vaultProfiles",
                "entities",
                "discoveries",
                "cryptoAssets",
                "cboms",
                "signingProfiles",
                "signingRecords",
                "tspProfiles"}) {
            stubFor(get(urlPathMatching("/v1/.*/search"))
                    .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("[]")));

            String result = service.getSearchableFields(resource);

            assertThat(result).contains("Available search fields for " + resource);
        }
    }

    @Test
    void cryptoAssetFieldsRefuseIlmBefore220() {
        stubIlmVersion("2.19.0");

        assertThat(service.getSearchableFields("cryptoAssets"))
                .isEqualTo("Cryptographic asset inventory requires ILM 2.20 or later; the connected ILM is 2.19.0.");
        verify(0, getRequestedFor(urlEqualTo("/v1/cryptoAssets/search")));
    }

    @Test
    void cbomFieldsNeedNoVersionCheck() {
        stubFor(get(urlEqualTo("/v1/cboms/search")).willReturn(okJson("[]")));

        assertThat(service.getSearchableFields("cboms")).contains("Available search fields for cboms");
        verify(0, getRequestedFor(urlEqualTo("/v1/info")));
    }

    @Test
    void signingRecordFieldsOnIlm220() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/signingRecords/search")).willReturn(okJson("[]")));

        assertThat(service.getSearchableFields("signingRecords"))
                .contains("Available search fields for signingRecords");
        verify(getRequestedFor(urlEqualTo("/v1/signingRecords/search")));
    }
}
