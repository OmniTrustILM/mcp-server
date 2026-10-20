package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.CryptoAssetServiceImpl;
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
class CryptoAssetToolTest {

    private static final String ASSET_UUID = "82643a70-90b8-4042-a4d2-4723b68e8391";

    private CryptoAssetTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        tool = new CryptoAssetTool(new CryptoAssetServiceImpl(client, new PlatformVersionCheck(client)));
        stubIlmVersion("2.20.0");
    }

    @Test
    void statisticsToolReadsInventoryStatistics() {
        stubFor(get(urlEqualTo("/v1/statistics/cryptoAssets")).willReturn(okJson(fixture("crypto-asset-statistics"))));

        assertThat(tool.getCryptoAssetStatistics()).contains("Total Assets: 573");
        verify(getRequestedFor(urlEqualTo("/v1/statistics/cryptoAssets")));
    }

    @Test
    void searchToolSearchesInventory() {
        stubFor(post(urlEqualTo("/v1/cryptoAssets")).willReturn(okJson(fixture("crypto-assets"))));

        assertThat(tool.searchCryptoAssets(null, 5, 1)).contains("Found 573 cryptographic assets");
        verify(postRequestedFor(urlEqualTo("/v1/cryptoAssets")));
    }

    @Test
    void detailToolReadsOneAsset() {
        stubFor(get(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID)).willReturn(okJson(fixture("crypto-asset"))));

        assertThat(tool.getCryptoAsset(ASSET_UUID)).contains("Cryptographic Asset: name-3");
        verify(getRequestedFor(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID)));
    }

    @Test
    void explanationToolReadsVerdictExplanation() {
        stubFor(get(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID + "/pqcExplanation"))
                .willReturn(okJson(fixture("crypto-asset-pqc-explanation"))));

        assertThat(tool.explainCryptoAssetPqcVerdict(ASSET_UUID)).contains("PQC Verdict Explanation for " + ASSET_UUID);
        verify(getRequestedFor(urlEqualTo("/v1/cryptoAssets/" + ASSET_UUID + "/pqcExplanation")));
    }
}
