package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class NotificationInstanceTest {

    static final String EMAIL_CONNECTOR = "00000000-0000-4000-8000-000000000501";
    static final String WEBHOOK_CONNECTOR = "00000000-0000-4000-8000-000000000502";
    static final String WEBHOOK_CALLBACK = "/v1/connectors/" + WEBHOOK_CONNECTOR
            + "/notificationProvider/WEBHOOK/callback";
    private static final String INSTANCES = "/v1/notificationInstances";
    private static final String NEW_INSTANCE = "00000000-0000-4000-8000-000000000513";
    private static final String EMAIL_INSTANCE = "00000000-0000-4000-8000-000000000511";
    private static final String TEMPLATE = "{\"data_emailContentTemplate\":\"<p>${certificate}</p>\"}";

    private NotificationService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new NotificationServiceImpl(IlmTestSupport.client(wmInfo));
        stubConnector(EMAIL_CONNECTOR, "EMAIL", "email");
        stubConnector(WEBHOOK_CONNECTOR, "WEBHOOK", "webhook");
        stubFor(get(urlEqualTo("/v1/notificationInstances/attributes/mapping/" + EMAIL_CONNECTOR + "/EMAIL"))
                .willReturn(okJson(fixture("notification-mapping-attributes-email"))));
        stubFor(get(urlEqualTo("/v1/notificationInstances/attributes/mapping/" + WEBHOOK_CONNECTOR + "/WEBHOOK"))
                .willReturn(okJson("[]")));
    }

    static void stubConnector(String uuid, String kind, String name) {
        stubFor(get(urlEqualTo("/v1/connectors/" + uuid)).willReturn(okJson(fixture("connector-" + name))));
        stubFor(get(urlEqualTo("/v1/connectors/" + uuid + "/attributes/notificationProvider/" + kind))
                .willReturn(okJson(fixture("connector-attributes-" + name))));
    }

    @Test
    void emailSettingsListFieldsDefaultsAndMappings() {
        assertThat(service.getNotificationConnectorSettings(EMAIL_CONNECTOR, "EMAIL", null))
                .contains("Notification settings for name-2 [" + EMAIL_CONNECTOR + "], kind EMAIL",
                        "- data_senderEmailAddress | Sender email address (string, required) — Default: ilm@example.com",
                        "- data_emailContentTemplate | Email content template (codeblock, required) — Give it as plain "
                                + "text (html); no default",
                        "Recipient attributes that can be mapped to custom attributes:",
                        "- data_recipientEmailAddress | Recipient email address (string, list)");
    }

    @Test
    void webhookSettingsSayWhatTheTemplateDependsOn() {
        assertThat(service.getNotificationConnectorSettings(WEBHOOK_CONNECTOR, "WEBHOOK", null))
                .contains("- data_contentType | Content type (string, required) — One of: RAW_JSON, JSON, XML",
                        "Content type information: RAW_JSON sends",
                        "group_contentTemplate needs data_contentType; give it in attributes to see its fields");
        verify(0, postRequestedFor(urlPathMatching(".*/callback")));
    }

    @Test
    void webhookSettingsResolveTheTemplateForJson() {
        stubFor(post(urlEqualTo(WEBHOOK_CALLBACK)).willReturn(okJson(fixture("notification-callback-template-json"))));

        String result = service
                .getNotificationConnectorSettings(WEBHOOK_CONNECTOR, "WEBHOOK", "{\"data_contentType\":\"JSON\"}");

        verify(postRequestedFor(urlEqualTo(WEBHOOK_CALLBACK)).withRequestBody(equalToJson("""
                {"uuid":"00000000-0000-4000-8000-000000000614","name":"group_contentTemplate",
                 "pathVariable":{"contentType":"JSON"},"requestParameter":{},"body":{}}
                """)));
        assertThat(result)
                .contains(
                        "- data_contentTemplate_json | Content template (codeblock, required) — Give it as plain text "
                                + "(json); no default")
                .doesNotContain("needs data_contentType");
    }

    @Test
    void settingsOfAConnectorWithoutTheKind() {
        assertThat(service.getNotificationConnectorSettings(EMAIL_CONNECTOR, "SMS", null))
                .isEqualTo(
                        "Connector name-2 [" + EMAIL_CONNECTOR + "] has no notification kind SMS. Its kinds: EMAIL.");
    }

    @Test
    void settingsRefuseInvalidAttributesJson() {
        assertThat(service.getNotificationConnectorSettings(WEBHOOK_CONNECTOR, "WEBHOOK", "{not json"))
                .startsWith("Invalid attributes JSON. Expected a JSON object of field names and values, for example "
                        + "{\"data_webhookUrl\":\"https://example.com/hook\",\"data_contentType\":\"JSON\"}. Error: ");
    }

    @Test
    void emailInstanceNeedsATemplate() {
        assertThat(createEmail(null, null))
                .isEqualTo("Kind EMAIL needs values for: data_emailContentTemplate (Email content template).");
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
    }

    @Test
    void emailTemplateIsSentEncoded() {
        stubCreated();

        String result = createEmail(TEMPLATE, null);

        verify(postRequestedFor(urlEqualTo(INSTANCES))
                .withRequestBody(equalToJson(
                        """
                                {"name":"alerts","connectorUuid":"%s","kind":"EMAIL","attributes":[
                                 {"uuid":"00000000-0000-4000-8000-000000000601","name":"data_senderEmailAddress","contentType":"string",
                                  "content":[{"data":"ilm@example.com"}]},
                                 {"uuid":"00000000-0000-4000-8000-000000000602","name":"data_emailSubject","contentType":"string",
                                  "content":[{"data":"Certificate notification"}]},
                                 {"uuid":"00000000-0000-4000-8000-000000000603","name":"data_emailContentTemplate","contentType":"codeblock",
                                  "content":[{"data":{"code":"PHA+JHtjZXJ0aWZpY2F0ZX08L3A+","language":"html"}}]}],
                                 "attributeMappings":[]}
                                """
                                .formatted(EMAIL_CONNECTOR),
                        false, true)));
        assertThat(result)
                .startsWith("Created notification instance alerts [" + NEW_INSTANCE + "] for name-2, kind EMAIL.")
                .contains("Set: data_emailContentTemplate",
                        "ILM defaults used: data_senderEmailAddress, data_emailSubject")
                .doesNotContain("Mapped:");
    }

    @Test
    void webhookInstanceResolvesTheTemplateCallback() {
        stubCreated();
        stubFor(post(urlEqualTo(WEBHOOK_CALLBACK)).willReturn(okJson(fixture("notification-callback-template-json"))));

        String result = createWebhook("{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":\"JSON\","
                + "\"data_contentTemplate_json\":\"{\\\"event\\\":\\\"${event}\\\"}\"}");

        verify(postRequestedFor(urlEqualTo(WEBHOOK_CALLBACK)));
        verify(postRequestedFor(urlEqualTo(INSTANCES))
                .withRequestBody(equalToJson(
                        """
                                {"kind":"WEBHOOK","attributes":[
                                 {"uuid":"00000000-0000-4000-8000-000000000611","name":"data_webhookUrl","contentType":"string",
                                  "content":[{"data":"https://example.com/h"}]},
                                 {"uuid":"00000000-0000-4000-8000-000000000613","name":"data_contentType","contentType":"string",
                                  "content":[{"data":"JSON"}]},
                                 {"uuid":"00000000-0000-4000-8000-000000000615","name":"data_contentTemplate_json",
                                  "contentType":"codeblock",
                                  "content":[{"data":{"code":"eyJldmVudCI6IiR7ZXZlbnR9In0=","language":"json"}}]}]}
                                """,
                        false, true))
                .withRequestBody(notContaining("info_contentType"))
                .withRequestBody(notContaining("group_contentTemplate")));
        assertThat(result)
                .contains("Set: data_webhookUrl, data_contentType, data_contentTemplate_json")
                .doesNotContain("ILM defaults used");
    }

    @Test
    void jsonWebhookNeedsATemplate() {
        stubFor(post(urlEqualTo(WEBHOOK_CALLBACK)).willReturn(okJson(fixture("notification-callback-template-json"))));

        assertThat(createWebhook("{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":\"JSON\"}"))
                .isEqualTo("Kind WEBHOOK needs values for: data_contentTemplate_json (Content template).");
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
    }

    @Test
    void callbackFieldWithADefaultTemplateUsesIt() {
        stubCreated();
        stubFor(post(urlEqualTo(WEBHOOK_CALLBACK))
                .willReturn(okJson(fixture("notification-callback-template-json")
                        .replace("\"language\": \"json\"", "\"code\": \"eyJ9\", \"language\": \"json\""))));

        assertThat(service
                .getNotificationConnectorSettings(WEBHOOK_CONNECTOR, "WEBHOOK", "{\"data_contentType\":\"JSON\"}"))
                .contains("- data_contentTemplate_json | Content template (codeblock, required) — Default: a template "
                        + "(json)");
        assertThat(createWebhook("{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":\"JSON\"}"))
                .contains("ILM defaults used: data_contentTemplate_json");
    }

    @Test
    void extensibleListsTakeValuesOutsideTheirChoices() {
        stubCreated();
        stubFor(get(urlEqualTo("/v1/connectors/" + EMAIL_CONNECTOR + "/attributes/notificationProvider/EMAIL"))
                .willReturn(okJson("""
                        [{"uuid":"00000000-0000-4000-8000-000000000701","name":"data_tags","type":"data",
                          "contentType":"string","properties":{"label":"Tags","required":true,"list":true,
                          "extensibleList":true},"content":[{"data":"a"},{"data":"b"}]},
                         {"uuid":"00000000-0000-4000-8000-000000000702","name":"data_labels","type":"data",
                          "contentType":"string","properties":{"label":"Labels","list":true,"extensibleList":true},
                          "content":[]}]
                        """)));

        assertThat(service.getNotificationConnectorSettings(EMAIL_CONNECTOR, "EMAIL", null))
                .contains("- data_tags | Tags (string, required) — Suggested: a, b; other values are allowed",
                        "- data_labels | Labels (string) — Any value");
        createEmail("{\"data_tags\":\"custom\",\"data_labels\":\"blue\"}", null);

        verify(postRequestedFor(urlEqualTo(INSTANCES)).withRequestBody(equalToJson("""
                {"attributes":[
                 {"uuid":"00000000-0000-4000-8000-000000000701","name":"data_tags","contentType":"string",
                  "content":[{"data":"custom"}]},
                 {"uuid":"00000000-0000-4000-8000-000000000702","name":"data_labels","contentType":"string",
                  "content":[{"data":"blue"}]}]}
                """, false, true)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|',
            value = {
                    "{\"data_tags\":[\"x\",\"y\"]}|Field data_tags takes one value.",
                    "{\"data_tags\":[null]}|Field data_tags has no value; leave it out to use the connector's default.",
                    "{\"data_tags\":[]}|Field data_tags has no value; leave it out to use the connector's default."})
    void listValuesMustBePresentAndSingle(String attributes, String message) {
        stubFor(get(urlEqualTo("/v1/connectors/" + EMAIL_CONNECTOR + "/attributes/notificationProvider/EMAIL"))
                .willReturn(okJson("""
                        [{"uuid":"00000000-0000-4000-8000-000000000701","name":"data_tags","type":"data",
                          "contentType":"string","properties":{"label":"Tags","required":true,"list":true,
                          "extensibleList":true},"content":[]}]
                        """)));

        assertThat(createEmail(attributes, null)).isEqualTo(message);
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
    }

    @Test
    void severalContentTypesAreRefusedBeforeTheCallback() {
        assertThat(createWebhook(
                "{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":[\"JSON\",\"XML\"]}"))
                .isEqualTo("Field data_contentType takes one value.");
        verify(0, postRequestedFor(urlPathMatching(".*/callback")));
    }

    @Test
    void multiSelectSourceReachesTheCallbackAsAList() {
        String callback = "/v1/connectors/" + EMAIL_CONNECTOR + "/notificationProvider/EMAIL/callback";
        stubFor(get(urlEqualTo("/v1/connectors/" + EMAIL_CONNECTOR + "/attributes/notificationProvider/EMAIL"))
                .willReturn(okJson("""
                        [{"uuid":"00000000-0000-4000-8000-000000000703","name":"data_channels","type":"data",
                          "contentType":"string","properties":{"label":"Channels","list":true,"multiSelect":true},
                          "content":[{"data":"a"},{"data":"b"}]},
                         {"uuid":"00000000-0000-4000-8000-000000000704","name":"group_extra","type":"group",
                          "attributeCallback":{"callbackMethod":"GET","mappings":[
                           {"from":"data_channels.data","to":"channels","targets":["requestParameter"]}]}}]
                        """)));
        stubFor(post(urlEqualTo(callback)).willReturn(okJson("[]")));

        service.getNotificationConnectorSettings(EMAIL_CONNECTOR, "EMAIL", "{\"data_channels\":[\"a\"]}");

        verify(postRequestedFor(urlEqualTo(callback))
                .withRequestBody(equalToJson("{\"requestParameter\":{\"channels\":[\"a\"]}}", false, true)));
    }

    @Test
    void singleItemArrayResolvesTheCallback() {
        stubCreated();
        stubFor(post(urlEqualTo(WEBHOOK_CALLBACK)).willReturn(okJson(fixture("notification-callback-template-json"))));

        createWebhook("{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":[\"JSON\"],"
                + "\"data_contentTemplate_json\":\"t\"}");

        verify(postRequestedFor(urlEqualTo(WEBHOOK_CALLBACK))
                .withRequestBody(equalToJson("{\"pathVariable\":{\"contentType\":\"JSON\"}}", false, true)));
        verify(postRequestedFor(urlEqualTo(INSTANCES))
                .withRequestBody(containing("\"content\":[{\"data\":\"JSON\"}]")));
    }

    @Test
    void rawJsonWebhookNeedsNoTemplate() {
        stubCreated();
        stubFor(post(urlEqualTo(WEBHOOK_CALLBACK)).willReturn(okJson("[]")));

        createWebhook("{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":\"RAW_JSON\"}");

        verify(postRequestedFor(urlEqualTo(INSTANCES))
                .withRequestBody(matchingJsonPath("$.attributes.length()", equalTo("2")))
                .withRequestBody(notContaining("data_contentTemplate")));
    }

    @Test
    void unknownMappingIsRefusedBeforeIlm() {
        stubFor(get(urlEqualTo("/v1/attributes/custom")).willReturn(okJson(fixture("custom-attributes"))));

        assertThat(createEmail(TEMPLATE, "{\"data_recipientEmailAddress\":\"Missing\"}"))
                .isEqualTo("No custom attribute named or with UUID 'Missing'. list_custom_attributes lists them.");
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
    }

    @Test
    void mappingResolvesACustomAttributeByName() {
        stubCreated();
        stubFor(get(urlEqualTo("/v1/attributes/custom")).willReturn(okJson(fixture("custom-attributes"))));

        String result = createEmail(TEMPLATE, "{\"data_recipientEmailAddress\":\"name-51\"}");

        verify(postRequestedFor(urlEqualTo(INSTANCES)).withRequestBody(equalToJson("""
                {"attributeMappings":[{"mappingAttributeUuid":"00000000-0000-4000-8000-000000000621",
                 "mappingAttributeName":"data_recipientEmailAddress",
                 "customAttributeUuid":"295e8f4e-b300-4f19-a8f4-a7cecb9da498","customAttributeLabel":"label-52"}]}
                """, false, true)));
        assertThat(result)
                .contains("Mapped: data_recipientEmailAddress → custom attribute label-52 "
                        + "[295e8f4e-b300-4f19-a8f4-a7cecb9da498]");
    }

    @Test
    void ilmRefusalComesBackAsText() {
        stubFor(post(urlEqualTo(INSTANCES))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"message\":\"Object of type 'NotificationInstanceReference' identified by alerts already exists.\"}")));

        assertThat(createEmail(TEMPLATE, null)).startsWith("Error creating notification instance: ILM answered 409");
    }

    @Test
    void secretFieldsAreRefused() {
        stubFor(get(urlEqualTo("/v1/connectors/" + EMAIL_CONNECTOR + "/attributes/notificationProvider/EMAIL"))
                .willReturn(okJson(fixture("connector-attributes-email")
                        .replace("\"contentType\": \"string\"", "\"contentType\": \"secret\""))));

        assertThat(createEmail("{\"data_senderEmailAddress\":\"x\",\"data_emailContentTemplate\":\"t\"}", null))
                .isEqualTo("Field data_senderEmailAddress has content type secret, which this tool cannot fill.");
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"data_emailContentTemplate\":null}|Field data_emailContentTemplate has no value; leave it out to use the connector's default.",
            "{\"data_emailSubject\":null,\"data_emailContentTemplate\":\"t\"}|Field data_emailSubject has no value; leave it out to use the connector's default.",
            "{\"data_emailContentTemplate\":{\"code\":\"<p>x</p>\",\"language\":\"html\"}}|Field data_emailContentTemplate takes plain text."})
    void valuesMustHaveAUsableShape(String attributes, String message) {
        assertThat(createEmail(attributes, null)).isEqualTo(message);
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "' '|EMAIL|{\"data_emailContentTemplate\":\"t\"}||A notification instance needs a name.",
            "alerts|EMAIL|{\"data_emailContentTemplate\":\"t\",\"data_unknown\":\"x\"}||Unknown fields for kind EMAIL: data_unknown. get_notification_connector_settings lists them.",
            "alerts|WEBHOOK|{\"data_webhookUrl\":\"https://example.com/h\",\"data_contentType\":\"YAML\"}||data_contentType must be one of RAW_JSON, JSON, XML.",
            "alerts|WEBHOOK|{\"data_webhookUrl\":\"https://example.com/h\"}||group_contentTemplate needs data_contentType. get_notification_connector_settings lists the choices.",
            "alerts|EMAIL|{\"data_emailContentTemplate\":\"t\"}|{\"data_other\":\"name-51\"}|Unknown mappable attributes: data_other. Mappable: data_recipientEmailAddress."})
    void invalidValuesAreRefused(String name, String kind, String attributes, String mappings, String message) {
        String connector = "EMAIL".equals(kind) ? EMAIL_CONNECTOR : WEBHOOK_CONNECTOR;

        assertThat(service
                .createNotificationInstance(
                        new NotificationInstanceInput(name, connector, kind, null, attributes, mappings)))
                .isEqualTo(message);
        verify(0, postRequestedFor(urlEqualTo(INSTANCES)));
        verify(0, postRequestedFor(urlPathMatching(".*/callback")));
    }

    @Test
    void deleteNotificationInstanceDeletesIt() {
        stubFor(get(urlEqualTo(INSTANCES)).willReturn(okJson(fixture("notification-instances"))));
        stubFor(delete(urlEqualTo(INSTANCES + "/" + EMAIL_INSTANCE)).willReturn(noContent()));

        assertThat(service.deleteNotificationInstance(EMAIL_INSTANCE))
                .isEqualTo("Deleted notification instance name-1 [" + EMAIL_INSTANCE + "].");
        verify(deleteRequestedFor(urlEqualTo(INSTANCES + "/" + EMAIL_INSTANCE)));
    }

    @Test
    void deleteOfAUsedInstanceSaysWhichProfilesUseIt() {
        stubFor(get(urlEqualTo(INSTANCES)).willReturn(okJson(fixture("notification-instances"))));
        stubFor(delete(urlEqualTo(INSTANCES + "/" + EMAIL_INSTANCE))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"message\":\"Cannot delete notification instance. Notification profile(s) referencing "
                                        + "this notification instance: name-5\"}")));

        assertThat(service.deleteNotificationInstance(EMAIL_INSTANCE))
                .contains("Notification profile(s) referencing this notification instance: name-5",
                        "Delete or change those profiles first; delete_notification_profile deletes one.");
    }

    @Test
    void deleteFailingForAnotherReasonHasNoHint() {
        stubFor(get(urlEqualTo(INSTANCES)).willReturn(okJson(fixture("notification-instances"))));
        stubFor(delete(urlEqualTo(INSTANCES + "/" + EMAIL_INSTANCE)).willReturn(aResponse().withStatus(500)));

        assertThat(service.deleteNotificationInstance(EMAIL_INSTANCE))
                .startsWith("Error deleting notification instance: ILM answered 500")
                .doesNotContain("Delete or change those profiles first");
    }

    @Test
    void deleteOfAnUnknownInstanceDeletesNothing() {
        stubFor(get(urlEqualTo(INSTANCES)).willReturn(okJson(fixture("notification-instances"))));

        assertThat(service.deleteNotificationInstance("00000000-0000-4000-8000-000000000599"))
                .isEqualTo("No notification instance [00000000-0000-4000-8000-000000000599]; "
                        + "list_notification_instances lists them.");
        verify(0, deleteRequestedFor(urlPathMatching(".*")));
    }

    private String createEmail(String attributes, String mappings) {
        return service
                .createNotificationInstance(
                        new NotificationInstanceInput("alerts", EMAIL_CONNECTOR, "EMAIL", null, attributes, mappings));
    }

    private String createWebhook(String attributes) {
        return service
                .createNotificationInstance(
                        new NotificationInstanceInput("hook", WEBHOOK_CONNECTOR, "WEBHOOK", null, attributes, null));
    }

    private static void stubCreated() {
        stubFor(post(urlEqualTo(INSTANCES))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"uuid\":\"" + NEW_INSTANCE + "\"}")));
    }
}
