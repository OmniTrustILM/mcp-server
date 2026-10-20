package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.CbomServiceImpl;
import com.otilm.mcp.service.PlatformVersionCheck;
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
class CbomToolTest {

    private static final String CBOM_UUID = "fb2bce9f-99c9-4a71-a6cd-6e4c1a10d843";

    private CbomTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        tool = new CbomTool(new CbomServiceImpl(client, new PlatformVersionCheck(client)));
    }

    @Test
    void searchToolListsCboms() {
        stubFor(post(urlEqualTo("/v1/cboms")).willReturn(okJson(fixture("cboms"))));

        assertThat(tool.searchCboms(null, 5, 1)).contains("Found 71 CBOMs");
        verify(postRequestedFor(urlEqualTo("/v1/cboms")));
    }

    @Test
    void detailToolReadsOneCbom() {
        stubFor(get(urlEqualTo("/v1/cboms/" + CBOM_UUID)).willReturn(okJson(fixture("cbom"))));

        assertThat(tool.getCbom(CBOM_UUID)).contains("CBOM: ");
        verify(getRequestedFor(urlEqualTo("/v1/cboms/" + CBOM_UUID)));
    }

    @Test
    void versionsToolListsVersions() {
        stubFor(get(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/versions")).willReturn(okJson(fixture("cbom-versions"))));

        assertThat(tool.getCbomVersions(CBOM_UUID)).contains("CBOM Versions for " + CBOM_UUID);
        verify(getRequestedFor(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/versions")));
    }

    @Test
    void assetsToolListsContributedAssets() {
        stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/assets")).willReturn(okJson(fixture("cbom-assets"))));

        assertThat(tool.searchCbomAssets(CBOM_UUID, null, 5, 1)).contains("Found 61 cryptographic assets");
        verify(postRequestedFor(urlEqualTo("/v1/cboms/" + CBOM_UUID + "/assets")));
    }
}
