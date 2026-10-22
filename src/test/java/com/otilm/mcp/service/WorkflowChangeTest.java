package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.mcp.IlmTestSupport;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.service.WorkflowCreateTest.ACTION;
import static com.otilm.mcp.service.WorkflowCreateTest.CONDITION;
import static com.otilm.mcp.service.WorkflowCreateTest.RULE;
import static com.otilm.mcp.service.WorkflowCreateTest.SET;
import static com.otilm.mcp.service.WorkflowCreateTest.TRIGGER;
import static com.otilm.mcp.service.WorkflowCreateTest.unprocessable;
import static com.otilm.mcp.service.WorkflowServiceTest.stubEventsSettings;
import static com.otilm.mcp.service.WorkflowServiceTest.stubTrigger;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class WorkflowChangeTest {

    private static final String SET_FIELD = "6daef4cf-2ad8-45f8-8998-de8d3fc44fad";
    private static final String STATUS_TRIGGER = "cb36ddc3-bb10-4bc0-9624-92442f903dc2";
    private static final String JOB_TRIGGER = "e4004ce0-2b01-4603-a394-8347314aefe0";
    private static final String MANUAL = "ba3f51fb-f41d-4027-88f4-c2ca17102eec";
    private static final String EVENTS = "/v1/settings/events";
    private static final String WORKFLOW_OBJECTS = "/v1/workflows/.*";

    private WorkflowService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new WorkflowServiceImpl(IlmTestSupport.client(wmInfo));
    }

    @Test
    void attachListsTheTriggerOnItsEvent() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubEventsSettings();
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(noContent()));

        String result = service.attachTrigger(SET_FIELD);

        verify(patchRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(equalToJson(
                        "{\"event\":\"certificate_discovered\",\"triggerUuids\":[\"" + SET_FIELD + "\"]}")));
        assertThat(result)
                .contains(
                        "Attached name-1 [" + SET_FIELD
                                + "] to certificate_discovered platform-wide (position 1 of 1).",
                        WorkflowText.runAsNote(ResourceEvent.CERTIFICATE_DISCOVERED));
    }

    @Test
    void attachingTwiceChangesNothing() {
        stubEventTrigger(STATUS_TRIGGER, "name-47", "certificate_status_changed");
        stubEventsSettings();

        assertThat(service.attachTrigger(STATUS_TRIGGER))
                .isEqualTo("name-47 [" + STATUS_TRIGGER
                        + "] is already attached to certificate_status_changed platform-wide; nothing changed.");
        verify(0, patchRequestedFor(urlEqualTo(EVENTS)));
    }

    @Test
    void detachRemovesOnlyThisTriggerInOrder() {
        stubEventTrigger(STATUS_TRIGGER, "name-47", "certificate_status_changed");
        stubEventsSettings();
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(noContent()));

        String result = service.detachTrigger(STATUS_TRIGGER);

        verify(patchRequestedFor(urlEqualTo(EVENTS)).withRequestBody(equalToJson("""
                {"event":"certificate_status_changed","triggerUuids":["679fe74b-b728-4b68-87e9-28e742914782",
                 "3407aabf-7497-4686-9152-58fed8056fc9","47b6e99e-34b4-4876-b8db-f15f915013f1",
                 "87ab1393-1b84-42ba-b386-4f6f18aa07bb","d6fe91c3-720b-477e-9e2a-c5c8dba912d7"]}
                """)));
        assertThat(result)
                .contains(
                        "Detached name-47 [" + STATUS_TRIGGER
                                + "] from certificate_status_changed; 5 platform triggers remain.",
                        WorkflowText.runAsNote(ResourceEvent.CERTIFICATE_STATUS_CHANGED));
    }

    @Test
    void detachingTheLastTriggerNeedsNoRunAsNote() {
        stubEventTrigger(JOB_TRIGGER, "name-49", "scheduled_job_finished");
        stubEventsSettings();
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(noContent()));

        assertThat(service.detachTrigger(JOB_TRIGGER))
                .isEqualTo("Detached name-49 [" + JOB_TRIGGER
                        + "] from scheduled_job_finished; 0 platform triggers remain.\n");
    }

    @Test
    void detachOfAnUnattachedTriggerChangesNothing() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubEventsSettings();

        assertThat(service.detachTrigger(SET_FIELD))
                .isEqualTo("name-1 [" + SET_FIELD
                        + "] is not attached to certificate_discovered platform-wide; nothing changed.");
        verify(0, patchRequestedFor(urlEqualTo(EVENTS)));
    }

    @Test
    void attachRefusesATriggerWithoutEvent() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + MANUAL)).willReturn(okJson("""
                {"uuid":"%s","name":"manual-1","resource":"certificates","ignoreTrigger":false,
                 "rules":[],"actions":[]}
                """.formatted(MANUAL))));

        assertThat(service.attachTrigger(MANUAL))
                .isEqualTo("Trigger manual-1 [" + MANUAL + "] has no event, so it cannot be attached platform-wide.");
        assertThat(service.detachTrigger(MANUAL))
                .isEqualTo("Trigger manual-1 [" + MANUAL + "] has no event, so it cannot be attached platform-wide.");
    }

    @Test
    void deleteRemovesOnlyTheTriggerWhenNoPartIsNamedAfterIt() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubFor(delete(urlMatching(WORKFLOW_OBJECTS)).willReturn(noContent()));

        String result = service.deleteTrigger(SET_FIELD);

        assertThat(findAll(deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS))).stream().map(LoggedRequest::getUrl))
                .containsExactly("/v1/workflows/triggers/" + SET_FIELD);
        assertThat(result).isEqualTo("Deleted trigger name-1 [" + SET_FIELD + "].");
    }

    @Test
    void deleteRemovesPartsCreatedByCreateTrigger() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + TRIGGER))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","name":"expiring-web","type":"event","resource":"certificates","event":"certificate_expiring",
                                 "ignoreTrigger":false,
                                 "rules":[{"uuid":"%s","name":"expiring-web","conditions":[{"uuid":"%s","name":"expiring-web","items":[]}]}],
                                 "actions":[{"uuid":"%s","name":"expiring-web","executions":[{"uuid":"%s","name":"expiring-web - set fields","items":[]}]}]}
                                """
                                .formatted(TRIGGER, RULE, CONDITION, ACTION, SET))));
        stubFor(delete(urlMatching(WORKFLOW_OBJECTS)).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/workflows/conditions/" + CONDITION))
                .willReturn(unprocessable("Condition is in use.")));

        String result = service.deleteTrigger(TRIGGER);

        assertThat(findAll(deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS))).stream().map(LoggedRequest::getUrl))
                .containsExactly("/v1/workflows/triggers/" + TRIGGER, "/v1/workflows/rules/" + RULE,
                        "/v1/workflows/conditions/" + CONDITION, "/v1/workflows/actions/" + ACTION,
                        "/v1/workflows/executions/" + SET);
        assertThat(result)
                .contains("Deleted trigger expiring-web [" + TRIGGER + "].",
                        "Also deleted: rule expiring-web [" + RULE + "], action expiring-web [" + ACTION
                                + "], execution expiring-web - set fields [" + SET + "]",
                        "Kept:", "- condition expiring-web [" + CONDITION + "]: ", "Condition is in use.");
    }

    @Test
    void deleteOfAnAttachedTriggerChangesNothing() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubFor(delete(urlEqualTo("/v1/workflows/triggers/" + SET_FIELD))
                .willReturn(
                        unprocessable("Cannot delete trigger. It has 1 event association(s): Certificate discovered")));

        String result = service.deleteTrigger(SET_FIELD);

        verify(1, deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .contains("Cannot delete trigger. It has 1 event association(s)",
                        "Nothing else was deleted. If the trigger is still attached, detach it first");
    }

    @Test
    void concurrentAttachmentsDoNotInterleave() throws Exception {
        String first = "00000000-0000-4000-8000-000000000011";
        String second = "00000000-0000-4000-8000-000000000012";
        stubEventTrigger(first, "first", "certificate_expiring");
        stubEventTrigger(second, "second", "certificate_expiring");
        stubEventsSettings();
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(noContent().withFixedDelay(300)));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> one = pool.submit(() -> {
                start.await();
                return service.attachTrigger(first);
            });
            Future<String> two = pool.submit(() -> {
                start.await();
                return service.attachTrigger(second);
            });
            start.countDown();
            assertThat(one.get(10, TimeUnit.SECONDS)).startsWith("Attached ");
            assertThat(two.get(10, TimeUnit.SECONDS)).startsWith("Attached ");
        }

        assertThat(findAll(anyRequestedFor(urlEqualTo(EVENTS))).stream().map(request -> request.getMethod().getName()))
                .containsExactly("GET", "PATCH", "GET", "PATCH");
    }

    private static void stubEventTrigger(String uuid, String name, String event) {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + uuid)).willReturn(okJson("""
                {"uuid":"%s","name":"%s","type":"event","resource":"%s",
                 "event":"%s","ignoreTrigger":false,"rules":[],"actions":[]}
                """.formatted(uuid, name, "scheduled_job_finished".equals(event) ? "jobs" : "certificates", event))));
    }
}
