package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static com.otilm.mcp.IlmTestSupport.stubIlmVersion;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SigningServiceTest {

    private static final String PROFILE_UUID = "38f6e529-8123-4ead-a5f1-f3e65d8c06a9";

    private SigningService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        service = new SigningServiceImpl(client, new PlatformVersionCheck(client));
    }

    @Test
    void searchListsProfiles() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/signingProfiles/list")).willReturn(okJson(fixture("signing-profiles"))));

        assertThat(service.searchSigningProfiles(null, 50, 1))
                .contains("Found 98 signing profiles (page 1 of 2)", "- name-14 [" + PROFILE_UUID + "]", "  Version: 1",
                        "  Signing Scheme: managed", "  Workflow: timestamping", "  Enabled: true", "  Protocols: tsp");
    }

    @Test
    void staticKeyProfileShowsCertificateAndRecording() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/signingProfiles/" + PROFILE_UUID))
                .willReturn(okJson(fixture("signing-profile-static"))));

        assertThat(service.getSigningProfile(PROFILE_UUID))
                .contains("Signing Profile: name-16", "  UUID: " + PROFILE_UUID, "  Version: 1",
                        "  Signing Scheme: managed (static_key)",
                        "  Certificate: commonName-1.example.com [552038e1-4278-4785-b93e-1e4d092ca3fd]",
                        "  Workflow: timestamping", "  Recording: enabled", "  Protocols: tsp");
    }

    @Test
    void oneTimeKeyProfileShowsProfilesAndCustomAttributes() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/signingProfiles/" + PROFILE_UUID)).willReturn(okJson("""
                {
                    "uuid": "38f6e529-8123-4ead-a5f1-f3e65d8c06a9",
                    "name": "document-signing",
                    "version": 2,
                    "enabled": true,
                    "enabledProtocols": [],
                    "signingScheme": {
                        "signingScheme": "managed",
                        "managedSigningType": "one_time_key",
                        "raProfile": {"uuid": "6d7e8f90-a1b2-4c3d-8e4f-5a6b7c8d9e0f", "name": "signing-ra"},
                        "csrTemplate": {"uuid": "7e8f90a1-b2c3-4d4e-9f5a-6b7c8d9e0f1a", "name": "signing-csr"},
                        "tokenProfile": {"uuid": "8f90a1b2-c3d4-4e5f-8a6b-7c8d9e0f1a2b", "name": "hsm-profile"}
                    },
                    "workflow": {"type": "content_signing"},
                    "recordPolicy": {"recordingEnabled": true, "retentionDays": 30},
                    "customAttributes": [
                        {
                            "uuid": "9a0b1c2d-3e4f-4a5b-8c6d-7e8f9a0b1c2d",
                            "name": "department",
                            "label": "Department",
                            "type": "custom",
                            "contentType": "string",
                            "version": "v3",
                            "content": [{"data": "team-1", "contentType": "string"}]
                        }
                    ]
                }
                """)));

        assertThat(service.getSigningProfile(PROFILE_UUID))
                .contains("  Signing Scheme: managed (one_time_key)", "  RA Profile: signing-ra",
                        "  CSR Template: signing-csr", "  Token Profile: hsm-profile", "  Workflow: content_signing",
                        "  Recording: enabled, retention 30 days", "  Custom Attributes:", "    department: team-1")
                .doesNotContain("Protocols:");
    }

    @Test
    void delegatedProfileShowsConnector() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/signingProfiles/" + PROFILE_UUID)).willReturn(okJson("""
                {
                    "uuid": "38f6e529-8123-4ead-a5f1-f3e65d8c06a9",
                    "name": "remote-signing",
                    "version": 1,
                    "enabled": false,
                    "signingScheme": {
                        "signingScheme": "delegated",
                        "connector": {"uuid": "a0b1c2d3-e4f5-4a6b-9c7d-8e9f0a1b2c3d", "name": "csc-connector"},
                        "attributes": []
                    },
                    "workflow": {"type": "raw_signing"},
                    "recordPolicy": {"recordingEnabled": false}
                }
                """)));

        assertThat(service.getSigningProfile(PROFILE_UUID))
                .contains("  Signing Scheme: delegated", "  Connector: csc-connector", "  Enabled: false",
                        "  Recording: disabled");
    }

    @Test
    void recordsListWithoutContent() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/signingRecords")).willReturn(okJson(fixture("signing-records"))));

        assertThat(service.searchSigningRecords(null, 5, 1))
                .contains("Found 147 signing records (page 1 of 30)",
                        "- name-23 [2607d7d7-8c8f-4119-97fc-6d220f061f33]", "  Signing Profile: name-24",
                        "  Protocol: tsp", "  Signing Time: 2026-10-13T13:16:33.234020Z",
                        "  Timestamp Token Serials: 0000000000000000000000000000000000000001");
    }

    @Test
    void recordsNeverShowSignatureValues() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/signingRecords")).willReturn(okJson("""
                {
                    "items": [
                        {
                            "uuid": "b1c2d3e4-f5a6-4b7c-8d9e-0f1a2b3c4d5e",
                            "name": "record-1",
                            "protocol": "tsp",
                            "signatureValue": "c2lnbmF0dXJlTWFya2Vy"
                        }
                    ],
                    "itemsPerPage": 10,
                    "pageNumber": 1,
                    "totalPages": 1,
                    "totalItems": 1
                }
                """)));

        assertThat(service.searchSigningRecords(null, null, null))
                .contains("record-1")
                .doesNotContain("c2lnbmF0dXJlTWFya2Vy");
    }

    @Test
    void noRecordsSaysSo() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/signingRecords")).willReturn(okJson("""
                {"items": [], "itemsPerPage": 10, "pageNumber": 1, "totalPages": 0, "totalItems": 0}
                """)));

        assertThat(service.searchSigningRecords(null, null, null)).contains("No signing records found.");
    }

    @Test
    void statisticsSummarizeVolume() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlPathEqualTo("/v1/statistics/signingRecords")).willReturn(okJson(fixture("signing-statistics"))));

        assertThat(service.getSigningStatistics("7d"))
                .contains("Records Retained: 147", "Signed in Last 24 Hours: 0", "Signed in Last 7 Days: 16",
                        "Active Profiles: 73", "Distinct Requesters: 2", "Volume (7d):", "  2026-10-16T00:00:00Z: 16",
                        "Signings by Profile:", "Signings by Workflow Type:", "  timestamping: 147",
                        "Signings by Protocol:", "  tsp: 147", "Signings by Scheme:", "  managed_static_key: 147");
        verify(getRequestedFor(urlEqualTo("/v1/statistics/signingRecords?period=7d")));
    }

    @Test
    void statisticsWithoutPeriodUseIlmDefault() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlPathEqualTo("/v1/statistics/signingRecords")).willReturn(okJson(fixture("signing-statistics"))));

        assertThat(service.getSigningStatistics(null)).contains("Volume:");
        verify(getRequestedFor(urlEqualTo("/v1/statistics/signingRecords")));
    }

    @Test
    void statisticsRejectUnknownPeriod() {
        assertThat(service.getSigningStatistics("week"))
                .isEqualTo("Unknown period 'week'. Use one of: 24h, 7d, 30d, 90d.");
        verify(0, getRequestedFor(anyUrl()));
    }

    @Test
    void tspProfilesList() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/tspProfiles/list")).willReturn(okJson(fixture("tsp-profiles"))));

        assertThat(service.searchTspProfiles(null, 5, 1))
                .contains("Found 73 TSP profiles (page 1 of 15)", "- name-27 [ec658b0a-a6b2-4419-802d-9be6aa2d09ce]",
                        "  Default Signing Profile: name-28", "  Signing URL: https://signingurl-1.example.com",
                        "  Enabled: true");
    }

    @Test
    void profilesRefuseIlmBefore218() {
        stubIlmVersion("2.17.4");

        assertThat(service.searchSigningProfiles(null, null, null))
                .isEqualTo("Signing profiles requires ILM 2.18 or later; the connected ILM is 2.17.4.");
        verify(0, postRequestedFor(urlEqualTo("/v1/signingProfiles/list")));
    }

    @Test
    void recordsRefuseIlmBefore219() {
        stubIlmVersion("2.18.0");

        assertThat(service.searchSigningRecords(null, null, null))
                .isEqualTo("Signing records requires ILM 2.19 or later; the connected ILM is 2.18.0.");
        verify(0, postRequestedFor(urlEqualTo("/v1/signingRecords")));
    }

    @Test
    void certificateWithoutCommonNameShowsNotAvailable() {
        stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/signingProfiles/" + PROFILE_UUID)).willReturn(okJson("""
                {
                    "uuid": "38f6e529-8123-4ead-a5f1-f3e65d8c06a9",
                    "name": "no-cn-signing",
                    "version": 1,
                    "enabled": true,
                    "signingScheme": {
                        "signingScheme": "managed",
                        "managedSigningType": "static_key",
                        "certificate": {"uuid": "552038e1-4278-4785-b93e-1e4d092ca3fd"}
                    },
                    "workflow": {"type": "raw_signing"}
                }
                """)));

        assertThat(service.getSigningProfile(PROFILE_UUID))
                .contains("  Certificate: N/A [552038e1-4278-4785-b93e-1e4d092ca3fd]")
                .doesNotContain("null");
    }

    @Test
    void tspProfilesRefuseIlmBefore218() {
        stubIlmVersion("2.17.4");

        assertThat(service.searchTspProfiles(null, null, null))
                .isEqualTo("TSP profiles requires ILM 2.18 or later; the connected ILM is 2.17.4.");
        verify(0, postRequestedFor(urlEqualTo("/v1/tspProfiles/list")));
    }

    @Test
    void statisticsRefuseIlmBefore219() {
        stubIlmVersion("2.18.0");

        assertThat(service.getSigningStatistics("7d"))
                .isEqualTo("Signing statistics requires ILM 2.19 or later; the connected ILM is 2.18.0.");
        verify(0, getRequestedFor(urlPathEqualTo("/v1/statistics/signingRecords")));
    }
}
