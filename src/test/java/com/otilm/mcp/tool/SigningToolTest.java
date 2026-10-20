package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.PlatformVersionCheck;
import com.otilm.mcp.service.SigningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
class SigningToolTest {

    private static final String PROFILE_UUID = "38f6e529-8123-4ead-a5f1-f3e65d8c06a9";

    private SigningTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        tool = new SigningTool(new SigningServiceImpl(client, new PlatformVersionCheck(client)));
        stubIlmVersion("2.20.0");
    }

    @Test
    void profileSearchToolListsProfiles() {
        stubFor(post(urlEqualTo("/v1/signingProfiles/list")).willReturn(okJson(fixture("signing-profiles"))));

        assertThat(tool.searchSigningProfiles(null, null, null)).contains("Found 98 signing profiles");
        verify(postRequestedFor(urlEqualTo("/v1/signingProfiles/list")));
    }

    @Test
    void profileToolReadsOneProfile() {
        stubFor(get(urlEqualTo("/v1/signingProfiles/" + PROFILE_UUID))
                .willReturn(okJson(fixture("signing-profile-static"))));

        assertThat(tool.getSigningProfile(PROFILE_UUID)).contains("Signing Profile: name-16");
        verify(getRequestedFor(urlEqualTo("/v1/signingProfiles/" + PROFILE_UUID)));
    }

    @Test
    void recordSearchToolListsRecords() {
        stubFor(post(urlEqualTo("/v1/signingRecords")).willReturn(okJson(fixture("signing-records"))));

        assertThat(tool.searchSigningRecords(null, null, null)).contains("Found 147 signing records");
        verify(postRequestedFor(urlEqualTo("/v1/signingRecords")));
    }

    @Test
    void statisticsToolReadsStatistics() {
        stubFor(get(urlPathEqualTo("/v1/statistics/signingRecords")).willReturn(okJson(fixture("signing-statistics"))));

        assertThat(tool.getSigningStatistics("30d")).contains("Records Retained: 147", "Volume (30d):");
        verify(getRequestedFor(urlEqualTo("/v1/statistics/signingRecords?period=30d")));
    }

    @Test
    void tspSearchToolListsProfiles() {
        stubFor(post(urlEqualTo("/v1/tspProfiles/list")).willReturn(okJson(fixture("tsp-profiles"))));

        assertThat(tool.searchTspProfiles(null, null, null)).contains("Found 73 TSP profiles");
        verify(postRequestedFor(urlEqualTo("/v1/tspProfiles/list")));
    }
}
