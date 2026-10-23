package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.CredentialConfigurationTool;
import com.otilm.mcp.tool.LocationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class ReplacementCallbackIntegrationTest {
    private static final String OBJECT = "1612bfd9-6e0f-4776-939c-c788423c5b67";
    private static final String PARENT = "fe812790-3cbc-4458-af7c-3775be4c6eda";
    private static final String CONNECTOR = "219b025b-729c-4c84-8233-49d75e607f7b";
    private static final String LOCATION_ENDPOINT = "/v1/entities/" + PARENT + "/locations/" + OBJECT;
    private static final String LOCATION_SCHEMA_ENDPOINT = "/v1/entities/" + PARENT + "/attributes/location";
    private static final String LOCATION_CALLBACK_ENDPOINT = "/v1/locations/" + PARENT + "/callback";
    private static final String EXISTING_POOL = "[{\"contentType\":\"string\",\"reference\":\"pool-blue\",\"data\":\"blue\"}]";
    private ConfigurationApiClient api;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        api = new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build());
        stubFor(get(urlEqualTo("/v1/info")).willReturn(okJson("{\"app\":{\"version\":\"2.20.0\"}}")));
    }

    @Test
    void credentialUpdatePreservesOmittedReadableCallbackDependencyAndFreshProtectedInput() {
        CredentialConfigurationTool tool = new CredentialConfigurationTool(new CredentialConfigurationServiceImpl(api));
        stubFor(get(urlEqualTo("/v1/credentials/" + OBJECT))
                .willReturn(
                        okJson("""
                                {"uuid":"1612bfd9-6e0f-4776-939c-c788423c5b67","connectorUuid":"219b025b-729c-4c84-8233-49d75e607f7b","kind":"API",
                                "attributes":[{"name":"region","content":[{"data":"eu"}]},
                                {"name":"password","content":[{"data":{"secret":"OLD_PROTECTED_SENTINEL"}}]}],"customAttributes":[]}
                                """)));
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR + "/attributes/credentialProvider/API"))
                .willReturn(okJson(schema(true))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/credentials")).willReturn(okJson("[]")));
        String callback = "/v1/connectors/" + CONNECTOR + "/credentialProvider/API/callback";
        callback(callback);
        stubFor(put(urlEqualTo("/v1/credentials/" + OBJECT)).willReturn(okJson("{\"uuid\":\"" + OBJECT + "\"}")));
        assertThat(tool
                .updateCredential(OBJECT,
                        "{\"attributes\":{\"size\":2048,\"password\":{\"secret\":\"FRESH_SENTINEL\"}}}"))
                .contains(OBJECT)
                .doesNotContain("FRESH_SENTINEL", "OLD_PROTECTED_SENTINEL");
        verify(postRequestedFor(urlEqualTo(callback))
                .withRequestBody(matchingJsonPath("$.body.zone", equalTo("eu")))
                .withRequestBody(notMatching(".*OLD_PROTECTED_SENTINEL.*")));
        verify(putRequestedFor(urlEqualTo("/v1/credentials/" + OBJECT))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("eu")))
                .withRequestBody(matchingJsonPath("$.attributes[1].content[0].data.secret", equalTo("FRESH_SENTINEL")))
                .withRequestBody(matchingJsonPath("$.attributes[2].content[0].data", equalTo("2048"))));
    }

    @Test
    void locationUpdatePreservesOmittedDependencyInResourceScopedCallback() {
        LocationTool tool = new LocationTool(new EntityLocationServiceImpl(api));
        String path = "/v1/entities/" + PARENT + "/locations/" + OBJECT;
        stubFor(get(urlEqualTo(path)).willReturn(okJson("""
                {"uuid":"1612bfd9-6e0f-4776-939c-c788423c5b67","description":"existing","enabled":true,
                "attributes":[{"name":"region","content":[{"data":"eu"}]}],"customAttributes":[]}
                """)));
        stubFor(get(urlEqualTo("/v1/entities/" + PARENT + "/attributes/location")).willReturn(okJson(schema(false))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/locations")).willReturn(okJson("[]")));
        String callback = "/v1/locations/" + PARENT + "/callback";
        callback(callback);
        stubFor(put(urlEqualTo(path)).willReturn(okJson("{\"uuid\":\"" + OBJECT + "\"}")));
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"attributes\":{\"size\":2048}}")).contains(OBJECT);
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(matchingJsonPath("$.body.zone", equalTo("eu"))));
        verify(putRequestedFor(urlEqualTo(path))
                .withRequestBody(matchingJsonPath("$.description", equalTo("existing")))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("eu")))
                .withRequestBody(matchingJsonPath("$.attributes[1].content[0].data", equalTo("2048"))));
    }

    @Test
    void locationMetadataUpdateRefreshesChoicesBeforeRetainingAnOmittedListSelection() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, "", false);
        stubPoolChoices(EXISTING_POOL);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains(OBJECT);
        verify(postRequestedFor(urlEqualTo(LOCATION_CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.requestParameter.region", equalTo("eu"))));
        verify(putRequestedFor(urlEqualTo(LOCATION_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.description", equalTo("updated")))
                .withRequestBody(matchingJsonPath("$.attributes[1].content", equalToJson(EXISTING_POOL))));
    }

    @Test
    void locationMetadataUpdateRefusesAnExistingSelectionRemovedByTheCallback() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, "", false);
        stubPoolChoices("[{\"contentType\":\"string\",\"reference\":\"pool-green\",\"data\":\"green\"}]");
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains("pool", "offered");
        verify(postRequestedFor(urlEqualTo(LOCATION_CALLBACK_ENDPOINT)));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void locationReplacementRefusesARemovedReferenceEvenWhenAnotherOptionHasTheSameData() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, "", false);
        stubPoolChoices("[{\"contentType\":\"string\",\"reference\":\"pool-replacement\",\"data\":\"blue\"}]");
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains("pool", "offered");
        verify(postRequestedFor(urlEqualTo(LOCATION_CALLBACK_ENDPOINT)));
        verify(0, putRequestedFor(anyUrl()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void locationReplacementNeverTreatsARemovedReferenceAsOfferedObjectData(boolean extensibleList) {
        LocationTool tool = locationWithExistingPool(
                "[{\"contentType\":\"object\",\"reference\":\"pool-old\",\"data\":{\"value\":\"old\"}}]",
                ",\"extensibleList\":" + extensibleList, false, "object");
        stubPoolChoices("""
                [{"contentType":"object","reference":"pool-new","data":{"reference":"pool-old"}}]
                """);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains("pool", "offered");
        verify(postRequestedFor(urlEqualTo(LOCATION_CALLBACK_ENDPOINT)));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void locationReplacementRetainsTheSelectedReferenceWithRefreshedOfferedData() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, "", false);
        String refreshed = "[{\"contentType\":\"string\",\"reference\":\"pool-blue\",\"data\":\"renamed-blue\"}]";
        stubPoolChoices(refreshed);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains(OBJECT);
        verify(putRequestedFor(urlEqualTo(LOCATION_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.attributes[1].content", equalToJson(refreshed))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"********\"", "123"})
    void locationReplacementRefusesMaskedOrIncorrectlyTypedListDataBeforeCallbacks(String data) {
        LocationTool tool = locationWithExistingPool("[{\"reference\":\"pool-blue\",\"data\":" + data + "}]", "",
                false);
        stubPoolChoices(EXISTING_POOL);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains("pool");
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void locationReplacementRequiresFreshProtectedListDataBeforeCallbacks() {
        LocationTool tool = locationWithExistingPool(
                "[{\"reference\":\"pool-blue\",\"data\":\"OLD_PROTECTED_SENTINEL\"}]",
                ",\"protectionLevel\":\"encrypted\"", false);
        stubPoolChoices(EXISTING_POOL);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}"))
                .contains("pool", "fresh")
                .doesNotContain("OLD_PROTECTED_SENTINEL");
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void locationReplacementRefusesExcessExistingSelectionsBeforeCallbacks() {
        LocationTool tool = locationWithExistingPool("""
                [{"reference":"pool-blue","data":"blue"},{"reference":"pool-green","data":"green"}]
                """, "", false);
        stubPoolChoices(EXISTING_POOL);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains("pool", "one value");
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void locationReplacementDoesNotReplayReadOnlyCallbackSelections() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, ",\"readOnly\":true", false);
        stubPoolChoices(EXISTING_POOL);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains("pool", "read-only");
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void locationReplacementResolvesAnEarlierConsumerAfterItsListSourceChoices() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, "", true);
        stubPoolChoices(EXISTING_POOL);
        stubFor(post(urlEqualTo(LOCATION_CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("details")))
                .withRequestBody(matchingJsonPath("$.body.selectedReference", equalTo("pool-blue")))
                .willReturn(okJson("[]")));
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"description\":\"updated\"}")).contains(OBJECT);
        verify(postRequestedFor(urlEqualTo(LOCATION_CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("details")))
                .withRequestBody(matchingJsonPath("$.body.selectedReference", equalTo("pool-blue"))));
        verify(putRequestedFor(urlEqualTo(LOCATION_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.attributes[1].content", equalToJson(EXISTING_POOL))));
    }

    @Test
    void locationReplacementClearsAnOptionalSelectionWithoutReplayingItOrDroppingOtherValues() {
        LocationTool tool = locationWithExistingPool(EXISTING_POOL, "", false);
        stubFor(get(urlEqualTo(LOCATION_ENDPOINT)).willReturn(okJson("""
                {"uuid":"1612bfd9-6e0f-4776-939c-c788423c5b67","description":"existing","enabled":true,
                 "attributes":[{"name":"region","version":"v2","contentType":"string","content":[{"data":"eu"}]},
                  {"name":"pool","version":"v3","contentType":"string","content":%s}],
                 "customAttributes":[{"name":"department","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"department-ref","data":"Compliance"}]}]}
                """.formatted(EXISTING_POOL))));
        stubFor(get(urlEqualTo(LOCATION_SCHEMA_ENDPOINT)).willReturn(okJson("""
                [{"name":"region","type":"data","version":2,"contentType":"string","properties":{}},
                 {"name":"pool","type":"data","version":3,"contentType":"string",
                  "properties":{"list":true},"content":[],
                  "attributeCallback":{"callbackContext":"pools","callbackMethod":"GET",
                   "mappings":[{"from":"region","to":"region","targets":["requestParameter"]}]}}]
                """)));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/locations")).willReturn(okJson("""
                [{"name":"department","type":"custom","version":3,"contentType":"string","properties":{}}]
                """)));
        stubPoolChoices(EXISTING_POOL);
        assertThat(tool.updateLocation(PARENT, OBJECT, "{\"attributes\":{\"pool\":[]}}")).contains(OBJECT);
        verify(putRequestedFor(urlEqualTo(LOCATION_ENDPOINT)).withRequestBody(equalToJson("""
                {"description":"existing","enabled":true,
                 "attributes":[{"name":"region","version":"v2","contentType":"string","content":[{"data":"eu"}]}],
                 "customAttributes":[{"name":"department","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"department-ref","data":"Compliance"}]}]}
                """)));
    }

    private LocationTool locationWithExistingPool(String content, String additionalProperties, boolean consumerFirst) {
        return locationWithExistingPool(content, additionalProperties, consumerFirst, "string");
    }

    private LocationTool locationWithExistingPool(String content, String additionalProperties, boolean consumerFirst,
            String contentType) {
        stubFor(get(urlEqualTo(LOCATION_ENDPOINT)).willReturn(okJson("""
                {"uuid":"1612bfd9-6e0f-4776-939c-c788423c5b67","description":"existing","enabled":true,
                 "attributes":[{"name":"region","version":"v2","contentType":"string","content":[{"data":"eu"}]},
                  {"name":"pool","version":"v3","contentType":"%s","content":%s}],"customAttributes":[]}
                """.formatted(contentType, content))));
        String consumer = consumerFirst
                ? """
                        {"name":"details","type":"group","attributeCallback":{"callbackContext":"details","callbackMethod":"GET",
                         "mappings":[{"from":"pool.reference","to":"selectedReference","targets":["body"]}]}},
                        """
                : "";
        stubFor(get(urlEqualTo(LOCATION_SCHEMA_ENDPOINT)).willReturn(okJson("""
                [{"name":"region","type":"data","version":2,"contentType":"string","properties":{"label":"Region"}},
                 %s{"name":"pool","type":"data","version":3,"contentType":"%s",
                 "properties":{"label":"Pool","list":true,"required":true%s},"content":[],
                 "attributeCallback":{"callbackContext":"pools","callbackMethod":"GET",
                  "mappings":[{"from":"region","to":"region","targets":["requestParameter"]}]}}]
                """.formatted(consumer, contentType, additionalProperties))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/locations")).willReturn(okJson("[]")));
        stubFor(put(urlEqualTo(LOCATION_ENDPOINT)).willReturn(okJson("{\"uuid\":\"" + OBJECT + "\"}")));
        return new LocationTool(new EntityLocationServiceImpl(api));
    }

    private static void stubPoolChoices(String choices) {
        stubFor(post(urlEqualTo(LOCATION_CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("pool")))
                .willReturn(okJson(choices)));
    }

    private static void callback(String path) {
        stubFor(post(urlEqualTo(path)).willReturn(okJson("""
                [{"name":"size","type":"data","version":3,"contentType":"integer","properties":{}}]
                """)));
    }

    private static String schema(boolean protectedField) {
        return """
                [{"name":"region","type":"data","version":2,"contentType":"string","properties":{}}
                """ + (protectedField
                ? ",{\"name\":\"password\",\"type\":\"data\",\"version\":2,\"contentType\":\"secret\",\"properties\":{}}"
                : "") + """
                        ,{"name":"providerFields","type":"group","attributeCallback":{"callbackContext":"schema",
                        "mappings":[{"from":"region","to":"zone","targets":["body"]}]}}]
                        """;
    }
}
