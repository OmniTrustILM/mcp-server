package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.service.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class NotificationToolTest {

    private static final String GROUP_PROFILE = "00000000-0000-4000-8000-000000000531";

    private NotificationTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        tool = new NotificationTool(new NotificationServiceImpl(IlmTestSupport.client(wmInfo)));
    }

    @Test
    void readToolsDelegate() {
        stubFor(get(urlEqualTo("/v1/notificationInstances")).willReturn(okJson(fixture("notification-instances"))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles?itemsPerPage=1000&pageNumber=1"))
                .willReturn(okJson(fixture("notification-profiles"))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + GROUP_PROFILE))
                .willReturn(okJson(fixture("notification-profile"))));

        assertThat(tool.listNotificationInstances()).contains("Notification instances (2)");
        assertThat(tool.listNotificationProfiles()).contains("Notification profiles (2)");
        assertThat(tool.getNotificationProfile(GROUP_PROFILE)).contains("Notification profile: name-5");
    }

    @Test
    void createToolsDelegate() {
        assertThat(tool.createNotificationInstance(" ", null, null, null, null, null))
                .isEqualTo("A notification instance needs a name.");
        assertThat(tool.createNotificationProfile(" ", null, null, null, null, null, null))
                .isEqualTo("A notification profile needs a name.");
    }

    @Test
    void createToolsAppearAtReadWrite() {
        assertThat(ToolNames.allowed(AccessLevel.READ_WRITE, new NotificationTool(null)))
                .contains("create_notification_instance", "create_notification_profile");
        assertThat(ToolNames.allowed(AccessLevel.READ_ONLY, new NotificationTool(null)))
                .doesNotContain("create_notification_instance", "create_notification_profile");
    }

    @Test
    void deleteToolsDelegate() {
        stubFor(get(urlEqualTo("/v1/notificationInstances")).willReturn(okJson("[]")));

        assertThat(tool.deleteNotificationInstance(GROUP_PROFILE)).startsWith("No notification instance [");
        assertThat(tool.deleteNotificationProfile(GROUP_PROFILE))
                .startsWith("Error deleting notification profile: ILM answered 404");
    }

    @Test
    void deletesNeedFull() {
        assertThat(ToolNames.allowed(AccessLevel.READ_WRITE, new NotificationTool(null)))
                .doesNotContain("delete_notification_instance", "delete_notification_profile");
        assertThat(ToolNames.allowed(AccessLevel.FULL, new NotificationTool(null)))
                .hasSize(8)
                .contains("delete_notification_instance", "delete_notification_profile");
    }

    @Test
    void readToolsAppearAtReadOnly() {
        assertThat(ToolNames.allowed(AccessLevel.READ_ONLY, new NotificationTool(null)))
                .containsExactlyInAnyOrder("list_notification_instances", "list_notification_profiles",
                        "get_notification_profile", "get_notification_connector_settings");
    }
}
