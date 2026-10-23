package com.otilm.mcp.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class WorkflowServiceTest {

    private static final String SET_FIELD = "6daef4cf-2ad8-45f8-8998-de8d3fc44fad";
    private static final String NOTIFY = "7299c805-5b8b-4a06-a504-f7302b31cf85";
    private static final String IGNORE = "ba3f51fb-f41d-4027-88f4-c2ca17102eec";

    private WorkflowService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new WorkflowServiceImpl(IlmTestSupport.client(wmInfo));
    }

    @Test
    void getTriggerExplainsASetFieldTrigger() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubEventsSettings();

        assertThat(service.getTrigger(SET_FIELD))
                .contains("Trigger: name-1 [" + SET_FIELD + "]", "Event: certificate_discovered (certificates)",
                        "When (all must hold):", "  - property ISSUERDN NOT_CONTAINS value-4",
                        "  - set property GROUP_NAME to 5a58cdc9-604d-4325-bbca-edaf335773cf",
                        "  - set custom attribute-8|TEXT to value-9",
                        "Platform attachment: not attached to certificate_discovered platform-wide",
                        WorkflowText.PLATFORM_NOTE);
    }

    @Test
    void getTriggerExplainsANotificationTrigger() {
        stubTrigger(NOTIFY, "workflow-trigger-notification");
        stubEventsSettings();

        assertThat(service.getTrigger(NOTIFY))
                .contains("  - property COMMENT_RESOLVED_AT NOT_EMPTY\n",
                        "  - notify through profile notificationProfileName-22 [cd9a1f7f-fe48-4f3c-a890-e23f795f4ecc]");
    }

    @Test
    void getTriggerExplainsAnIgnoreTrigger() {
        stubTrigger(IGNORE, "workflow-trigger-ignore");
        stubEventsSettings();

        assertThat(service.getTrigger(IGNORE))
                .contains("Then: skip the certificate, so discovery does not import it (ignore trigger)",
                        "  - meta attribute-29|STRING CONTAINS value-30",
                        "  - property RA_PROFILE_NAME EQUALS value-33");
    }

    @Test
    void getTriggerWithoutEventSaysSo() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + IGNORE)).willReturn(okJson("""
                {"uuid": "%s", "name": "manual-1", "resource": "certificates",
                 "ignoreTrigger": false, "rules": [], "actions": []}
                """.formatted(IGNORE))));
        stubEventsSettings();

        assertThat(service.getTrigger(IGNORE))
                .contains("Event: none (certificates)", "When: every certificates object of this event",
                        "Platform attachment: none (no event)");
    }

    @Test
    void getTriggerStillAnswersWhenEventSettingsCannotBeRead() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubFor(get(urlEqualTo("/v1/settings/events")).willReturn(aResponse().withStatus(403)));

        assertThat(service.getTrigger(SET_FIELD))
                .contains("Trigger: name-1 [" + SET_FIELD + "]",
                        "Platform attachment: unknown (the platform event settings could not be read)");
    }

    @Test
    void unreadableEventSettingsAreLoggedWithoutTheResponseBody() {
        stubTrigger(SET_FIELD, "workflow-trigger-set-field");
        stubFor(get(urlEqualTo("/v1/settings/events"))
                .willReturn(aResponse().withStatus(403).withBody("{\"message\":\"detail-from-ilm\"}")));
        Logger logger = (Logger) LoggerFactory.getLogger(WorkflowServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level level = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            service.getTrigger(SET_FIELD);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(level);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Could not read the platform event settings: Forbidden");
    }

    @Test
    void listTriggerFieldsSaysWhatConditionsCheckAndSetFieldsSet() {
        stubFor(get(urlEqualTo("/v1/resources/certificates/filters/rules?settable=false"))
                .willReturn(okJson(fixture("rule-fields-certificates"))));
        stubFor(get(urlEqualTo("/v1/resources/certificates/filters/rules?settable=true"))
                .willReturn(okJson(fixture("rule-fields-certificates-settable"))));

        assertThat(service.listTriggerFields("certificate_expiring"))
                .contains("Trigger fields for certificate_expiring (certificates)\n", """
                        Conditions can check:
                        Source: custom
                          - attribute-1|STRING | attribute-1 (LIST) — Operators: EQUALS, NOT_EQUALS, EMPTY, \
                        NOT_EMPTY — Values: value-1, value-2
                        """,
                        "  - COMMON_NAME | Common Name (STRING) — Operators: CONTAINS, NOT_CONTAINS, EQUALS, NOT_EQUALS, "
                                + "EMPTY, NOT_EMPTY, STARTS_WITH, ENDS_WITH, MATCHES, NOT_MATCHES\n",
                        "  - GROUP_NAME | Groups (LIST) — Operators: EQUALS, NOT_EQUALS, EMPTY, NOT_EMPTY, COUNT_EQUAL, "
                                + "COUNT_NOT_EQUAL, COUNT_GREATER_THAN, COUNT_LESS_THAN — Values: "
                                + "group-1 [00000000-0000-4000-8000-000000000201], group-2 [",
                        "group-10 [00000000-0000-4000-8000-000000000210], and 2 more\n",
                        "  - KEY_SIZE | Key Size (LIST) — Operators: EQUALS, NOT_EQUALS, EMPTY, NOT_EMPTY — Values: -1, "
                                + "192, 256\n",
                        """
                                setFields can set:
                                Source: custom
                                  - attribute-1|STRING | attribute-1 (LIST) — Values: value-1, value-2
                                Source: property
                                  - RA_PROFILE_NAME | RA Profile (LIST) — Values: \
                                ra-profile-1 [00000000-0000-4000-8000-000000000301], \
                                ra-profile-2 [00000000-0000-4000-8000-000000000302]
                                """,
                        "  - OWNER | Owner (LIST) — Values: user-1 [00000000-0000-4000-8000-000000000401], "
                                + "user-2 [00000000-0000-4000-8000-000000000402]\n")
                .doesNotContain("group-11");
    }

    @Test
    void listTriggerFieldsSaysWhenThereAreNone() {
        stubFor(get(urlPathEqualTo("/v1/resources/comments/filters/rules")).willReturn(okJson("[]")));

        assertThat(service.listTriggerFields("comment_created"))
                .contains("Trigger fields for comment_created (comments)\n", "Conditions can check:\n  none\n",
                        "setFields can set:\n  none\n");
    }

    @Test
    void listTriggersFiltersByEvent() {
        stubTriggerList();

        assertThat(service.listTriggers(null))
                .contains("Triggers (5)", "- name-42 [", "Event: comment_created (comments)", "Ignore Trigger: yes",
                        "Description: description-43");
        assertThat(service.listTriggers("certificate_discovered"))
                .contains("Triggers for certificate_discovered (2)", "name-44", "name-45")
                .doesNotContain("name-42");
    }

    @Test
    void listEventTriggersShowsOrderForOneEvent() {
        stubEventsSettings();
        stubTriggerList();

        assertThat(service.listEventTriggers("certificate_status_changed"))
                .contains("Platform triggers for certificate_status_changed (6)",
                        "3. name-47 [cb36ddc3-bb10-4bc0-9624-92442f903dc2]", "   description-48",
                        WorkflowText.PLATFORM_NOTE);
        assertThat(service.listEventTriggers("certificate_discovered"))
                .contains("No platform triggers for certificate_discovered.");
    }

    @Test
    void listEventTriggersCoversEveryEvent() {
        stubEventsSettings();
        stubTriggerList();

        assertThat(service.listEventTriggers(null))
                .contains("Platform event triggers", "certificate_status_changed (6):", "scheduled_job_finished (1):",
                        "1. name-49 [e4004ce0-2b01-4603-a394-8347314aefe0]");
    }

    @Test
    void unknownEventFilterAnswersWithTheValidEvents() {
        assertThat(service.listTriggers("certificate_found")).startsWith("Unknown event 'certificate_found'.");
    }

    static void stubTrigger(String uuid, String fixture) {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + uuid)).willReturn(okJson(fixture(fixture))));
    }

    static void stubEventsSettings() {
        stubFor(get(urlEqualTo("/v1/settings/events")).willReturn(okJson(fixture("events-settings"))));
    }

    static void stubTriggerList() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers")).willReturn(okJson(fixture("workflow-triggers"))));
    }
}
