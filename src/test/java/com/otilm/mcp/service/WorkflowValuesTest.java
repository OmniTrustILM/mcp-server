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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static com.otilm.mcp.service.WorkflowCreateTest.stubCreates;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class WorkflowValuesTest {

    private static final String GROUP_1 = "00000000-0000-4000-8000-000000000201";
    private static final String USER_1 = "00000000-0000-4000-8000-000000000401";
    private static final String PROFILE = "00000000-0000-4000-8000-000000000102";
    private static final String WORKFLOW_OBJECTS = "/v1/workflows/.*";
    private static final String CONDITION_FIELDS = "/v1/resources/certificates/filters/rules?settable=false";
    private static final String SETTABLE_FIELDS = "/v1/resources/certificates/filters/rules?settable=true";
    private static final String NOTHING_LISTED = """
            [{"filterFieldSource":"custom","searchFieldData":[
              {"fieldIdentifier":"attribute-1|STRING","fieldLabel":"attribute-1","type":"list","conditions":["EQUALS"],
               "attributeContentType":"string","value":[],"multiValue":true}]},
             {"filterFieldSource":"property","searchFieldData":[
              {"fieldIdentifier":"GROUP_NAME","fieldLabel":"Groups","type":"list","conditions":["EQUALS"],
               "value":[],"multiValue":true},
              {"fieldIdentifier":"KEY_SIZE","fieldLabel":"Key Size","type":"list","conditions":["EQUALS"],
               "value":[],"multiValue":true}]}]
            """;
    private static final String BY_NAME = "; ILM matches such fields by name, and list_trigger_fields lists the names.";
    private static final String BY_UUID = "; ILM sets such fields by UUID, and list_trigger_fields lists the UUIDs.";

    private WorkflowService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new WorkflowServiceImpl(IlmTestSupport.client(wmInfo));
        stubFor(get(urlEqualTo(CONDITION_FIELDS)).willReturn(okJson(fixture("rule-fields-certificates"))));
        stubFor(get(urlEqualTo(SETTABLE_FIELDS)).willReturn(okJson(fixture("rule-fields-certificates-settable"))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + PROFILE)).willReturn(okJson("""
                {"uuid":"%s","name":"p","version":1,"recipientType":"owner","internalNotification":true}
                """.formatted(PROFILE))));
    }

    @Test
    void aConditionOnAGroupUuidIsRefusedWithTheNameToUse() {
        String result = notifyWhen(groups("EQUALS", "[\"" + GROUP_1 + "\"]"));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo("Conditions cannot match property GROUP_NAME with \"" + GROUP_1
                        + "\" (use the name \"group-1\")" + BY_NAME);
    }

    @Test
    void aConditionOnANameIlmDoesNotListIsRefused() {
        String result = notifyWhen(groups("NOT_EQUALS", "[\"group-1\",\"group-99\"]"));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result).isEqualTo("Conditions cannot match property GROUP_NAME with \"group-99\"" + BY_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"group-1\"", "[]", "null"})
    void aConditionWithoutAListOfNamesIsRefused(String value) {
        String result = notifyWhen(groups("EQUALS", value));

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo("Conditions cannot match property GROUP_NAME without a list of names, such as "
                        + "[\"<name>\"]" + BY_NAME);
    }

    @Test
    void everyRefusedConditionValueIsReportedAtOnce() {
        stubFor(get(urlEqualTo(CONDITION_FIELDS)).willReturn(okJson("""
                [{"filterFieldSource":"property","searchFieldData":[
                  {"fieldIdentifier":"GROUP_NAME","fieldLabel":"Groups","type":"list","conditions":["EQUALS"],
                   "value":[{"name":"group-1","uuid":"%s"}],"multiValue":true},
                  {"fieldIdentifier":"OWNER","fieldLabel":"Owner","type":"list","conditions":["EQUALS"],
                   "value":[{"name":"user-1","uuid":"%s"}],"multiValue":true}]}]
                """.formatted(GROUP_1, USER_1))));
        String conditions = """
                [{"fieldSource":"property","fieldIdentifier":"GROUP_NAME","condition":"EQUALS","value":["%s","group-9"]},
                 {"fieldSource":"property","fieldIdentifier":"OWNER","condition":"EQUALS","value":["%s"]}]
                """
                .formatted(GROUP_1, USER_1);

        String result = notifyWhen(conditions);

        assertThat(result)
                .isEqualTo("Conditions cannot match property GROUP_NAME with \"" + GROUP_1
                        + "\" (use the name \"group-1\") or \"group-9\", property OWNER with \"" + USER_1
                        + "\" (use the name \"user-1\")" + BY_NAME);
    }

    @Test
    void aNameToUseIsShownAsItIsWrittenInAList() {
        stubFor(get(urlEqualTo(CONDITION_FIELDS)).willReturn(okJson("""
                [{"filterFieldSource":"property","searchFieldData":[
                  {"fieldIdentifier":"GROUP_NAME","fieldLabel":"Groups","type":"list","conditions":["EQUALS"],
                   "value":[{"name":"[\\"production\\"]","uuid":"%s"}],"multiValue":true}]}]
                """.formatted(GROUP_1))));

        String result = notifyWhen(groups("EQUALS", "[\"" + GROUP_1 + "\"]"));

        assertThat(result).contains("(use the name \"[\\\"production\\\"]\")");
    }

    @Test
    void aConditionOnListedNamesIsSentAsGiven() {
        stubCreates();

        notifyWhen(groups("EQUALS", "[\"group-1\",\"group-12\"]"));

        verify(postRequestedFor(urlEqualTo("/v1/workflows/conditions")).withRequestBody(equalToJson("""
                {"items":[{"fieldSource":"property","fieldIdentifier":"GROUP_NAME","operator":"EQUALS",
                 "value":["group-1","group-12"]}]}
                """, false, true)));
    }

    @Test
    void aNameThatIsAlsoAUuidIsAccepted() {
        stubFor(get(urlEqualTo(CONDITION_FIELDS)).willReturn(okJson("""
                [{"filterFieldSource":"property","searchFieldData":[
                  {"fieldIdentifier":"GROUP_NAME","fieldLabel":"Groups","type":"list","conditions":["EQUALS"],
                   "value":[{"name":"%s","uuid":"00000000-0000-4000-8000-000000000202"},
                            {"name":"group-1","uuid":"%s"}],"multiValue":true}]}]
                """.formatted(GROUP_1, GROUP_1))));
        stubCreates();

        notifyWhen(groups("EQUALS", "[\"" + GROUP_1 + "\"]"));

        verify(postRequestedFor(urlEqualTo("/v1/workflows/conditions")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"condition\":\"EMPTY\"}",
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"condition\":\"COUNT_GREATER_THAN\",\"value\":1}",
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"KEY_SIZE\",\"condition\":\"EQUALS\",\"value\":[4096]}",
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"COMMON_NAME\",\"condition\":\"EQUALS\",\"value\":\"group-99\"}",
            "{\"fieldSource\":\"custom\",\"fieldIdentifier\":\"attribute-1|STRING\",\"condition\":\"EQUALS\",\"value\":\"value-3\"}"})
    void conditionsThatMatchNoListedNameAreNotChecked(String condition) {
        stubCreates();

        notifyWhen("[" + condition + "]");

        verify(postRequestedFor(urlEqualTo("/v1/workflows/conditions")));
    }

    @Test
    void aConditionOnAListFieldWithoutValuesYetIsNotChecked() {
        stubFor(get(urlEqualTo(CONDITION_FIELDS)).willReturn(okJson(NOTHING_LISTED)));
        stubCreates();

        notifyWhen("[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"KEY_SIZE\",\"condition\":\"EQUALS\","
                + "\"value\":[1024]}]");

        verify(postRequestedFor(urlEqualTo("/v1/workflows/conditions")));
    }

    @Test
    void setFieldsWithAGroupNameIsRefusedWithTheUuidToUse() {
        String result = set("GROUP_NAME", "[\"group-1\"]");

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo("setFields cannot set property GROUP_NAME to \"group-1\" (use the UUID \"" + GROUP_1 + "\")"
                        + BY_UUID);
    }

    @Test
    void setFieldsWithAUuidIlmDoesNotListIsRefused() {
        String result = set("OWNER", "\"00000000-0000-4000-8000-000000000499\"");

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result)
                .isEqualTo("setFields cannot set property OWNER to \"00000000-0000-4000-8000-000000000499\"" + BY_UUID);
    }

    @Test
    void everyRefusedSetFieldsValueIsReportedAtOnce() {
        String result = set("""
                [{"fieldSource":"property","fieldIdentifier":"GROUP_NAME","value":["%s","group-2","group-99"]},
                 {"fieldSource":"property","fieldIdentifier":"OWNER","value":"user-1"}]
                """.formatted(GROUP_1));

        assertThat(result)
                .isEqualTo("setFields cannot set property GROUP_NAME to \"group-2\" (use the UUID "
                        + "\"00000000-0000-4000-8000-000000000202\") or \"group-99\", property OWNER to \"user-1\" "
                        + "(use the UUID \"" + USER_1 + "\")" + BY_UUID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"value\":[\"00000000-0000-4000-8000-000000000201\",\"00000000-0000-4000-8000-000000000212\"]}",
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"OWNER\",\"value\":\"00000000-0000-4000-8000-000000000401\"}",
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"value\":[]}",
            "{\"fieldSource\":\"custom\",\"fieldIdentifier\":\"attribute-1|STRING\",\"value\":\"value-3\"}"})
    void setFieldsThatSetListedObjectsOrNoneAreSentAsGiven(String setField) {
        stubCreates();

        set("[" + setField + "]");

        verify(postRequestedFor(urlEqualTo("/v1/workflows/executions"))
                .withRequestBody(
                        equalToJson("{\"items\":[" + setField.replace("\"value\"", "\"data\"") + "]}", false, true)));
    }

    @Test
    void setFieldsAcceptsAListedUuidInEitherCase() {
        stubFor(get(urlEqualTo(SETTABLE_FIELDS)).willReturn(okJson("""
                [{"filterFieldSource":"property","searchFieldData":[
                  {"fieldIdentifier":"OWNER","fieldLabel":"Owner","type":"list","conditions":["EQUALS"],
                   "value":[{"name":"user-1","uuid":"0000000a-0000-4000-8000-00000000040b"}],"multiValue":true}]}]
                """)));
        stubCreates();

        set("OWNER", "\"0000000A-0000-4000-8000-00000000040B\"");

        verify(postRequestedFor(urlEqualTo("/v1/workflows/executions")));
    }

    @Test
    void setFieldsOnAPropertyThatListsNothingIsRefused() {
        stubFor(get(urlEqualTo(SETTABLE_FIELDS)).willReturn(okJson(NOTHING_LISTED)));

        String result = set("GROUP_NAME", "[\"group-1\"]");

        verify(0, postRequestedFor(urlMatching(WORKFLOW_OBJECTS)));
        assertThat(result).isEqualTo("setFields cannot set property GROUP_NAME to \"group-1\"" + BY_UUID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"value\":[]}",
            "{\"fieldSource\":\"custom\",\"fieldIdentifier\":\"attribute-1|STRING\",\"value\":\"value-3\"}"})
    void setFieldsThatNeedNoListedObjectAreSentWhenNothingIsListed(String setField) {
        stubFor(get(urlEqualTo(SETTABLE_FIELDS)).willReturn(okJson(NOTHING_LISTED)));
        stubCreates();

        set("[" + setField + "]");

        verify(postRequestedFor(urlEqualTo("/v1/workflows/executions")));
    }

    @Test
    void aRefusalIsLoggedWithoutTheGivenValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(TriggerRequests.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level level = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            notifyWhen(groups("EQUALS", "[\"" + GROUP_1 + "\"]"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(level);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Refused a trigger with fields, operators or values that ILM does not list");
    }

    private String set(String field, String value) {
        return set("[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"%s\",\"value\":%s}]".formatted(field, value));
    }

    private String set(String setFields) {
        return service
                .createTrigger(
                        new TriggerInput("expiring-web", "certificate_expiring", null, setFields, null, null, false));
    }

    private String notifyWhen(String conditions) {
        return service
                .createTrigger(new TriggerInput("expiring-web", "certificate_expiring", conditions, null, PROFILE, null,
                        false));
    }

    private static String groups(String operator, String value) {
        return "[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"GROUP_NAME\",\"condition\":\"%s\",\"value\":%s}]"
                .formatted(operator, value);
    }
}
