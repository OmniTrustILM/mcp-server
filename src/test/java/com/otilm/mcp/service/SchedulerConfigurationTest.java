package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.SchedulerTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SchedulerConfigurationTest {
    private static final String UUID = "613094ba-8f40-4c51-ab36-887a46a32475";
    private SchedulerTool service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new SchedulerTool(new SchedulerServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build())));
    }

    @Test
    void readsActualJobsDetailAndHistoryWithoutSerializedJobPayload() {
        stubFor(get(urlEqualTo("/v1/scheduler/jobs?itemsPerPage=10&pageNumber=1")).willReturn(okJson("""
                {"scheduledJobs":[{"uuid":"613094ba-8f40-4c51-ab36-887a46a32475","jobName":"Inventory",
                "jobType":"discovery","cronExpression":"0 0 * * * ?","enabled":true}],"totalItems":1}
                """)));
        stubFor(get(urlEqualTo("/v1/scheduler/jobs/" + UUID)).willReturn(okJson("""
                {"uuid":"613094ba-8f40-4c51-ab36-887a46a32475","jobName":"Inventory","enabled":true,
                "objectData":{"password":"PROTECTED_SENTINEL"},"lastSkipReason":"PROTECTED_SENTINEL"}
                """)));
        stubFor(get(urlEqualTo("/v1/scheduler/jobs/" + UUID + "/history?itemsPerPage=20&pageNumber=2"))
                .willReturn(okJson("""
                        {"scheduledJobHistory":[{"jobUuid":"613094ba-8f40-4c51-ab36-887a46a32475",
                        "status":"success","startTime":"2026-10-23T12:00:00Z","resultMessage":"PROTECTED_SENTINEL"}]}
                        """)));
        assertThat(service.listScheduledJobs(null, null)).contains("Inventory", UUID, "cronExpression", "totalItems");
        assertThat(service.getScheduledJob(UUID))
                .contains("Inventory", UUID)
                .doesNotContain("PROTECTED_SENTINEL", "objectData");
        assertThat(service.getScheduledJobHistory(UUID, 20, 2))
                .contains("success", UUID)
                .doesNotContain("PROTECTED_SENTINEL");
    }

    @Test
    void updatesOnlyCronAndUsesActualEnableDisableDeleteRoutes() {
        stubFor(put(urlEqualTo("/v1/scheduler/jobs/" + UUID)).willReturn(okJson("{\"uuid\":\"" + UUID + "\"}")));
        stubFor(patch(urlEqualTo("/v1/scheduler/jobs/" + UUID + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/scheduler/jobs/" + UUID + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/scheduler/jobs/" + UUID)).willReturn(noContent()));
        assertThat(service.updateScheduledJob(UUID, "0 0 3 * * ?")).contains(UUID);
        assertThat(service.enableScheduledJob(UUID)).contains(UUID);
        assertThat(service.disableScheduledJob(UUID)).contains(UUID);
        assertThat(service.deleteScheduledJob(UUID)).contains(UUID);
        verify(putRequestedFor(urlEqualTo("/v1/scheduler/jobs/" + UUID))
                .withRequestBody(equalToJson("{\"cronExpression\":\"0 0 3 * * ?\"}")));
        verify(patchRequestedFor(urlEqualTo("/v1/scheduler/jobs/" + UUID + "/enable")));
        verify(patchRequestedFor(urlEqualTo("/v1/scheduler/jobs/" + UUID + "/disable")));
        verify(deleteRequestedFor(urlEqualTo("/v1/scheduler/jobs/" + UUID)));
    }

    @Test
    void acceptsQuartzOverflowingMonthRangesAndOptionalYear() {
        stubFor(put(urlEqualTo("/v1/scheduler/jobs/" + UUID)).willReturn(okJson("{}")));
        assertThat(service.updateScheduledJob(UUID, "0 0 3 ? NOV-FEB MON 2027")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/scheduler/jobs/" + UUID))
                .withRequestBody(equalToJson("{\"cronExpression\":\"0 0 3 ? NOV-FEB MON 2027\"}")));
    }

    @Test
    void refusesInvalidQuartzDayAndYearBeforeMutation() {
        for (String cron : new String[]{"0 0 3 * * *", "0 0 3 ? * 0", "0 0 3 ? * MON bad"}) {
            assertThat(service.updateScheduledJob(UUID, cron))
                    .as("Quartz expression %s", cron)
                    .containsIgnoringCase("invalid");
        }
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void refusesInvalidUuidCronAndPaginationBeforeAnyRequest() {
        assertThat(service.updateScheduledJob(UUID, "invalid")).containsIgnoringCase("cron");
        assertThat(service.enableScheduledJob("PROTECTED_SENTINEL")).doesNotContain("PROTECTED_SENTINEL");
        assertThat(service.listScheduledJobs(1001, 0)).containsIgnoringCase("page");
        verify(0, anyRequestedFor(anyUrl()));
    }
}
