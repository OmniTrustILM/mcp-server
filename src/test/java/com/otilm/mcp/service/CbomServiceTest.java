package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static com.otilm.mcp.IlmTestSupport.stubIlmVersion;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class CbomServiceTest {

    private static final String CBOM_UUID = "fb2bce9f-99c9-4a71-a6cd-6e4c1a10d843";

    private CbomService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        service = new CbomServiceImpl(client, new PlatformVersionCheck(client));
    }

    @Test
    void searchListsCbomsWithCountsAndSyncState() {
        stubFor(post(urlEqualTo("/v1/cboms")).willReturn(okJson("""
                {
                    "items": [
                        {
                            "uuid": "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
                            "serialNumber": "urn:uuid:3b9d2f1a-6c4e-4a8b-9d7f-2e1c0b9a8f7e",
                            "version": 3,
                            "specVersion": "1.6",
                            "timestamp": "2026-10-01T10:00:00Z",
                            "source": "CBOM-Lens",
                            "algorithms": 10,
                            "certificates": 20,
                            "protocols": 5,
                            "cryptoMaterial": 5,
                            "totalAssets": 40,
                            "assetSyncState": "failed",
                            "assetSyncError": "Document could not be read"
                        }
                    ],
                    "itemsPerPage": 10,
                    "pageNumber": 1,
                    "totalPages": 1,
                    "totalItems": 1
                }
                """)));

        assertThat(service.searchCboms(null, null, null))
                .contains("Found 1 CBOMs (page 1 of 1)",
                        "- urn:uuid:3b9d2f1a-6c4e-4a8b-9d7f-2e1c0b9a8f7e v3 [1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d]",
                        "  Spec Version: 1.6", "  Timestamp: 2026-10-01T10:00Z", "  Source: CBOM-Lens",
                        "  Assets: 40 (algorithms 10, certificates 20, protocols 5, crypto material 5)",
                        "  Asset Sync: failed", "  Asset Sync Error: Document could not be read");
    }

    @Test
    void searchReadsCbomsWithoutSyncFields() {
        stubFor(post(urlEqualTo("/v1/cboms")).willReturn(okJson("""
                {
                    "items": [
                        {
                            "uuid": "2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e",
                            "serialNumber": "urn:uuid:4c0e3a2b-7d5f-4b9c-8e1a-3f2d1c0b9a8e",
                            "version": 1,
                            "specVersion": "1.6",
                            "source": "CBOM-Lens",
                            "algorithms": 1,
                            "certificates": 0,
                            "protocols": 0,
                            "cryptoMaterial": 0,
                            "totalAssets": 1
                        }
                    ],
                    "itemsPerPage": 10,
                    "pageNumber": 1,
                    "totalPages": 1,
                    "totalItems": 1
                }
                """)));

        assertThat(service.searchCboms(null, null, null))
                .contains("  Assets: 1 (")
                .doesNotContain("Asset Sync", "null");
    }

    @Test
    void searchReadsRecordedPage() {
        stubFor(post(urlEqualTo("/v1/cboms")).willReturn(okJson(fixture("cboms"))));

        assertThat(service.searchCboms(null, 5, 1))
                .contains("Found 71 CBOMs (page 1 of 15)", "v3 [" + CBOM_UUID + "]", "  Asset Sync: synced");
    }

    @Test
    void noCbomsSaysSo() {
        stubFor(post(urlEqualTo("/v1/cboms")).willReturn(okJson("""
                {"items": [], "itemsPerPage": 10, "pageNumber": 1, "totalPages": 0, "totalItems": 0}
                """)));

        assertThat(service.searchCboms(null, null, null)).contains("No CBOMs found.");
    }

    @Test
    void detailShowsCustomAttributesButNotTheDocument() {
        stubFor(get(urlEqualTo("/v1/cboms/" + CBOM_UUID)).willReturn(okJson("""
                {
                    "uuid": "fb2bce9f-99c9-4a71-a6cd-6e4c1a10d843",
                    "createdAt": "2026-10-14T18:00:01Z",
                    "serialNumber": "urn:uuid:3b9d2f1a-6c4e-4a8b-9d7f-2e1c0b9a8f7e",
                    "version": 3,
                    "specVersion": "1.6",
                    "source": "CBOM-Lens",
                    "algorithms": 1,
                    "certificates": 0,
                    "protocols": 0,
                    "cryptoMaterial": 0,
                    "totalAssets": 1,
                    "content": {"bomFormat": "CycloneDX", "contentMarker": "raw"},
                    "customAttributes": [
                        {
                            "uuid": "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f",
                            "name": "owner",
                            "label": "Owner",
                            "type": "custom",
                            "contentType": "string",
                            "version": "v3",
                            "content": [{"data": "team-1", "contentType": "string"}]
                        }
                    ]
                }
                """)));

        assertThat(service.getCbom(CBOM_UUID))
                .contains("CBOM: urn:uuid:3b9d2f1a-6c4e-4a8b-9d7f-2e1c0b9a8f7e v3", "  UUID: " + CBOM_UUID,
                        "  Created: 2026-10-14T18:00:01Z", "  Custom Attributes:", "    owner: team-1")
                .doesNotContain("contentMarker", "CycloneDX");
    }

    @Test
    void detailReadsRecordedCbom() {
        stubFor(get(urlEqualTo("/v1/cboms/" + CBOM_UUID)).willReturn(okJson(fixture("cbom"))));

        assertThat(service.getCbom(CBOM_UUID))
                .contains("CBOM: 0000000000000000000000000000000000000004 v3", "  Asset Sync: synced")
                .doesNotContain("contentMarker", "Custom Attributes");
    }

    @Test
    void versionsListEachVersion() {
        stubFor(get(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/versions")).willReturn(okJson(fixture("cbom-versions"))));

        assertThat(service.getCbomVersions(CBOM_UUID))
                .contains("CBOM Versions for " + CBOM_UUID, "- v3 [" + CBOM_UUID + "]",
                        "- v1 [210cb55b-7fe1-4d0e-b684-9f8f276b1b27]", "  Timestamp: 2025-05-31T03:45:47Z");
    }

    @Test
    void assetsRefuseOlderIlm() {
        stubIlmVersion("2.19.0");

        assertThat(service.searchCbomAssets(CBOM_UUID, null, null, null))
                .isEqualTo("CBOM asset listing requires ILM 2.20 or later; the connected ILM is 2.19.0.");
        verify(0, postRequestedFor(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/assets")));
    }

    @Test
    void assetsListContributedAssets() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/assets")).willReturn(okJson(fixture("cbom-assets"))));

        assertThat(service.searchCbomAssets(CBOM_UUID, null, 5, 1))
                .contains("Found 61 cryptographic assets (page 1 of 13)",
                        "- name-12 [1795afd7-3140-4551-b59a-fc51cf42ad4d]", "  PQC Verdict: notReady");
    }

    @Test
    void cbomToolsBelowAssetsNeedNoVersionCheck() {
        stubFor(post(urlEqualTo("/v1/cboms")).willReturn(okJson(fixture("cboms"))));

        service.searchCboms(null, null, null);

        verify(0, getRequestedFor(urlEqualTo("/v1/info")));
    }
}
