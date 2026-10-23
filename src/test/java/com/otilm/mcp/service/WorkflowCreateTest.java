package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.otilm.api.model.core.other.ResourceEvent;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class WorkflowCreateTest {

    static final String CONDITION = "00000000-0000-4000-8000-000000000001";
    static final String RULE = "00000000-0000-4000-8000-000000000002";
    static final String SET = "00000000-0000-4000-8000-000000000003";
    static final String NOTIFY_EXECUTION = "00000000-0000-4000-8000-000000000004";
    static final String ACTION = "00000000-0000-4000-8000-000000000005";
    static final String TRIGGER = "00000000-0000-4000-8000-000000000006";
    private static final String GROUP = "00000000-0000-4000-8000-000000000100";
    private static final String OWNER = "00000000-0000-4000-8000-000000000101";
    private static final String PROFILE = "00000000-0000-4000-8000-000000000102";
    private static final String WORKFLOW_OBJECTS = "/v1/workflows/.*";
    private static final String EVENTS = "/v1/settings/events";
    private static final String RULE_FIELDS = "/v1/resources/certificates/filters/rules";
    private static final String CONDITIONS = "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\",\"condition\":\"ENDS_WITH\",\"value\":\".example.com\"}]";
    private static final String SET_FIELDS = """
            [{"fieldSource":"property","fieldIdentifier":"GROUP_NAME","value":["%s"]},
             {"fieldSource":"property","fieldIdentifier":"OWNER","value":"%s"},
             {"fieldSource":"custom","fieldIdentifier":"attribute-1|STRING","value":"web"}]
            """.formatted(GROUP, OWNER);

    private WorkflowService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new WorkflowServiceImpl(IlmTestSupport.client(wmInfo));
        stubFor(get(urlEqualTo(RULE_FIELDS + "?settable=false"))
                .willReturn(okJson(fixture("rule-fields-certificates"))));
        stubFor(get(urlEqualTo(RULE_FIELDS + "?settable=true"))
                .willReturn(okJson(fixture("rule-fields-certificates-settable"))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + PROFILE)).willReturn(okJson("""
                {"uuid":"%s","name":"p","version":1,"recipientType":"owner","internalNotification":true}
                """.formatted(PROFILE))));
    }

    @Test
    void createTriggerSendsTheWholeChain() {
        stubCreates();
        stubFor(get(urlEqualTo(EVENTS)).willReturn(okJson(fixture("events-settings"))));
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(noContent()));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, PROFILE,
                        null, null));

        verify(postRequestedFor(urlEqualTo("/v1/workflows/conditions"))
                .withRequestBody(equalToJson(
                        """
                                {"name":"expiring-web","type":"checkField","resource":"certificates","items":[
                                 {"fieldSource":"property","fieldIdentifier":"COMMON_NAME","operator":"ENDS_WITH","value":".example.com"}]}
                                """,
                        false, true)));
        verify(postRequestedFor(urlEqualTo("/v1/workflows/rules"))
                .withRequestBody(equalToJson("{\"name\":\"expiring-web\",\"conditionsUuids\":[\"" + CONDITION + "\"]}",
                        false, true)));
        verify(postRequestedFor(urlEqualTo("/v1/workflows/executions")).withRequestBody(equalToJson("""
                {"name":"expiring-web - set fields","type":"setField","items":[
                 {"fieldSource":"property","fieldIdentifier":"GROUP_NAME","data":["%s"]},
                 {"fieldSource":"property","fieldIdentifier":"OWNER","data":"%s"},
                 {"fieldSource":"custom","fieldIdentifier":"attribute-1|STRING","data":"web"}]}
                """.formatted(GROUP, OWNER), false, true)));
        verify(postRequestedFor(urlEqualTo("/v1/workflows/executions")).withRequestBody(equalToJson("""
                {"name":"expiring-web - notify","type":"sendNotification","items":[{"notificationProfileUuid":"%s"}]}
                """.formatted(PROFILE), false, true)));
        verify(postRequestedFor(urlEqualTo("/v1/workflows/actions"))
                .withRequestBody(equalToJson("{\"name\":\"expiring-web\",\"executionsUuids\":[\"" + SET + "\",\""
                        + NOTIFY_EXECUTION + "\"]}", false, true)));
        verify(postRequestedFor(urlEqualTo("/v1/workflows/triggers")).withRequestBody(equalToJson("""
                {"name":"expiring-web","type":"event","event":"certificate_expiring","resource":"certificates",
                 "ignoreTrigger":false,"rulesUuids":["%s"],"actionsUuids":["%s"]}
                """.formatted(RULE, ACTION), false, true)));
        verify(patchRequestedFor(urlEqualTo(EVENTS)).withRequestBody(equalToJson("""
                {"event":"certificate_expiring","triggerUuids":["729af1fb-d1b7-48b0-bf6f-1bdafcad8a91","%s"]}
                """.formatted(TRIGGER))));
        assertThat(result)
                .contains("Created trigger expiring-web [" + TRIGGER + "].",
                        "Attached to certificate_expiring platform-wide (position 2 of 2).",
                        WorkflowText.runAsNote(ResourceEvent.CERTIFICATE_EXPIRING));
    }

    @Test
    void createTriggerAttachesToAnEventWithoutTriggers() {
        stubCreates();
        stubFor(get(urlEqualTo(EVENTS)).willReturn(okJson("{\"eventsMapping\":{}}")));
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(noContent()));

        String result = service
                .createTrigger(
                        new TriggerInput("expiring-web", "certificate_expiring", null, null, PROFILE, null, null));

        verify(0, postRequestedFor(urlEqualTo("/v1/workflows/conditions")));
        verify(0, postRequestedFor(urlEqualTo("/v1/workflows/rules")));
        verify(0, getRequestedFor(urlPathEqualTo(RULE_FIELDS)));
        verify(patchRequestedFor(urlEqualTo(EVENTS))
                .withRequestBody(
                        equalToJson("{\"event\":\"certificate_expiring\",\"triggerUuids\":[\"" + TRIGGER + "\"]}")));
        assertThat(result).contains("position 1 of 1");
    }

    @Test
    void createIgnoreTriggerHasNoActionsAndNeedNotAttach() {
        stubCreates();

        String result = service
                .createTrigger(
                        new TriggerInput("skip-test", "certificate_discovered", CONDITIONS, null, null, true, false));

        verify(0, postRequestedFor(urlEqualTo("/v1/workflows/executions")));
        verify(0, postRequestedFor(urlEqualTo("/v1/workflows/actions")));
        verify(postRequestedFor(urlEqualTo("/v1/workflows/triggers"))
                .withRequestBody(
                        equalToJson("{\"ignoreTrigger\":true,\"actionsUuids\":[],\"rulesUuids\":[\"" + RULE + "\"]}",
                                false, true)));
        verify(0, getRequestedFor(urlEqualTo(EVENTS)));
        assertThat(result).contains("Not attached. Attach it with attach_trigger when it should start running.");
    }

    @Test
    void createTriggerRemovesWhatItCreatedWhenAStepFails() {
        stubCreates();
        stubFor(post(urlEqualTo("/v1/workflows/triggers"))
                .willReturn(unprocessable("Trigger with same name already exists.")));
        stubFor(delete(urlMatching(WORKFLOW_OBJECTS)).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/workflows/conditions/" + CONDITION))
                .willReturn(unprocessable("Condition is in use.")));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, null,
                        null, null));

        assertThat(findAll(deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS))).stream().map(LoggedRequest::getUrl))
                .containsExactly("/v1/workflows/actions/" + ACTION, "/v1/workflows/executions/" + SET,
                        "/v1/workflows/rules/" + RULE, "/v1/workflows/conditions/" + CONDITION);
        assertThat(result)
                .startsWith("Error creating trigger: ILM answered 422")
                .contains("Trigger with same name already exists.", "Could not remove:",
                        "- condition expiring-web [" + CONDITION + "]: ", "Condition is in use.");
    }

    @Test
    void createTriggerFailingAtTheFirstStepCreatesNothing() {
        stubFor(post(urlEqualTo("/v1/workflows/conditions"))
                .willReturn(unprocessable("Condition with same name already exists.")));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, null,
                        null, null));

        verify(0, deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .startsWith("Error creating trigger: ILM answered 422")
                .contains("Condition with same name already exists.")
                .doesNotContain("Removed what this call had created.", "Could not remove:");
    }

    @Test
    void createTriggerReportsACompleteCleanup() {
        stubCreates();
        stubFor(post(urlEqualTo("/v1/workflows/actions"))
                .willReturn(unprocessable("Action with same name already exists.")));
        stubFor(delete(urlMatching(WORKFLOW_OBJECTS)).willReturn(noContent()));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, null,
                        null, null));

        assertThat(result).endsWith("Removed what this call had created.");
    }

    @Test
    void createTriggerRemovesEverythingWhenTheEventSettingsCannotBeRead() {
        stubCreates();
        stubFor(get(urlEqualTo(EVENTS)).willReturn(aResponse().withStatus(403)));
        stubFor(delete(urlMatching(WORKFLOW_OBJECTS)).willReturn(noContent()));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, null,
                        null, null));

        verify(0, patchRequestedFor(urlEqualTo(EVENTS)));
        assertRemovedNewestFirst(result, "Error creating trigger: ILM answered 403");
    }

    @Test
    void createTriggerRemovesEverythingWhenAttachingIsRefused() {
        stubCreates();
        stubFor(get(urlEqualTo(EVENTS)).willReturn(okJson(fixture("events-settings"))));
        stubFor(patch(urlEqualTo(EVENTS)).willReturn(unprocessable("Trigger cannot be associated with the event.")));
        stubFor(delete(urlMatching(WORKFLOW_OBJECTS)).willReturn(noContent()));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, null,
                        null, null));

        assertRemovedNewestFirst(result, "Error creating trigger: ILM answered 422");
    }

    @Test
    void createTriggerAcceptsAnAttachmentMadeMeanwhile() {
        stubCreates();
        stubFor(get(urlEqualTo(EVENTS))
                .willReturn(okJson("{\"eventsMapping\":{\"certificate_expiring\":[\"" + TRIGGER + "\"]}}")));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, SET_FIELDS, null,
                        null, null));

        verify(0, patchRequestedFor(urlEqualTo(EVENTS)));
        verify(0, deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .startsWith("Created trigger expiring-web [" + TRIGGER + "].")
                .endsWith("Already attached to certificate_expiring platform-wide.\n");
    }

    private static void assertRemovedNewestFirst(String result, String error) {
        assertThat(findAll(deleteRequestedFor(urlMatching(WORKFLOW_OBJECTS))).stream().map(LoggedRequest::getUrl))
                .containsExactly("/v1/workflows/triggers/" + TRIGGER, "/v1/workflows/actions/" + ACTION,
                        "/v1/workflows/executions/" + SET, "/v1/workflows/rules/" + RULE,
                        "/v1/workflows/conditions/" + CONDITION);
        assertThat(result).startsWith(error).endsWith("Removed what this call had created.");
    }

    @Test
    void createTriggerRefusesConditionsOnUnlistedFieldsAndCreatesNothing() {
        String conditions = """
                [{"fieldSource":"property","fieldIdentifier":"COMMON_NAME","condition":"EQUALS","value":"a"},
                 {"fieldSource":"property","fieldIdentifier":"GROUPNAME","condition":"EQUALS","value":"b"},
                 {"fieldSource":"custom","fieldIdentifier":"Category|STRING","condition":"EQUALS","value":"c"}]
                """;

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", conditions, null, PROFILE, null,
                        null));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        verify(0, getRequestedFor(urlEqualTo(RULE_FIELDS + "?settable=true")));
        assertThat(result)
                .isEqualTo("Conditions cannot check property GROUPNAME, custom Category|STRING; list_trigger_fields "
                        + "lists the fields they can check.");
    }

    @Test
    void createTriggerRefusesAnOperatorTheFieldDoesNotTake() {
        String conditions = "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\","
                + "\"condition\":\"COUNT_GREATER_THAN\",\"value\":1}]";

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", conditions, null, PROFILE, null,
                        null));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo("Conditions cannot use COUNT_GREATER_THAN on property COMMON_NAME; list_trigger_fields "
                        + "lists the operators each field takes.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"conditions\":[],", ""})
    void createTriggerRefusesOperatorsOnAFieldThatListsNone(String operators) {
        stubFor(get(urlEqualTo(RULE_FIELDS + "?settable=false")).willReturn(okJson("""
                [{"filterFieldSource":"property","searchFieldData":[
                  {"fieldIdentifier":"COMMON_NAME","fieldLabel":"Common Name","type":"string",%s"multiValue":false}]}]
                """.formatted(operators))));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, null, PROFILE, null,
                        null));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo("Conditions cannot use ENDS_WITH on property COMMON_NAME; list_trigger_fields lists the "
                        + "operators each field takes.");
    }

    @Test
    void createTriggerCreatesNothingWhenTheFieldsCannotBeRead() {
        stubFor(get(urlEqualTo(RULE_FIELDS + "?settable=false")).willReturn(aResponse().withStatus(403)));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, null, PROFILE, null,
                        null));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result).startsWith("Error creating trigger: ILM answered 403");
    }

    @Test
    void createTriggerRefusesSetFieldsOnUnlistedFieldsAndCreatesNothing() {
        String setFields = "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\",\"value\":\"x\"}]";

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, setFields, null,
                        null, null));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo(
                        "setFields cannot set property COMMON_NAME; list_trigger_fields lists the fields it can set.");
    }

    @Test
    void createTriggerRefusesAnUnknownNotificationProfile() {
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + PROFILE)).willReturn(aResponse().withStatus(404)));

        String result = service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", CONDITIONS, null, PROFILE, null,
                        null));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo(
                        "Notification profile " + PROFILE + " was not found; list_notification_profiles lists them.");
    }

    @Test
    void invalidInputCallsNothing() {
        assertThat(service.createTrigger(new TriggerInput("t", "certificate_expiring", null, null, null, null, null)))
                .isEqualTo("A trigger needs something to do: setFields, notificationProfileUuid, or ignore=true.");
    }

    static void stubCreates() {
        stubFor(post(urlEqualTo("/v1/workflows/conditions")).willReturn(okJson(created(CONDITION))));
        stubFor(post(urlEqualTo("/v1/workflows/rules")).willReturn(okJson(created(RULE))));
        stubFor(post(urlEqualTo("/v1/workflows/executions"))
                .withRequestBody(containing("setField"))
                .willReturn(okJson(created(SET))));
        stubFor(post(urlEqualTo("/v1/workflows/executions"))
                .withRequestBody(containing("sendNotification"))
                .willReturn(okJson(created(NOTIFY_EXECUTION))));
        stubFor(post(urlEqualTo("/v1/workflows/actions")).willReturn(okJson(created(ACTION))));
        stubFor(post(urlEqualTo("/v1/workflows/triggers")).willReturn(okJson("""
                {"uuid":"%s","name":"expiring-web","type":"event","resource":"certificates",
                 "event":"certificate_expiring","ignoreTrigger":false,"rules":[],"actions":[]}
                """.formatted(TRIGGER))));
    }

    static ResponseDefinitionBuilder unprocessable(String message) {
        return aResponse()
                .withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"message\":\"" + message + "\"}");
    }

    private static String created(String uuid) {
        return "{\"uuid\":\"" + uuid + "\",\"name\":\"expiring-web\"}";
    }
}
