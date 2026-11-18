package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.SettingsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SettingsProviderConcurrencyTest {
    private static final String PROVIDER = "/v1/settings/authentication/oauth2Providers/example";
    private static final String JWK_SET_URL = "\"jwkSetUrl\":\"https://id.example.test/jwks\"";
    private static final String STORED = "{\"issuerUrl\":\"https://id.example.test\",\"clientId\":\"ilm\","
            + JWK_SET_URL + "}";
    private static final String CHANGED_BY_OTHERS = "{\"issuerUrl\":\"https://id.example.test\",\"clientId\":\"ilm-2\","
            + JWK_SET_URL + "}";
    private static final String PATCH = "{\"skew\":45}";
    private static final String CONCURRENT_CHANGE = "OAuth provider example was changed concurrently in ILM and was not updated. Retry the update.";
    private static final String MISSING_PROVIDER = "OAuth provider example does not exist in ILM and was not updated.";
    private SettingsTool tool;

    @BeforeEach
    void wireTool(WireMockRuntimeInfo wm) {
        tool = new SettingsTool(new SettingsServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.21.0");
    }

    @Test
    void sendsIfMatch_whenTheReadCarriedAnEntityTag() {
        // given
        var readTag = "\"tag-1\"";
        stubFor(get(urlEqualTo(PROVIDER)).willReturn(okJson(STORED).withHeader("ETag", readTag)));
        stubFor(put(urlEqualTo(PROVIDER)).willReturn(noContent()));

        // when
        String result = tool.updateOAuthProviderSettings("example", PATCH);

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER)).withHeader("If-Match", equalTo(readTag)));
    }

    @Test
    void sendsNoIfMatch_whenTheReadCarriedNoEntityTag() {
        // given — an ILM older than 2.21 answers the read without an ETag
        stubFor(get(urlEqualTo(PROVIDER)).willReturn(okJson(STORED)));
        stubFor(put(urlEqualTo(PROVIDER)).willReturn(noContent()));

        // when
        String result = tool.updateOAuthProviderSettings("example", PATCH);

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER)).withHeader("If-Match", absent()));
    }

    @Test
    void mergesIntoTheChangedProvider_whenTheFirstWriteIsRefused() {
        // given
        var staleTag = "\"tag-1\"";
        var freshTag = "\"tag-2\"";
        stubFor(get(urlEqualTo(PROVIDER))
                .inScenario("concurrent change")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(STORED).withHeader("ETag", staleTag))
                .willSetStateTo("changed"));
        stubFor(get(urlEqualTo(PROVIDER))
                .inScenario("concurrent change")
                .whenScenarioStateIs("changed")
                .willReturn(okJson(CHANGED_BY_OTHERS).withHeader("ETag", freshTag)));
        stubFor(put(urlEqualTo(PROVIDER)).withHeader("If-Match", equalTo(staleTag)).willReturn(preconditionFailed()));
        stubFor(put(urlEqualTo(PROVIDER)).withHeader("If-Match", equalTo(freshTag)).willReturn(noContent()));

        // when
        String result = tool.updateOAuthProviderSettings("example", PATCH);

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER))
                .withHeader("If-Match", equalTo(freshTag))
                .withRequestBody(equalToJson("{\"issuerUrl\":\"https://id.example.test\",\"clientId\":\"ilm-2\","
                        + "\"skew\":45," + JWK_SET_URL + "}", true, true)));
    }

    @Test
    void reportsConcurrentChange_whenTheRetriedWriteIsRefusedToo() {
        // given
        stubFor(get(urlEqualTo(PROVIDER)).willReturn(okJson(STORED).withHeader("ETag", "\"tag-1\"")));
        stubFor(put(urlEqualTo(PROVIDER)).willReturn(preconditionFailed()));

        // when
        String result = tool.updateOAuthProviderSettings("example", PATCH);

        // then
        assertThat(result).isEqualTo(CONCURRENT_CHANGE);
        verify(2, getRequestedFor(urlEqualTo(PROVIDER)));
        verify(2, putRequestedFor(urlEqualTo(PROVIDER)));
    }

    @Test
    void reportsMissingProvider_whenItWasDeletedBeforeTheRetry() {
        // given
        stubFor(get(urlEqualTo(PROVIDER))
                .inScenario("deleted")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(STORED).withHeader("ETag", "\"tag-1\""))
                .willSetStateTo("deleted"));
        stubFor(get(urlEqualTo(PROVIDER))
                .inScenario("deleted")
                .whenScenarioStateIs("deleted")
                .willReturn(aResponse().withStatus(200)));
        stubFor(put(urlEqualTo(PROVIDER)).willReturn(preconditionFailed()));

        // when
        String result = tool.updateOAuthProviderSettings("example", PATCH);

        // then
        assertThat(result).isEqualTo(MISSING_PROVIDER);
        verify(1, putRequestedFor(urlEqualTo(PROVIDER)));
    }

    @Test
    void reportsMissingProvider_whenItDoesNotExist() {
        // given — ILM answers the read of an unknown provider with 200 and no body
        stubFor(get(urlEqualTo(PROVIDER)).willReturn(aResponse().withStatus(200)));

        // when
        String result = tool.updateOAuthProviderSettings("example", PATCH);

        // then
        assertThat(result).isEqualTo(MISSING_PROVIDER);
        verify(0, putRequestedFor(urlEqualTo(PROVIDER)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResponseDefinitionBuilder preconditionFailed() {
        return aResponse()
                .withStatus(412)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"OAuth2 provider 'example' was changed since it was read.\"}");
    }
}
