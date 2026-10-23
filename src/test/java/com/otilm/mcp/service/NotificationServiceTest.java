package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class NotificationServiceTest {

    static final String INSTANCES = "/v1/notificationInstances";
    static final String PROFILES = "/v1/notificationProfiles?itemsPerPage=1000&pageNumber=1";
    static final String GROUP_PROFILE = "00000000-0000-4000-8000-000000000531";

    private NotificationService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new NotificationServiceImpl(IlmTestSupport.client(wmInfo));
    }

    @Test
    void listNotificationInstancesShowsConnectorAndKind() {
        stubFor(get(urlEqualTo(INSTANCES)).willReturn(okJson(fixture("notification-instances"))));

        assertThat(service.listNotificationInstances())
                .contains("Notification instances (2)", "- name-1 [00000000-0000-4000-8000-000000000511]",
                        "Connector: name-2 [00000000-0000-4000-8000-000000000501]", "Kind: EMAIL",
                        "Description: description-1", "- name-3 [00000000-0000-4000-8000-000000000512]",
                        "Kind: WEBHOOK");
    }

    @Test
    void listNotificationProfilesShowsRecipientsAndChannel() {
        stubFor(get(urlEqualTo(PROFILES)).willReturn(okJson(fixture("notification-profiles"))));

        assertThat(service.listNotificationProfiles())
                .contains("Notification profiles (2)", "- name-5 [00000000-0000-4000-8000-000000000531]",
                        "Recipients: groups (1)", "Sends through: 00000000-0000-4000-8000-000000000511",
                        "- name-6 [00000000-0000-4000-8000-000000000532]", "Recipients: the object's owner",
                        "In-app: yes")
                .doesNotContain("Showing the first");
    }

    @Test
    void listNotificationProfilesSaysWhenItShowsAPage() {
        stubFor(get(urlEqualTo(PROFILES))
                .willReturn(
                        okJson(fixture("notification-profiles").replace("\"totalItems\": 2", "\"totalItems\": 1500"))));

        assertThat(service.listNotificationProfiles()).contains("Showing the first 2 of 1500.");
    }

    @Test
    void getNotificationProfileExplainsItInPlainWords() {
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + GROUP_PROFILE))
                .willReturn(okJson(fixture("notification-profile"))));

        assertThat(service.getNotificationProfile(GROUP_PROFILE))
                .contains("Notification profile: name-5 [00000000-0000-4000-8000-000000000531] (version 2)",
                        "Description: description-5",
                        "Recipients: groups name-7 [00000000-0000-4000-8000-000000000521]",
                        "Sends through: notification instance name-1 [00000000-0000-4000-8000-000000000511]",
                        "In-app notification: no",
                        "Frequency: at most once every 1 day per object (certificate_expiring only)",
                        "Repetitions: at most 5 per object (certificate_expiring only)");
    }

    @Test
    void getNotificationProfileOfAnUnknownUuid() {
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + GROUP_PROFILE)).willReturn(aResponse().withStatus(404)));

        assertThat(service.getNotificationProfile(GROUP_PROFILE))
                .startsWith("Error retrieving notification profile: ILM answered 404");
    }
}
