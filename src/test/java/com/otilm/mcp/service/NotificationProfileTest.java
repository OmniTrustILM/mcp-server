package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class NotificationProfileTest {

    private static final String PROFILES = "/v1/notificationProfiles";
    private static final String GROUP_PROFILE = "00000000-0000-4000-8000-000000000531";
    private static final String OWNER_PROFILE = "00000000-0000-4000-8000-000000000532";
    private static final String GROUP = "00000000-0000-4000-8000-000000000521";
    private static final String INSTANCE = "00000000-0000-4000-8000-000000000511";
    private static final String OWNER_DETAIL = """
            {"uuid":"00000000-0000-4000-8000-000000000532","name":"name-6","version":1,"recipientType":"owner",
             "recipients":[],"internalNotification":true,"frequency":"PT24H","eventDataCategories":[]}
            """;

    private NotificationService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new NotificationServiceImpl(IlmTestSupport.client(wmInfo));
    }

    @Test
    void profileWithoutFrequencyGetsOneDay() {
        stubFor(post(urlEqualTo(PROFILES)).willReturn(created(fixture("notification-profile"))));
        stubFor(get(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)).willReturn(okJson(fixture("notification-profile"))));

        String result = service
                .createNotificationProfile(
                        new NotificationProfileInput("name-5", "group", GROUP, INSTANCE, null, null, 5));

        verify(postRequestedFor(urlEqualTo(PROFILES)).withRequestBody(equalToJson("""
                {"name":"name-5","recipientType":"group","recipientUuids":["%s"],
                 "notificationInstanceUuid":"%s","internalNotification":false,"frequency":"PT24H","repetitions":5}
                """.formatted(GROUP, INSTANCE), false, true)));
        verify(getRequestedFor(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)));
        assertThat(result)
                .startsWith("Created notification profile name-5 [" + GROUP_PROFILE + "].")
                .contains("Recipients: groups name-7 [" + GROUP + "]",
                        "Sends through: notification instance name-1 [" + INSTANCE + "]",
                        "frequency was not given, so it is one day: certificate_expiring sends at most one notification "
                                + "a day for the same certificate; other events ignore frequency and repetitions.");
    }

    @Test
    void createdProfileIsReportedWhenTheReReadFails() {
        stubFor(post(urlEqualTo(PROFILES)).willReturn(created(fixture("notification-profile"))));
        stubFor(get(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)).willReturn(aResponse().withStatus(503)));

        assertThat(service
                .createNotificationProfile(
                        new NotificationProfileInput("name-5", "group", GROUP, INSTANCE, null, null, 5)))
                .startsWith("Created notification profile name-5 [" + GROUP_PROFILE + "].")
                .contains("Recipients: groups name-7 [" + GROUP + "]");
    }

    @Test
    void ownerProfileInAppOnly() {
        stubFor(post(urlEqualTo(PROFILES)).willReturn(created(OWNER_DETAIL)));
        stubFor(get(urlEqualTo(PROFILES + "/" + OWNER_PROFILE)).willReturn(okJson(OWNER_DETAIL)));

        String result = service
                .createNotificationProfile(
                        new NotificationProfileInput("name-6", "OWNER", null, null, true, "P2D", null));

        verify(postRequestedFor(urlEqualTo(PROFILES)).withRequestBody(equalToJson("""
                {"name":"name-6","recipientType":"owner","recipientUuids":[],"internalNotification":true,
                 "frequency":"PT48H"}
                """, false, true)));
        assertThat(result)
                .contains("Recipients: the object's owner", "In-app notification: yes")
                .doesNotContain("frequency was not given");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "\" \"|group|" + GROUP + "|" + INSTANCE + "|||A notification profile needs a name.",
            "p|team|" + GROUP + "|" + INSTANCE
                    + "|||Unknown recipient type 'team'. Valid types: none, default, user, group, role, owner, object.",
            "p|group||" + INSTANCE
                    + "|||Recipient type group needs recipients: comma-separated user, group or role UUIDs.",
            "p|owner|" + GROUP + "|" + INSTANCE + "|||Recipient type owner takes no recipients.",
            "p|group|not-a-uuid|" + INSTANCE + "|||'not-a-uuid' is not a UUID.",
            "p|group|" + GROUP + "|1-1-1-1-1|||'1-1-1-1-1' is not a UUID.",
            "p|default||" + INSTANCE + "|true||In-app notifications cannot go to recipient type default.",
            "p|owner|||||A notification profile needs a notificationInstanceUuid, internalNotification=true, or both.",
            "p|group|" + GROUP + "|" + INSTANCE + "||0|repetitions must be at least 1."})
    void profileInputIsChecked(String name, String type, String recipients, String instance, Boolean inApp,
            Integer repetitions, String message) {
        assertThat(service
                .createNotificationProfile(
                        new NotificationProfileInput(name, type, recipients, instance, inApp, null, repetitions)))
                .isEqualTo(message);
        verify(0, postRequestedFor(urlEqualTo(PROFILES)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT30M", "P0D", "-P1D", "daily"})
    void profileFrequencyMustBeDaysAndHours(String frequency) {
        assertThat(service
                .createNotificationProfile(
                        new NotificationProfileInput("p", "group", GROUP, INSTANCE, null, frequency, null)))
                .isEqualTo("frequency must be a positive ISO-8601 duration of days and hours, for example P1D or "
                        + "PT12H.");
        verify(0, postRequestedFor(urlEqualTo(PROFILES)));
    }

    @Test
    void ilmRefusalComesBackAsText() {
        stubFor(post(urlEqualTo(PROFILES))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Notification profile with name p already exists.\"}")));

        assertThat(service
                .createNotificationProfile(
                        new NotificationProfileInput("p", "group", GROUP, INSTANCE, null, null, null)))
                .startsWith("Error creating notification profile: ILM answered 409");
    }

    @Test
    void deleteNotificationProfileDeletesIt() {
        stubFor(get(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)).willReturn(okJson(fixture("notification-profile"))));
        stubFor(delete(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)).willReturn(noContent()));

        assertThat(service.deleteNotificationProfile(GROUP_PROFILE))
                .isEqualTo("Deleted notification profile name-5 [" + GROUP_PROFILE + "].");
        verify(deleteRequestedFor(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)));
    }

    @Test
    void deleteOfAUsedProfileSaysToDeleteTheTriggersFirst() {
        stubFor(get(urlEqualTo(PROFILES + "/" + GROUP_PROFILE)).willReturn(okJson(fixture("notification-profile"))));
        stubFor(delete(urlEqualTo(PROFILES + "/" + GROUP_PROFILE))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"message\":\"Cannot delete notification profile. 1 execution(s) are referencing this "
                                        + "notification profile: expiring-web - notify\"}")));

        assertThat(service.deleteNotificationProfile(GROUP_PROFILE))
                .contains("1 execution(s) are referencing this notification profile: expiring-web - notify",
                        "Delete the triggers that send through it first with delete_trigger; their notify parts are "
                                + "named \"<trigger> - notify\".");
    }

    private static ResponseDefinitionBuilder created(String body) {
        return aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(body);
    }
}
