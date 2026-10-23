package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AutomationMaintenanceTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class ScopedWorkflowTest {
    private static final String O = "11111111-1111-4111-8111-111111111111";
    private static final String T = "22222222-2222-4222-8222-222222222222";
    private static final String U = "33333333-3333-4333-8333-333333333333";
    private AutomationMaintenanceTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new AutomationMaintenanceTool(new AutomationMaintenanceServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                IlmTestSupport.client(wm), new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(post(urlEqualTo("/v1/workflows/events")).willReturn(created()));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(okJson("{\"uuid\":\"" + T
                        + "\",\"resource\":\"certificates\",\"event\":\"certificate_discovered\"}")));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + U))
                .willReturn(okJson("{\"uuid\":\"" + U
                        + "\",\"resource\":\"certificates\",\"event\":\"certificate_discovered\"}")));
    }

    @Test
    void discoveryCertificateClassificationRetainsOthersAndReportsIdentityRebinding() {
        stubFor(get(urlEqualTo("/v1/workflows/events/discoveries/" + O))
                .willReturn(okJson("{\"certificate_discovered\":[\"" + U + "\"]}")));
        assertThat(tool.attachScopedTrigger("discoveries", O, "certificate_discovered", T, null))
                .contains(T, U, "execution identity", "every");
        verify(postRequestedFor(urlEqualTo("/v1/workflows/events"))
                .withRequestBody(equalToJson("{\"resource\":\"discoveries\",\"objectUuid\":\"" + O
                        + "\",\"event\":\"certificate_discovered\",\"triggerUuids\":[\"" + U + "\",\"" + T + "\"]}")));
    }

    @Test
    void reorderRequiresSameMembershipAndDetachPreservesOtherEntries() {
        stubFor(get(urlEqualTo("/v1/workflows/events/discoveries/" + O))
                .willReturn(okJson("{\"certificate_discovered\":[\"" + U + "\",\"" + T + "\"]}")));
        assertThat(
                tool.reorderScopedTriggers("discoveries", O, "certificate_discovered", "[\"" + T + "\",\"" + U + "\"]"))
                .contains("execution identity");
        assertThat(tool.detachScopedTrigger("discoveries", O, "certificate_discovered", T)).contains(U);
        verify(postRequestedFor(urlEqualTo("/v1/workflows/events"))
                .withRequestBody(matchingJsonPath("$.triggerUuids", equalToJson("[\"" + U + "\"]"))));
        assertThat(tool.reorderScopedTriggers("discoveries", O, "certificate_discovered", "[]")).contains("same");
        verify(2, postRequestedFor(urlEqualTo("/v1/workflows/events")));
    }

    @Test
    void finishedRunAndCertificateEventsHaveDistinctCompatibility() {
        assertThat(tool.attachScopedTrigger("raProfiles", O, "discovery_finished", T, null)).contains("event");
        assertThat(tool.attachScopedTrigger("discoveries", O, "certificate_expiring", T, null)).contains("event");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void scopedReadsAndHistoryExposeActualSummaryAndDecisionFields() {
        stubFor(get(urlEqualTo("/v1/workflows/events/groups/" + O))
                .willReturn(okJson("{\"certificate_expiring\":[\"" + T + "\"]}")));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/history/" + O))
                .willReturn(okJson("{\"associationResource\":\"groups\",\"associationObjectUuid\":\"" + O
                        + "\",\"objectsEvaluated\":4,\"objectsMatched\":2,\"objectsIgnored\":1,\"objects\":[]}")));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T + "/history/" + O))
                .willReturn(okJson("[{\"triggerUuid\":\"" + T + "\",\"objectUuid\":\"" + O
                        + "\",\"conditionsMatched\":true,\"actionsPerformed\":false}]")));
        assertThat(tool.getScopedTriggers("groups", O)).contains(T, "certificate_expiring");
        assertThat(tool.getScopedTriggerHistory(O, null)).contains("objectsEvaluated", "4", "objectsMatched", "2");
        assertThat(tool.getScopedTriggerHistory(O, T))
                .contains("conditionsMatched", "true", "actionsPerformed", "false");
    }

    @Test
    void ignoreTriggersMustPrecedeNormalTriggersRatherThanBeingSilentlyReordered() {
        stubFor(get(urlEqualTo("/v1/workflows/events/discoveries/" + O))
                .willReturn(okJson("{\"certificate_discovered\":[\"" + U + "\"]}")));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(okJson("{\"uuid\":\"" + T
                        + "\",\"resource\":\"certificates\",\"event\":\"certificate_discovered\",\"ignoreTrigger\":true}")));
        assertThat(tool.attachScopedTrigger("discoveries", O, "certificate_discovered", T, null))
                .contains("Ignore", "before");
        verify(0, postRequestedFor(urlEqualTo("/v1/workflows/events")));
        assertThat(tool.attachScopedTrigger("discoveries", O, "certificate_discovered", T, 1))
                .contains("execution identity");
        verify(postRequestedFor(urlEqualTo("/v1/workflows/events"))
                .withRequestBody(matchingJsonPath("$.triggerUuids", equalToJson("[\"" + T + "\",\"" + U + "\"]"))));
    }

}
