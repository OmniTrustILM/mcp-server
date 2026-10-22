package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.service.WorkflowServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class WorkflowToolTest {

    private static final String SET_FIELD = "6daef4cf-2ad8-45f8-8998-de8d3fc44fad";

    private WorkflowTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        tool = new WorkflowTool(new WorkflowServiceImpl(IlmTestSupport.client(wmInfo)));
        stubFor(get(urlEqualTo("/v1/workflows/triggers")).willReturn(okJson(fixture("workflow-triggers"))));
        stubFor(get(urlEqualTo("/v1/settings/events")).willReturn(okJson(fixture("events-settings"))));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + SET_FIELD))
                .willReturn(okJson(fixture("workflow-trigger-set-field"))));
    }

    @Test
    void readToolsDelegate() {
        assertThat(tool.listTriggers(null)).contains("Triggers (5)");
        assertThat(tool.getTrigger(SET_FIELD)).contains("Trigger: name-1");
        assertThat(tool.listEventTriggers(null)).contains("Platform event triggers");
        stubFor(get(urlPathEqualTo("/v1/resources/comments/filters/rules")).willReturn(okJson("[]")));
        assertThat(tool.listTriggerFields("comment_created")).contains("Conditions can check:\n  none\n");
    }

    @Test
    void createTriggerDelegates() {
        assertThat(tool.createTrigger("t", "certificate_expiring", null, null, null, null, null))
                .isEqualTo("A trigger needs something to do: setFields, notificationProfileUuid, or ignore=true.");
    }

    @Test
    void createTriggerAppearsAtReadWrite() {
        assertThat(names(AccessLevel.READ_WRITE)).contains("create_trigger").doesNotContain("delete_trigger");
    }

    @Test
    void changeToolsDelegate() {
        stubFor(patch(urlEqualTo("/v1/settings/events")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/workflows/triggers/" + SET_FIELD)).willReturn(noContent()));

        assertThat(tool.attachTrigger(SET_FIELD)).startsWith("Attached name-1");
        assertThat(tool.detachTrigger(SET_FIELD)).contains("is not attached");
        assertThat(tool.deleteTrigger(SET_FIELD)).isEqualTo("Deleted trigger name-1 [" + SET_FIELD + "].");
    }

    @Test
    void attachAndDetachNeedReadWriteAndDeleteNeedsFull() {
        assertThat(names(AccessLevel.READ_WRITE))
                .contains("attach_trigger", "detach_trigger")
                .doesNotContain("delete_trigger");
        assertThat(names(AccessLevel.FULL)).hasSize(8).contains("delete_trigger");
    }

    @Test
    void readToolsAppearAtReadOnly() {
        assertThat(names(AccessLevel.READ_ONLY))
                .containsExactlyInAnyOrder("list_triggers", "get_trigger", "list_event_triggers",
                        "list_trigger_fields");
    }

    private static List<String> names(AccessLevel level) {
        return ToolNames.allowed(level, new WorkflowTool(null));
    }
}
