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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static com.otilm.mcp.IlmTestSupport.stubIlmVersion;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class CryptoAssetServiceTest {

    private static final String ASSET_UUID = "82643a70-90b8-4042-a4d2-4723b68e8391";
    private static final String STATISTICS_PATH = "/v1/statistics/cryptoAssets";

    private CryptoAssetService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        service = new CryptoAssetServiceImpl(client, new PlatformVersionCheck(client));
    }

    @Test
    void statisticsSummarizePqcReadiness() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo(STATISTICS_PATH)).willReturn(okJson(fixture("crypto-asset-statistics"))));

        assertThat(service.getStatistics())
                .contains("Total Assets: 573", "Source CBOMs: 37", "Untyped Assets: 0", "Assets by PQC Verdict:",
                        "  notReady: 389", "  ready: 59", "Assets by Type:", "  certificate: 164",
                        "Top Algorithm Families:", "  ml-dsa: 17", "Distinct Algorithm Families: 44",
                        "Assets Without Algorithm Family: 95", "CBOMs by Sync State:", "  synced: 71",
                        "Last Completed Sync: 2026-10-14T18:00:00.941409Z");
    }

    @Test
    void statisticsRefuseOlderIlm() {
        stubIlmVersion("2.19.1");

        assertThat(service.getStatistics())
                .isEqualTo("Cryptographic asset inventory requires ILM 2.20 or later; the connected ILM is 2.19.1.");
        verify(0, getRequestedFor(urlEqualTo(STATISTICS_PATH)));
    }

    @Test
    void statisticsStillCallIlmWhenVersionIsUnreadable() {
        stubFor(get(urlEqualTo("/v1/info")).willReturn(unauthorized()));
        stubFor(get(urlEqualTo(STATISTICS_PATH)).willReturn(okJson(fixture("crypto-asset-statistics"))));

        assertThat(service.getStatistics()).contains("Total Assets: 573");
        verify(getRequestedFor(urlEqualTo(STATISTICS_PATH)));
    }

    @Test
    void searchReadsRecordedPage() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/cryptoAssets")).willReturn(okJson(fixture("crypto-assets"))));

        assertThat(service.searchCryptoAssets(null, 5, 1))
                .contains("Found 573 cryptographic assets (page 1 of 115)", "- name-1 [" + ASSET_UUID + "]",
                        "  Type: algorithm", "  PQC Verdict: notReady", "  Source CBOMs: 1", "  Occurrences: 1")
                .doesNotContain("Quarantined");
    }

    @Test
    void searchMarksQuarantinedAssetsAndSendsFilters() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/cryptoAssets")).willReturn(okJson("""
                {
                    "items": [
                        {
                            "uuid": "6e5d4c3b-2a19-4807-96f5-e4d3c2b1a098",
                            "name": "RSA-2048",
                            "type": "algorithm",
                            "pqcVerdict": "notReady",
                            "sourceCbomCount": 2,
                            "occurrenceCount": 15,
                            "quarantined": true
                        }
                    ],
                    "itemsPerPage": 10,
                    "pageNumber": 1,
                    "totalPages": 1,
                    "totalItems": 1
                }
                """)));

        String result = service
                .searchCryptoAssets(
                        """
                                [{"fieldSource":"property","fieldIdentifier":"CBOM_ASSET_PQC_VERDICT","condition":"EQUALS","value":"notReady"}]
                                """,
                        null, null);

        assertThat(result)
                .contains("- RSA-2048 [6e5d4c3b-2a19-4807-96f5-e4d3c2b1a098]", "  Source CBOMs: 2", "  Occurrences: 15",
                        "  Quarantined: true");
        verify(postRequestedFor(urlEqualTo("/v1/cryptoAssets"))
                .withRequestBody(containing("\"fieldIdentifier\":\"CBOM_ASSET_PQC_VERDICT\"")));
    }

    @Test
    void searchWithNoAssetsSaysSo() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/cryptoAssets")).willReturn(okJson("""
                {"items": [], "itemsPerPage": 10, "pageNumber": 1, "totalPages": 0, "totalItems": 0}
                """)));

        assertThat(service.searchCryptoAssets(null, null, null)).contains("No cryptographic assets found.");
    }

    @Test
    void searchRejectsInvalidFiltersWithoutCallingIlm() {
        stubIlmVersion("2.20.0");

        assertThat(service.searchCryptoAssets("not json", null, null)).contains("Invalid filters JSON");
        verify(0, postRequestedFor(urlEqualTo("/v1/cryptoAssets")));
    }

    @Test
    void detailShowsVerdictFieldsOidsAndSourcesButNoPayloads() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/cryptoAssets/6e5d4c3b-2a19-4807-96f5-e4d3c2b1a098"))
                .willReturn(
                        okJson("""
                                {
                                    "uuid": "6e5d4c3b-2a19-4807-96f5-e4d3c2b1a098",
                                    "name": "RSA-2048",
                                    "type": "algorithm",
                                    "pqcVerdict": "notReady",
                                    "sourceCbomCount": 1,
                                    "occurrenceCount": 12,
                                    "quarantined": false,
                                    "verdict": {
                                        "ruleId": "CLASSICAL-RSA",
                                        "reason": "RSA is broken by a cryptographically relevant quantum computer",
                                        "referencedAsset": {"uuid": "7f6e5d4c-3b2a-4190-8a7b-6c5d4e3f2a1b", "visible": true, "name": "rsaEncryption", "type": "algorithm"},
                                        "decidedAt": "2026-10-14T08:22:36Z",
                                        "evaluatedAt": "2026-10-15T08:22:36Z"
                                    },
                                    "normalizedFields": {"algorithmFamily": "RSA", "primitive": "pke", "parameterSet": "2048"},
                                    "electedPayload": {"payloadMarker": "elected"},
                                    "sources": [
                                        {
                                            "cbomUuid": "8a7b6c5d-4e3f-4a1b-9c0d-1e2f3a4b5c6d",
                                            "serialNumber": "urn:uuid:3b9d2f1a-6c4e-4a8b-9d7f-2e1c0b9a8f7e",
                                            "version": 3,
                                            "occurrenceCount": 12,
                                            "source": "CBOM-Lens",
                                            "payload": {"payloadMarker": "source"},
                                            "evidence": [{"location": "src/a.c", "line": 10}, {"location": "src/b.c", "line": 20}]
                                        }
                                    ],
                                    "oids": [{"oid": "1.2.840.113549.1.1.1", "refuted": false}, {"oid": "1.2.3.4", "refuted": true}]
                                }
                                """)));

        assertThat(service.getCryptoAsset("6e5d4c3b-2a19-4807-96f5-e4d3c2b1a098"))
                .contains("Cryptographic Asset: RSA-2048", "  UUID: 6e5d4c3b-2a19-4807-96f5-e4d3c2b1a098",
                        "  PQC Verdict: notReady", "  Verdict Rule: CLASSICAL-RSA",
                        "  Verdict Reason: RSA is broken by a cryptographically relevant quantum computer",
                        "  Verdict Decided: 2026-10-14T08:22:36Z",
                        "  Referenced Asset: rsaEncryption [7f6e5d4c-3b2a-4190-8a7b-6c5d4e3f2a1b]",
                        "  Algorithm Family: RSA", "  Primitive: pke", "  Parameter Set: 2048",
                        "  OIDs: 1.2.840.113549.1.1.1, 1.2.3.4 (refuted)", "  Sources:",
                        "    - urn:uuid:3b9d2f1a-6c4e-4a8b-9d7f-2e1c0b9a8f7e v3 (CBOM-Lens), recorded locations: 12\n")
                .doesNotContain("payloadMarker", "Quarantined");
    }

    @Test
    void detailReadsRecordedAsset() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID)).willReturn(okJson(fixture("crypto-asset"))));

        assertThat(service.getCryptoAsset(ASSET_UUID))
                .contains("Cryptographic Asset: name-3", "  Verdict Rule: SYMMETRIC-UNDERSIZED",
                        "  Algorithm Family: snow3g", "  Primitive: stream-cipher",
                        "    - 0000000000000000000000000000000000000001 v1 (source-3), recorded locations: none\n")
                .doesNotContain("payloadMarker", "OIDs:", "occurrences\n");
    }

    @Test
    void explanationListsStepsInOrder() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID + "/pqcExplanation"))
                .willReturn(okJson(fixture("crypto-asset-pqc-explanation"))));

        String result = service.explainPqcVerdict(ASSET_UUID);

        assertThat(result)
                .contains("PQC Verdict Explanation for " + ASSET_UUID, "  Verdict: notReady",
                        "  Rule: SYMMETRIC-UNDERSIZED", "  Matches Stored Verdict: true", "  Steps:",
                        "    1. title-1 [NAME-CIPHER-SUITE]: notMatched", "       message-1",
                        "       Fields: assetType=assetType-2, algorithmFamily=SNOW3G, name=name-5", "    21. ")
                .doesNotContain("  Stored Verdict:");
        assertThat(result.indexOf("    1. title-1")).isLessThan(result.indexOf("    2. title-2"));
    }

    @Test
    void explanationShowsStoredVerdictWhenItDiffers() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID + "/pqcExplanation"))
                .willReturn(
                        okJson("""
                                {
                                    "uuid": "82643a70-90b8-4042-a4d2-4723b68e8391",
                                    "verdict": "notReady",
                                    "ruleId": "CLASSICAL-RSA",
                                    "reason": "RSA is not quantum safe",
                                    "steps": [
                                        {
                                            "ruleId": "CLASSICAL-RSA",
                                            "title": "Classical RSA",
                                            "outcome": "decided",
                                            "verdict": "notReady",
                                            "referencedAsset": {"uuid": "7f6e5d4c-3b2a-4190-8a7b-6c5d4e3f2a1b", "visible": false, "type": "algorithm"}
                                        }
                                    ],
                                    "matchesStored": false,
                                    "storedVerdict": "ready",
                                    "storedRuleId": "PQC-ML-DSA"
                                }
                                """)));

        assertThat(service.explainPqcVerdict(ASSET_UUID))
                .contains("  Matches Stored Verdict: false", "  Stored Verdict: ready (rule PQC-ML-DSA)",
                        "    1. Classical RSA [CLASSICAL-RSA]: decided (verdict notReady)",
                        "       Referenced Asset: N/A [7f6e5d4c-3b2a-4190-8a7b-6c5d4e3f2a1b]");
    }

    @Test
    void unknownAssetAnswersPlainly() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Cryptographic asset not found\"}")));

        assertThat(service.getCryptoAsset(ASSET_UUID))
                .isEqualTo(
                        "Error retrieving cryptographic asset: ILM answered 404 Not Found: Cryptographic asset not found");
    }
}
