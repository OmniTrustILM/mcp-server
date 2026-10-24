package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AutomationMaintenanceTool;
import com.otilm.mcp.tool.NotificationMaintenanceTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AutomationUpdateTest {
    private static final String T = "11111111-1111-4111-8111-111111111111";
    private static final String C = "22222222-2222-4222-8222-222222222222";
    private static final String E = "33333333-3333-4333-8333-333333333333";
    private static final String A = "44444444-4444-4444-8444-444444444444";
    private AutomationMaintenanceTool tool;
    private NotificationMaintenanceTool notifications;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        AutomationMaintenanceService service = new AutomationMaintenanceServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                IlmTestSupport.client(wm), new PlatformVersionCheck(IlmTestSupport.client(wm)));
        tool = new AutomationMaintenanceTool(service);
        notifications = new NotificationMaintenanceTool(service);
        IlmTestSupport.stubIlmVersion("2.20.0");
    }

    @Test
    void notificationInstancePreservesReadableDefaultsMappingsAndRequiresFreshProtectedValues() {
        stubFor(get(urlEqualTo("/v1/notificationInstances/" + T)).willReturn(okJson("""
                {"uuid":"%s","description":"Original","connectorUuid":"%s","kind":"EMAIL",
                "attributes":[{"name":"sender","content":[{"data":"sender@example.test"}]}],
                "attributeMappings":[{"mappingAttributeName":"recipient","customAttributeUuid":"%s"}]}
                """.formatted(T, C, A))));
        stubFor(get(urlEqualTo("/v1/connectors/" + C + "/attributes/notificationProvider/EMAIL")).willReturn(okJson("""
                [{"name":"sender","version":2,"type":"data","contentType":"string","properties":{}}]
                """)));
        stubFor(put(urlEqualTo("/v1/notificationInstances/" + T))
                .willReturn(okJson("{\"attributes\":[{\"data\":\"SENTINEL\"}]}")));
        assertThat(notifications.updateNotificationInstance(T, "{\"description\":\"Revised\"}"))
                .contains(T)
                .doesNotContain("SENTINEL");
        verify(putRequestedFor(urlEqualTo("/v1/notificationInstances/" + T))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("sender@example.test")))
                .withRequestBody(matchingJsonPath("$.attributeMappings[0].customAttributeUuid", equalTo(A))));
    }

    @Test
    void profileUpdatePreservesRecipientsTransportAndActualVersion() {
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + T))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","name":"Ops","description":"Original","version":5,"recipientType":"user",
                                "recipients":[{"uuid":"%s"}],"notificationInstance":{"uuid":"%s"},"internalNotification":true,"frequency":"PT24H","repetitions":3}
                                """
                                .formatted(T, A, C))));
        stubFor(put(urlEqualTo("/v1/notificationProfiles/" + T)).willReturn(okJson("{\"uuid\":\"" + T + "\"}")));
        assertThat(notifications.updateNotificationProfile(T, "{\"description\":\"Revised\"}"))
                .contains(T, "version", "5");
        verify(putRequestedFor(urlEqualTo("/v1/notificationProfiles/" + T))
                .withRequestBody(
                        equalToJson("{\"description\":\"Revised\",\"recipientType\":\"user\",\"recipientUuids\":[\"" + A
                                + "\"],\"notificationInstanceUuid\":\"" + C
                                + "\",\"internalNotification\":true,\"frequency\":\"PT24H\",\"repetitions\":3}")));
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(notifications.updateNotificationProfile(T, "{\"eventDataCategories\":[\"metadata\"]}"))
                .contains("2.20");
        verify(1, putRequestedFor(urlEqualTo("/v1/notificationProfiles/" + T)));
    }

    @Test
    void wholeWorkflowUpdateKeepsOriginalUuidAndSharedPartsAndNeverCreatesAnotherTrigger() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","name":"Ops","description":"Existing","type":"event","resource":"certificates","event":"certificate_expiring",
                                "rules":[],"actions":[{"uuid":"%s","name":"Shared action","executions":[]}]}
                                """
                                .formatted(T, C))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + C)).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/executions")).willReturn(okJson("{\"uuid\":\"" + E + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/actions")).willReturn(okJson("{\"uuid\":\"" + A + "\"}")));
        stubFor(put(urlEqualTo("/v1/workflows/triggers/" + T)).willReturn(okJson("{\"uuid\":\"" + T + "\"}")));
        assertThat(tool
                .updateTrigger(T,
                        "{\"replaceComposition\":true,\"conditions\":[],\"setFields\":[],\"notificationProfileUuid\":\""
                                + C + "\",\"ignore\":false}"))
                .contains(T, "associations", "retained");
        verify(putRequestedFor(urlEqualTo("/v1/workflows/triggers/" + T))
                .withRequestBody(matchingJsonPath("$.actionsUuids[0]", equalTo(A))));
        verify(0, postRequestedFor(urlEqualTo("/v1/workflows/triggers")));
        verify(0, deleteRequestedFor(urlEqualTo("/v1/workflows/actions/" + C)));
    }

    @Test
    void failedWorkflowPutRestoresReferencesAndCleansOnlyNewlyCreatedParts() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","name":"Ops","description":"Existing","type":"event","resource":"certificates","event":"certificate_expiring","rules":[],"actions":[]}
                                """
                                .formatted(T))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + C)).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/executions")).willReturn(okJson("{\"uuid\":\"" + E + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/actions")).willReturn(okJson("{\"uuid\":\"" + A + "\"}")));
        stubFor(put(urlEqualTo("/v1/workflows/triggers/" + T))
                .withRequestBody(matchingJsonPath("$.actionsUuids", equalToJson("[\"" + A + "\"]")))
                .willReturn(serverError()));
        stubFor(put(urlEqualTo("/v1/workflows/triggers/" + T))
                .withRequestBody(matchingJsonPath("$.actionsUuids", equalToJson("[]")))
                .willReturn(okJson("{}")));
        stubFor(delete(urlEqualTo("/v1/workflows/actions/" + A)).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/workflows/executions/" + E)).willReturn(noContent()));
        assertThat(tool
                .updateTrigger(T,
                        "{\"replaceComposition\":true,\"conditions\":[],\"setFields\":[],\"notificationProfileUuid\":\""
                                + C + "\",\"ignore\":false}"))
                .contains("Restored", "Removed", T);
        verify(deleteRequestedFor(urlEqualTo("/v1/workflows/actions/" + A)));
        verify(deleteRequestedFor(urlEqualTo("/v1/workflows/executions/" + E)));
        verify(0, deleteRequestedFor(urlEqualTo("/v1/workflows/triggers/" + T)));
    }

    @Test
    void protectedNotificationValuesFailBeforeMutationAndMappingTargetsAreResolved() {
        stubFor(get(urlEqualTo("/v1/notificationInstances/" + T))
                .willReturn(
                        okJson("""
                                {"connectorUuid":"%s","kind":"EMAIL","attributes":[{"name":"pin","content":[{"data":{"secret":"********","protectionLevel":"encrypted"}}]}]}
                                """
                                .formatted(C))));
        stubFor(get(urlEqualTo("/v1/connectors/" + C + "/attributes/notificationProvider/EMAIL")).willReturn(okJson("""
                [{"name":"pin","version":2,"type":"data","contentType":"secret","properties":{}}]
                """)));
        assertThat(notifications.updateNotificationInstance(T, "{\"description\":\"Revised\"}"))
                .contains("pin", "fresh");
        verify(0, putRequestedFor(anyUrl()));
        stubFor(get(urlEqualTo("/v1/notificationInstances/attributes/mapping/" + C + "/EMAIL"))
                .willReturn(okJson("[{\"uuid\":\"" + E + "\",\"name\":\"recipient\"}]")));
        stubFor(get(urlEqualTo("/v1/attributes/custom"))
                .willReturn(okJson("[{\"uuid\":\"" + A + "\",\"name\":\"Contact\",\"label\":\"Contact address\"}]")));
        stubFor(put(urlEqualTo("/v1/notificationInstances/" + T)).willReturn(okJson("{}")));
        assertThat(notifications
                .updateNotificationInstance(T,
                        "{\"attributes\":{\"pin\":{\"secret\":\"fresh-pin\",\"protectionLevel\":\"none\"}},\"attributeMappings\":{\"recipient\":\"Contact\"}}"))
                .contains(T)
                .doesNotContain("fresh-pin");
        verify(putRequestedFor(urlEqualTo("/v1/notificationInstances/" + T))
                .withRequestBody(matchingJsonPath("$.attributeMappings[0].mappingAttributeUuid", equalTo(E)))
                .withRequestBody(matchingJsonPath("$.attributeMappings[0].customAttributeUuid", equalTo(A))));
    }

    @Test
    void metadataOnlyTriggerUpdateWorksOn217WithoutUnsupportedNameField() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(okJson("{\"uuid\":\"" + T
                        + "\",\"name\":\"Ops\",\"type\":\"event\",\"resource\":\"certificates\",\"event\":\"certificate_expiring\",\"rules\":[],\"actions\":[]}")));
        stubFor(put(urlEqualTo("/v1/workflows/triggers/" + T)).willReturn(okJson("{}")));
        assertThat(tool.updateTrigger(T, "{\"description\":\"Revised\"}")).contains(T, "retained");
        verify(putRequestedFor(urlEqualTo("/v1/workflows/triggers/" + T)).withRequestBody(notMatching(".*\"name\".*")));
        assertThat(tool.updateTrigger(T, "{\"name\":\"Renamed\"}")).contains("2.18");
    }

    @Test
    void cleanupFailureReportsOwnedIdentifiersWithoutEchoingUpstreamBodies() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(okJson("{\"uuid\":\"" + T
                        + "\",\"name\":\"Ops\",\"type\":\"event\",\"resource\":\"certificates\",\"event\":\"certificate_expiring\",\"rules\":[],\"actions\":[]}")));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + C)).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/executions")).willReturn(okJson("{\"uuid\":\"" + E + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/actions")).willReturn(serverError()));
        stubFor(delete(urlEqualTo("/v1/workflows/executions/" + E))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"SENTINEL\"}")));
        assertThat(tool
                .updateTrigger(T,
                        "{\"replaceComposition\":true,\"conditions\":[],\"setFields\":[],\"notificationProfileUuid\":\""
                                + C + "\",\"ignore\":false}"))
                .contains("not changed", E)
                .doesNotContain("SENTINEL");
        verify(0, putRequestedFor(urlEqualTo("/v1/workflows/triggers/" + T)));
    }

    @Test
    void notificationCallbacksCanReadOmittedExistingReadableDependencies() {
        stubFor(get(urlEqualTo("/v1/notificationInstances/" + T))
                .willReturn(
                        okJson("""
                                {"connectorUuid":"%s","kind":"EMAIL","attributes":[{"name":"sender","content":[{"data":"sender@example.test"}]}]}
                                """
                                .formatted(C))));
        stubFor(get(urlEqualTo("/v1/connectors/" + C + "/attributes/notificationProvider/EMAIL"))
                .willReturn(
                        okJson("""
                                [{"name":"sender","version":2,"type":"data","contentType":"string","properties":{}},
                                {"name":"callback","type":"group","attributeCallback":{"callbackContext":"options","callbackMethod":"GET", "mappings":[{"from":"sender","to":"sender","targets":["requestParameter"]}]}}]
                                """)));
        stubFor(post(urlEqualTo("/v1/connectors/" + C + "/notificationProvider/EMAIL/callback"))
                .willReturn(okJson(
                        "[{\"name\":\"resolved\",\"version\":2,\"type\":\"data\",\"contentType\":\"string\",\"properties\":{},\"content\":[{\"data\":\"resolved-choice\"}]}]")));
        stubFor(put(urlEqualTo("/v1/notificationInstances/" + T)).willReturn(okJson("{}")));
        assertThat(notifications.updateNotificationInstance(T, "{\"description\":\"Revised\"}")).contains(T);
        verify(postRequestedFor(urlEqualTo("/v1/connectors/" + C + "/notificationProvider/EMAIL/callback"))
                .withRequestBody(matchingJsonPath("$.requestParameter.sender", equalTo("sender@example.test"))));
        verify(putRequestedFor(urlEqualTo("/v1/notificationInstances/" + T))
                .withRequestBody(matchingJsonPath("$.attributes[1].content[0].data", equalTo("resolved-choice"))));
    }

    @Test
    void failedRestorationRetainsNewObjectsForInspection() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(okJson("{\"uuid\":\"" + T
                        + "\",\"name\":\"Ops\",\"type\":\"event\",\"resource\":\"certificates\",\"event\":\"certificate_expiring\",\"rules\":[],\"actions\":[]}")));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + C)).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/executions")).willReturn(okJson("{\"uuid\":\"" + E + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/actions")).willReturn(okJson("{\"uuid\":\"" + A + "\"}")));
        stubFor(put(urlEqualTo("/v1/workflows/triggers/" + T)).willReturn(serverError()));
        assertThat(tool
                .updateTrigger(T,
                        "{\"replaceComposition\":true,\"conditions\":[],\"setFields\":[],\"notificationProfileUuid\":\""
                                + C + "\",\"ignore\":false}"))
                .contains("Could not confirm restoration", "retained", E, A, T);
        verify(0, deleteRequestedFor(anyUrl()));
    }

    @Test
    void successfulUpdateKeepsOwnedRuleChildrenWhenTheRuleIsStillShared() {
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + T))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","name":"Ops","type":"event","resource":"certificates","event":"certificate_expiring",
                                "rules":[{"uuid":"%s","name":"Ops","description":"Created with the ILM MCP server.",
                                  "conditions":[{"uuid":"%s","name":"Ops","description":"Created with the ILM MCP server."}]}],"actions":[]}
                                """
                                .formatted(T, C, E))));
        stubFor(get(urlEqualTo("/v1/notificationProfiles/" + C)).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/executions")).willReturn(okJson("{\"uuid\":\"" + E + "\"}")));
        stubFor(post(urlEqualTo("/v1/workflows/actions")).willReturn(okJson("{\"uuid\":\"" + A + "\"}")));
        stubFor(put(urlEqualTo("/v1/workflows/triggers/" + T)).willReturn(okJson("{}")));
        stubFor(delete(urlEqualTo("/v1/workflows/rules/" + C)).willReturn(aResponse().withStatus(422)));
        assertThat(tool
                .updateTrigger(T,
                        "{\"replaceComposition\":true,\"conditions\":[],\"setFields\":[],\"notificationProfileUuid\":\""
                                + C + "\",\"ignore\":false}"))
                .contains("Updated", T, "Kept", C);
        verify(0, deleteRequestedFor(urlEqualTo("/v1/workflows/conditions/" + E)));
    }

    @Test
    void notificationDetailOmitsPopulatedProviderValues() {
        stubFor(get(urlEqualTo("/v1/notificationInstances/" + T))
                .willReturn(okJson("{\"uuid\":\"" + T
                        + "\",\"name\":\"Email\",\"attributes\":[{\"name\":\"secret\",\"content\":[{\"data\":\"SENTINEL\"}]}]}")));
        assertThat(notifications.getNotificationInstance(T)).contains(T, "Email").doesNotContain("SENTINEL");
    }

}
