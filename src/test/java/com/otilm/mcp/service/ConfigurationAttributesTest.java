package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@WireMockTest
class ConfigurationAttributesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CONNECTOR = "00000000-0000-4000-8000-000000000501";
    private static final String ATTRIBUTE = "00000000-0000-4000-8000-000000000601";
    private static final String CALLBACK = "/v1/connectors/" + CONNECTOR + "/notificationProvider/EMAIL/callback";
    private ConfigurationApiClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo info) {
        client = new ConfigurationApiClient(IlmApiClient.restClientBuilder(info.getHttpBaseUrl()).build());
    }

    @Test
    void requiredReadOnlyDefaultsAreSentWithTheirReferencesAndVersionTags() {
        JsonNode definitions = json("""
                [{"uuid":"00000000-0000-4000-8000-000000000601","name":"fixed","type":"data","version":2,
                  "contentType":"string","properties":{"readOnly":true,"required":true},
                  "content":[{"reference":"provider-fixed","data":"declared"}]},
                 {"name":"customFixed","type":"custom","version":3,"contentType":"object",
                  "properties":{"readOnly":true},"content":[{"reference":"custom-ref","data":{"value":7}}]}]
                """);
        JsonNode encoded = ConfigurationAttributes.encode(definitions, "{}");
        stubFor(post(urlEqualTo("/test-write")).willReturn(okJson("{}")));
        client.post("/test-write", encoded);
        verify(postRequestedFor(urlEqualTo("/test-write")).withRequestBody(equalToJson("""
                [{"uuid":"00000000-0000-4000-8000-000000000601","name":"fixed","version":"v2","contentType":"string",
                  "content":[{"reference":"provider-fixed","data":"declared"}]},
                 {"name":"customFixed","version":"v3","contentType":"object",
                  "content":[{"contentType":"object","reference":"custom-ref","data":{"value":7}}]}]
                """)));
    }

    @Test
    void optionalReadOnlyReplacementUsesDeclaredContentRatherThanStaleExistingContent() {
        JsonNode definitions = json("""
                [{"name":"fixed","type":"data","version":2,"contentType":"string","properties":{"readOnly":true},
                  "content":[{"reference":"current-ref","data":"current"}]}]
                """);
        JsonNode existing = json("""
                [{"name":"fixed","content":[{"reference":"stale-ref","data":"stale"}]}]
                """);
        assertThat(ConfigurationAttributes.replace(definitions, existing, "{}"))
                .isEqualTo(
                        json("""
                                [{"name":"fixed","version":"v2","contentType":"string","content":[{"reference":"current-ref","data":"current"}]}]
                                """));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.replace(definitions, existing, "{\"fixed\":\"override\"}"))
                .withMessageContaining("read-only");
    }

    @Test
    void unsafeReadOnlyDefinitionsFailBeforeMutation() {
        for (String field : new String[]{
                "{\"name\":\"fixed\",\"type\":\"data\",\"contentType\":\"secret\",\"properties\":{\"readOnly\":true},\"content\":[{\"data\":{\"secret\":\"DO_NOT_REPLAY\"}}]}",
                "{\"name\":\"fixed\",\"type\":\"data\",\"contentType\":\"string\",\"properties\":{\"readOnly\":true},\"content\":[{\"data\":\"********\"}]}",
                "{\"name\":\"fixed\",\"type\":\"data\",\"contentType\":\"string\",\"properties\":{\"readOnly\":true}}",
                "{\"name\":\"fixed\",\"type\":\"data\",\"contentType\":\"string\",\"properties\":{\"readOnly\":true,\"list\":true},\"content\":[{\"data\":\"fixed\"}]}",
                "{\"name\":\"fixed\",\"type\":\"data\",\"contentType\":\"string\",\"properties\":{\"readOnly\":true},\"attributeCallback\":{},\"content\":[{\"data\":\"fixed\"}]}"}) {
            JsonNode definitions = json("[" + field + "]");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> client.post("/test-write", ConfigurationAttributes.encode(definitions, "{}")))
                    .withMessageNotContaining("DO_NOT_REPLAY");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> client
                            .post("/test-write", ConfigurationAttributes
                                    .replace(definitions, json("[{\"name\":\"fixed\",\"content\":[]}]"), "{}")));
        }
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void codeTextWithoutSupportedSchemaLanguageFailsBeforeMutation() {
        for (JsonNode definitions : new JsonNode[]{schema("codeblock", "{}"), json("""
                [{"name":"value","type":"data","contentType":"codeblock","properties":{},
                  "content":[{"data":{"language":"plain"}}]}]
                """)}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> client
                            .post("/test-write", ConfigurationAttributes.encode(definitions, "{\"value\":\"text\"}")));
        }
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void explicitCodeLanguageAndBase64MustMatchReleasedContract() {
        for (String data : new String[]{
                "{\"language\":\"plain\",\"code\":\"e30=\"}",
                "{\"language\":\"JSON\",\"code\":\"e30=\"}",
                "{\"language\":\"json\",\"code\":\"not base64!\"}"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> client
                            .post("/test-write", ConfigurationAttributes
                                    .encode(schema("codeblock", "{}"), "{\"value\":" + data + "}")));
        }
        assertThat(ConfigurationAttributes
                .encode(schema("codeblock", "{}"), "{\"value\":{\"language\":\"xquery\",\"code\":\"e30=\"}}"))
                .isEqualTo(
                        json("""
                                [{"name":"value","version":"v3","contentType":"codeblock","content":[{"contentType":"codeblock","data":{"language":"xquery","code":"e30="}}]}]
                                """));
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void baselineInterfaceCallbacksOmitFieldsAddedInNineteen() {
        stubFor(get(urlEqualTo("/v1/info")).willReturn(okJson("{\"app\":{\"version\":\"2.17.0\"}}")));
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback")).willReturn(okJson("[]")));
        JsonNode definitions = JSON
                .readTree(
                        """
                                [{"uuid":"00000000-0000-4000-8000-000000000601","name":"option","type":"data","contentType":"string","version":2,
                                  "properties":{"list":true},"attributeCallback":{"callbackContext":"options","mappings":[]}}]
                                """);
        ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR,
                        "00000000-0000-4000-8000-000000000602"), definitions, "{}");
        verify(postRequestedFor(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .withRequestBody(notMatching(".*interfaceUuid.*"))
                .withRequestBody(notMatching(".*attributes.*")));
    }

    @Test
    void existingProtectedRecordNeedsFreshInputEvenWhenRedactionRemovesContent() {
        JsonNode definitions = JSON.readTree("""
                [{"name":"password","type":"data","version":2,"contentType":"secret","properties":{}}]
                """);
        JsonNode existing = JSON.readTree("""
                [{"name":"password","contentType":"secret","content":[]}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.replace(definitions, existing, "{}"))
                .withMessageContaining("fresh");
    }

    @Test
    void preservesVersionCompleteDefaultsAndOfferedReferencesOnTheWire() {
        JsonNode definitions = json("""
                [{"uuid":"00000000-0000-4000-8000-000000000601","name":"choice","version":2,
                  "type":"data","contentType":"string","properties":{"list":true,"required":true},
                  "content":[{"reference":"provider-choice","data":"RSA"}]},
                 {"name":"defaulted","version":3,"schemaVersion":"v3","type":"data","contentType":"object",
                  "properties":{},"content":[{"contentType":"object","reference":"default-ref","data":{"count":2}}]}]
                """);
        JsonNode encoded = ConfigurationAttributes.encode(definitions, "{\"choice\":\"RSA\"}");
        stubFor(post(urlEqualTo("/test-write")).willReturn(okJson("{}")));
        client.post("/test-write", encoded);
        verify(postRequestedFor(urlEqualTo("/test-write")).withRequestBody(equalToJson("""
                [{"uuid":"00000000-0000-4000-8000-000000000601","name":"choice","version":"v2",
                  "contentType":"string","content":[{"reference":"provider-choice","data":"RSA"}]},
                 {"name":"defaulted","version":"v3","contentType":"object",
                  "content":[{"contentType":"object","reference":"default-ref","data":{"count":2}}]}]
                """)));
    }

    @Test
    void encodesV2SecretFileCredentialAndPlainCodeText() {
        JsonNode definitions = json("""
                [{"name":"secret","version":2,"type":"data","contentType":"secret","properties":{}},
                 {"name":"file","version":2,"type":"data","contentType":"file","properties":{}},
                 {"name":"credential","version":2,"type":"data","contentType":"credential","properties":{}},
                 {"name":"code","version":2,"type":"data","contentType":"codeblock","properties":{},
                  "content":[{"data":{"language":"json"}}]}]
                """);
        JsonNode encoded = ConfigurationAttributes
                .encode(definitions,
                        """
                                {"secret":{"secret":"synthetic-value","protectionLevel":"none"},
                                 "file":{"content":"aGVsbG8=","fileName":"sample.txt","mimeType":"text/plain"},
                                 "credential":{"uuid":"00000000-0000-4000-8000-000000000701","name":"basic","kind":"Basic","attributes":[]},
                                 "code":"{}"}
                                """);
        assertThat(encoded)
                .isEqualTo(
                        json("""
                                [{"name":"secret","version":"v2","contentType":"secret","content":[{"data":{"secret":"synthetic-value","protectionLevel":"none"}}]},
                                 {"name":"file","version":"v2","contentType":"file","content":[{"data":{"content":"aGVsbG8=","fileName":"sample.txt","mimeType":"text/plain"}}]},
                                 {"name":"credential","version":"v2","contentType":"credential","content":[{"data":{"uuid":"00000000-0000-4000-8000-000000000701","name":"basic","kind":"Basic","attributes":[]}}]},
                                 {"name":"code","version":"v2","contentType":"codeblock","content":[{"data":{"code":"e30=","language":"json"}}]}]
                                """));
    }

    @Test
    void encodesV3ResourceAndObjectWithPerContentTypes() {
        JsonNode definitions = json(
                """
                        [{"name":"resource","version":3,"type":"data","contentType":"resource","properties":{"resource":"credentials"}},
                         {"name":"object","version":3,"type":"data","contentType":"object","properties":{}}]
                        """);
        assertThat(ConfigurationAttributes.encode(definitions, """
                {"resource":{"resource":"credentials","uuid":"00000000-0000-4000-8000-000000000701"},
                 "object":{"nested":{"value":true}}}
                """))
                .isEqualTo(
                        json("""
                                [{"name":"resource","version":"v3","contentType":"resource","content":[{"contentType":"resource","data":{"resource":"credentials","uuid":"00000000-0000-4000-8000-000000000701"}}]},
                                 {"name":"object","version":"v3","contentType":"object","content":[{"contentType":"object","data":{"nested":{"value":true}}}]}]
                                """));
    }

    @ParameterizedTest
    @CsvSource({
            "integer,3",
            "float,3.5",
            "boolean,true",
            "date,'\"2026-10-23\"'",
            "time,'\"10:12:13\"'",
            "datetime,'\"2026-10-23T10:12:13Z\"'"})
    void validatesAndPreservesScalarTypes(String type, String value) {
        assertThat(ConfigurationAttributes
                .encode(schema(type, "{}"), "{\"value\":" + value + "}")
                .get(0)
                .at("/content/0/data")).isEqualTo(json(value));
    }

    @ParameterizedTest
    @CsvSource({
            "integer,1.5",
            "integer,'\"1\"'",
            "boolean,'\"true\"'",
            "string,3",
            "date,'\"2026-02-30\"'",
            "time,'\"25:00:00\"'",
            "datetime,'\"2026-10-23\"'",
            "file,{}",
            "secret,{}",
            "credential,{}",
            "resource,{}"})
    void rejectsWrongTypesBeforeMutation(String type, String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> {
            JsonNode encoded = ConfigurationAttributes.encode(schema(type, "{}"), "{\"value\":" + value + "}");
            client.post("/test-write", encoded);
        });
        verify(0, postRequestedFor(urlEqualTo("/test-write")));
    }

    @Test
    void rejectsUnknownRequiredReadOnlyAndMultipleSingleValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.encode(schema("string", "{}"), "{\"other\":\"x\"}"))
                .withMessageContaining("other");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.encode(schema("string", "{\"required\":true}"), "{}"))
                .withMessageContaining("value");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes
                        .encode(schema("string", "{\"readOnly\":true}"), "{\"value\":\"x\"}"))
                .withMessageContaining("read-only");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes
                        .encode(schema("string", "{\"list\":true}"), "{\"value\":[\"x\",\"y\"]}"))
                .withMessageContaining("one");
    }

    @Test
    void refusesProtectedDefaultsAndMasksWithoutEchoingValues() {
        JsonNode definitions = json("""
                [{"name":"secret","version":2,"type":"data","contentType":"secret","properties":{"required":true},
                  "content":[{"data":{"secret":"synthetic-protected"}}]}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.encode(definitions, "{}"))
                .withMessageContaining("secret")
                .withMessageNotContaining("synthetic-protected");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> ConfigurationAttributes.encode(definitions, "{\"secret\":{\"secret\":\"********\"}}"));
    }

    @Test
    void settingsExposeSchemaMetadataAndNoProtectedOrArbitraryObjectContent() {
        JsonNode definitions = json(
                """
                        [{"name":"secret","type":"data","version":2,"contentType":"secret","properties":{"required":true},"content":[{"data":{"secret":"SENTINEL_SECRET"}}]},
                         {"name":"object","type":"data","version":3,"contentType":"object","properties":{},"content":[{"data":{"password":"SENTINEL_NESTED"}}]},
                         {"name":"file","type":"data","contentType":"file","properties":{},"content":[{"data":{"content":"SENTINEL_FILE"}}]},
                         {"name":"choice","type":"data","contentType":"string","properties":{"list":true},"content":[{"reference":"safe-ref","data":"RSA"}]}]
                        """);
        assertThat(ConfigurationAttributes.settings("Provider settings", definitions))
                .contains("Provider settings", "secret", "required", "version 3", "RSA")
                .doesNotContain("SENTINEL_SECRET", "SENTINEL_NESTED", "SENTINEL_FILE");
    }

    @Test
    void resolvesGroupDependenciesAndPreservesMappedPathsThroughFixedConnectorRoute() {
        JsonNode definitions = json(
                """
                        [{"name":"root","type":"data","contentType":"string","properties":{},"content":[{"data":"default"}]},
                         {"uuid":"00000000-0000-4000-8000-000000000601","name":"first","type":"group",
                          "attributeCallback":{"callbackContext":"provider/arbitrary/path","callbackMethod":"GET",
                           "mappings":[{"from":"root","to":"kind","targets":["pathVariable"]}]}},
                         {"name":"last","type":"group","attributeCallback":{"callbackContext":"provider/second","callbackMethod":"POST",
                           "mappings":[{"from":"middle.data.nested","to":"bodyValue","targets":["body"]}]}}]
                        """);
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.name", equalTo("first")))
                .willReturn(
                        okJson("""
                                [{"name":"middle","type":"data","contentType":"object","properties":{},"content":[{"data":{"nested":"ready"}}]}]
                                """)));
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.name", equalTo("last")))
                .willReturn(
                        okJson("""
                                [{"name":"finished","type":"data","contentType":"boolean","properties":{},"content":[{"data":true}]}]
                                """)));
        JsonNode resolved = ConfigurationAttributes.resolve(client, connector(), definitions, "{}");
        assertThat(ConfigurationAttributes.encode(resolved, "{}").toString()).contains("finished", "middle");
        verify(postRequestedFor(urlEqualTo(CALLBACK))
                .withRequestBody(equalToJson(
                        """
                                {"uuid":"00000000-0000-4000-8000-000000000601","name":"first","pathVariable":{"kind":"default"},"requestParameter":{},"body":{}}
                                """)));
        verify(postRequestedFor(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.body.bodyValue", equalTo("ready"))));
        verify(2, postRequestedFor(urlEqualTo(CALLBACK)));
    }

    @Test
    void dataCallbackChoicesKeepOfferedReferences() {
        JsonNode definitions = json("""
                [{"name":"value","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "attributeCallback":{"callbackContext":"choices","callbackMethod":"GET","mappings":[]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK)).willReturn(okJson("""
                [{"contentType":"string","reference":"choice-ref","data":"RSA"}]
                """)));
        assertThat(ConfigurationAttributes
                .encode(client, connector(), definitions, "{\"value\":\"RSA\"}")
                .get(0)
                .at("/content/0/reference")
                .asString()).isEqualTo("choice-ref");
        verify(1, postRequestedFor(urlEqualTo(CALLBACK)));
    }

    @Test
    void missingDependencyAndCyclesStopBeforeMutation() {
        JsonNode definitions = json("""
                [{"name":"a","type":"group","attributeCallback":{"callbackContext":"x","callbackMethod":"GET",
                  "mappings":[{"from":"b","to":"x","targets":["body"]}]}},
                 {"name":"b","type":"group","attributeCallback":{"callbackContext":"x","callbackMethod":"GET",
                  "mappings":[{"from":"a","to":"x","targets":["body"]}]}}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.encode(client, connector(), definitions, "{}"))
                .withMessageContaining("a needs b")
                .withMessageContaining("b needs a");
        verify(0, postRequestedFor(urlEqualTo(CALLBACK)));
    }

    @Test
    void resourceAndInterfaceContextsUseFixedPathsAndInterfaceUuid() {
        JsonNode definitions = json("""
                [{"name":"choices","type":"data","contentType":"string","properties":{"list":true},
                  "attributeCallback":{"dependsOn":[]}}]
                """);
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .willReturn(okJson("[{\"data\":\"RSA\"}]")));
        ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, "{}");
        verify(postRequestedFor(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .withRequestBody(matchingJsonPath("$.interfaceUuid", equalTo(ATTRIBUTE)))
                .withRequestBody(matchingJsonPath("$.attributes", equalToJson("[]"))));
        stubFor(post(urlEqualTo("/v1/raProfiles/" + CONNECTOR + "/callback"))
                .willReturn(okJson("[{\"data\":\"RSA\"}]")));
        ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ResourceContext(Resource.RA_PROFILE, CONNECTOR),
                        definitions, "{}");
        verify(postRequestedFor(urlEqualTo("/v1/raProfiles/" + CONNECTOR + "/callback")));
    }

    @Test
    void malformedValuesErrorsDoNotEchoInput() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.encode(schema("string", "{}"), "{SENTINEL_CREDENTIAL"))
                .withMessageNotContaining("SENTINEL_CREDENTIAL");
    }

    @Test
    void replacementPreservesReadableExistingButRequiresFreshProtectedValues() {
        JsonNode definitions = json("""
                [{"name":"name","type":"custom","version":2,"contentType":"string","properties":{}},
                 {"name":"secret","type":"custom","version":2,"contentType":"secret","properties":{}}]
                """);
        JsonNode existing = json("""
                [{"name":"name","content":[{"data":"retained"}]},
                 {"name":"secret","content":[{"data":{"secret":"********"}}]}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.replace(definitions, existing, "{}"))
                .withMessageContaining("secret")
                .withMessageContaining("fresh");
        assertThat(
                ConfigurationAttributes.replace(definitions, existing, "{\"secret\":{\"secret\":\"fresh-synthetic\"}}"))
                .isEqualTo(
                        json("""
                                [{"name":"name","version":"v2","contentType":"string","content":[{"data":"retained"}]},
                                 {"name":"secret","version":"v2","contentType":"secret","content":[{"data":{"secret":"fresh-synthetic"}}]}]
                                """));
    }

    @Test
    void callbackExpansionIsBoundedBeforeMutation() {
        for (int index = 0; index < 40; index++) {
            stubFor(post(urlEqualTo(CALLBACK))
                    .withRequestBody(matchingJsonPath("$.name", equalTo("group" + index)))
                    .willReturn(okJson("[{\"name\":\"group" + (index + 1) + "\",\"type\":\"group\","
                            + "\"attributeCallback\":{\"callbackContext\":\"next\",\"callbackMethod\":\"GET\",\"mappings\":[]}}]")));
        }
        JsonNode definitions = json(
                """
                        [{"name":"group0","type":"group","attributeCallback":{"callbackContext":"next","callbackMethod":"GET","mappings":[]}}]
                        """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.encode(client, connector(), definitions, "{}"))
                .withMessageContaining("bound");
        verify(0, postRequestedFor(urlEqualTo("/test-write")));
        verify(0,
                postRequestedFor(urlEqualTo(CALLBACK)).withRequestBody(matchingJsonPath("$.name", equalTo("group32"))));
    }

    @Test
    void rejectsUnknownSchemaVersionsAndInvalidOptionReferences() {
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigurationAttributes.encode(json("""
                [{"name":"value","type":"data","version":4,"contentType":"string","properties":{}}]
                """), "{\"value\":\"x\"}"));
        JsonNode definitions = json("""
                [{"name":"value","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "content":[{"contentType":"string","reference":"rsa-ref","data":"RSA"}]}]
                """);
        assertThat(ConfigurationAttributes
                .encode(definitions, "{\"value\":{\"reference\":\"rsa-ref\"}}")
                .get(0)
                .at("/content/0/data")
                .asString()).isEqualTo("RSA");
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> ConfigurationAttributes.encode(definitions, "{\"value\":{\"reference\":\"unknown\"}}"));
    }

    @Test
    void interfaceCallbackIncludesEncodedDependencyAttributes() {
        JsonNode definitions = json(
                """
                        [{"name":"algorithm","type":"data","version":3,"contentType":"string","properties":{},"content":[{"contentType":"string","data":"RSA"}]},
                         {"name":"size","type":"data","version":3,"contentType":"integer","properties":{"list":true},"attributeCallback":{"dependsOn":["algorithm"]}}]
                        """);
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .willReturn(okJson("[{\"contentType\":\"integer\",\"data\":2048}]")));
        ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, "{}");
        verify(postRequestedFor(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .withRequestBody(matchingJsonPath("$.attributes", equalToJson(
                        """
                                [{"name":"algorithm","version":"v3","contentType":"string","content":[{"contentType":"string","data":"RSA"}]}]
                                """))));
    }

    @Test
    void rejectsCallbackGroupRecursionAndInvalidContextCoordinates() {
        JsonNode definitions = json(
                """
                        [{"name":"recursive","type":"group","attributeCallback":{"callbackContext":"provider","callbackMethod":"GET","mappings":[]}}]
                        """);
        stubFor(post(urlEqualTo(CALLBACK)).willReturn(okJson(definitions.toString())));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.resolve(client, connector(), definitions, "{}"))
                .withMessageContaining("cycle")
                .withMessageContaining("recursive");
        verify(1, postRequestedFor(urlEqualTo(CALLBACK)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfigurationAttributes.ResourceContext(Resource.RA_PROFILE, "../../secrets"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, "not-a-uuid"));
    }

    @Test
    void preservesReadableFileAndContentReferencesDuringReplacement() {
        JsonNode definitions = json("""
                [{"name":"file","type":"data","version":3,"contentType":"file","properties":{}}]
                """);
        JsonNode existing = json("""
                [{"name":"file","content":[{"contentType":"file","reference":"readable-file-ref",
                   "data":{"content":"aGVsbG8=","fileName":"sample.txt","mimeType":"text/plain"}}]}]
                """);
        assertThat(ConfigurationAttributes.replace(definitions, existing, "{}").get(0).path("content"))
                .isEqualTo(existing.get(0).path("content"));
    }

    @Test
    void callbackDependencyOscillationIsReportedAsACycle() {
        JsonNode definitions = json("""
                [{"name":"mode","type":"data","contentType":"boolean","properties":{},"content":[{"data":false}]},
                 {"name":"group","type":"group","attributeCallback":{"callbackContext":"choice","callbackMethod":"GET",
                   "mappings":[{"from":"mode","to":"mode","targets":["body"]}]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.body.mode", equalTo("false")))
                .willReturn(
                        okJson("""
                                [{"name":"mode","type":"data","contentType":"boolean","properties":{},"content":[{"data":true}]}]
                                """)));
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.body.mode", equalTo("true")))
                .willReturn(
                        okJson("""
                                [{"name":"mode","type":"data","contentType":"boolean","properties":{},"content":[{"data":false}]}]
                                """)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.resolve(client, connector(), definitions, "{}"))
                .withMessageContaining("cycle");
        verify(2, postRequestedFor(urlEqualTo(CALLBACK)));
    }

    private static ConfigurationAttributes.ConnectorContext connector() {
        return new ConfigurationAttributes.ConnectorContext(CONNECTOR, FunctionGroupCode.NOTIFICATION_PROVIDER,
                "EMAIL");
    }

    private static JsonNode schema(String type, String properties) {
        return json("[{\"name\":\"value\",\"version\":3,\"type\":\"data\",\"contentType\":\"" + type
                + "\",\"properties\":" + properties + "}]");
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    @Test
    void replacementCallbacksUseValidatedExistingReadableDependencyValues() {
        JsonNode definitions = json("""
                [{"name":"region","type":"data","version":2,"contentType":"string","properties":{}},
                {"name":"password","type":"data","version":2,"contentType":"secret","properties":{}},
                {"name":"providerFields","type":"group","attributeCallback":{"callbackContext":"schema",
                "mappings":[{"from":"region","to":"zone","targets":["body"]}]}}]
                """);
        JsonNode existing = json("""
                [{"name":"region","content":[{"data":"eu"}]},
                {"name":"password","content":[{"data":{"secret":"DO_NOT_REPLAY"}}]}]
                """);
        stubFor(get(urlEqualTo("/v1/info")).willReturn(okJson("{\"app\":{\"version\":\"2.20.0\"}}")));
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback")).willReturn(okJson("""
                [{"name":"size","type":"data","version":3,"contentType":"integer","properties":{}}]
                """)));
        JsonNode resolved = ConfigurationAttributes
                .resolveReplacement(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, existing, "{\"password\":{\"secret\":\"fresh\"},\"size\":2048}");
        assertThat(resolved.toString()).contains("size").doesNotContain("DO_NOT_REPLAY");
        verify(postRequestedFor(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .withRequestBody(matchingJsonPath("$.body.zone", equalTo("eu")))
                .withRequestBody(notMatching(".*DO_NOT_REPLAY.*")));
    }

    @Test
    void replacementCallbackSeedingRefusesProtectedMaskedAndInvalidExistingValues() {
        JsonNode readable = json("""
                [{"name":"region","type":"data","version":2,"contentType":"string","properties":{}}]
                """);
        for (String value : new String[]{"\"********\"", "123"}) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ConfigurationAttributes
                            .resolveReplacement(client, connector(), readable,
                                    json("[{\"name\":\"region\",\"content\":[{\"data\":" + value + "}]}]"), "{}"));
        }
        JsonNode protectedDefinitions = json("""
                [{"name":"password","type":"data","version":2,"contentType":"secret","properties":{}}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes
                        .resolveReplacement(client, connector(), protectedDefinitions,
                                json("[{\"name\":\"password\",\"content\":[]}]"), "{}"))
                .withMessageContaining("fresh");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void recordedWebhookMappingSendsTheSelectedContentDataToTheFixedCallbackRoute() {
        IlmTestSupport.stubIlmVersion("2.20.0");
        String callback = "/v1/connectors/" + CONNECTOR + "/notificationProvider/WEBHOOK/callback";
        stubFor(post(urlEqualTo(callback))
                .willReturn(okJson(IlmTestSupport.fixture("notification-callback-template-json"))));
        JsonNode definitions = json(IlmTestSupport.fixture("connector-attributes-webhook"));
        String values = """
                {"data_webhookUrl":"https://example.com/webhook","data_contentType":"JSON",
                 "data_contentTemplate_json":"{}"}
                """;
        assertThatCode(
                () -> ConfigurationAttributes
                        .encode(client,
                                new ConfigurationAttributes.ConnectorContext(CONNECTOR,
                                        FunctionGroupCode.NOTIFICATION_PROVIDER, "WEBHOOK"),
                                definitions, values))
                .doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(equalToJson("""
                {"uuid":"00000000-0000-4000-8000-000000000614","name":"group_contentTemplate",
                 "pathVariable":{"contentType":"JSON"},"requestParameter":{},"body":{}}
                """)));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void legacyMappingsTraverseContentEnvelopesWithoutConfusingNestedDataProperties(int version) {
        JsonNode definitions = json("""
                [{"name":"choice","type":"data","version":%d,"contentType":"string","properties":{"list":true},
                  "content":[{"reference":"first","data":"RSA"},{"reference":"second","data":"ECDSA"}]},
                 {"name":"payload","type":"data","version":%d,"contentType":"object","properties":{}},
                 {"name":"group","type":"group","attributeCallback":{"callbackContext":"/provider","mappings":[
                  {"from":"choice","to":"bare","targets":["body"]},
                  {"from":"choice.data","to":"selected","targets":["body"]},
                  {"from":"choice.reference","to":"reference","targets":["requestParameter"]},
                  {"from":"payload.data","to":"whole","targets":["body"]},
                  {"from":"payload.data.nested","to":"outer","targets":["body"]},
                  {"from":"payload.data.data.nested","to":"inner","targets":["body"]}]}}]
                """.formatted(version, version));
        stubFor(post(urlEqualTo(CALLBACK)).willReturn(okJson("[]")));
        assertThatCode(() -> ConfigurationAttributes.resolve(client, connector(), definitions, """
                {"choice":{"reference":"second"},"payload":{"data":{"nested":"inside"},"nested":"outside"}}
                """)).doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(CALLBACK)).withRequestBody(equalToJson("""
                {"name":"group","pathVariable":{},"requestParameter":{"reference":"second"},
                 "body":{"bare":"ECDSA","selected":"ECDSA","whole":{"data":{"nested":"inside"},"nested":"outside"},
                         "outer":"outside","inner":"inside"}}
                """)));
    }

    @Test
    void legacyV3MappingCanReadTheSelectedContentTypeTag() {
        JsonNode definitions = json("""
                [{"name":"algorithm","type":"data","version":3,"contentType":"string","properties":{},
                  "content":[{"contentType":"string","data":"RSA"}]},
                 {"name":"group","type":"group","attributeCallback":{"callbackContext":"/provider","mappings":[
                  {"from":"algorithm.contentType","to":"type","targets":["body"]}]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK)).willReturn(okJson("[]")));
        assertThatCode(() -> ConfigurationAttributes.resolve(client, connector(), definitions, "{}"))
                .doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.body.type", equalTo("string"))));
    }

    @Test
    void legacyMultiSelectMappingUsesTheFirstSelectedContentEnvelope() {
        JsonNode definitions = json("""
                [{"name":"choices","type":"data","version":3,"contentType":"string",
                  "properties":{"list":true,"multiSelect":true},
                  "content":[{"reference":"a","data":"alpha"},{"reference":"b","data":"beta"}]},
                 {"name":"group","type":"group","attributeCallback":{"callbackContext":"/provider","mappings":[
                  {"from":"choices.data","to":"firstValue","targets":["body"]},
                  {"from":"choices.reference","to":"firstReference","targets":["body"]}]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK)).willReturn(okJson("[]")));
        assertThatCode(() -> ConfigurationAttributes
                .resolve(client, connector(), definitions,
                        "{\"choices\":[{\"reference\":\"b\"},{\"reference\":\"a\"}]}"))
                .doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(CALLBACK)).withRequestBody(equalToJson("""
                {"name":"group","pathVariable":{},"requestParameter":{},
                 "body":{"firstValue":"beta","firstReference":"b"}}
                """)));
    }

    @Test
    void dependencyAttributesKeepTheExplicitReferenceWhenOfferedValuesHaveIdenticalData() {
        IlmTestSupport.stubIlmVersion("2.20.0");
        JsonNode definitions = json("""
                [{"name":"algorithm","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "content":[{"contentType":"string","reference":"first","data":"RSA"},
                             {"contentType":"string","reference":"second","data":"RSA"}]},
                 {"name":"group","type":"group","attributeCallback":{"dependsOn":["algorithm"]}}]
                """);
        String callback = "/v2/connectors/" + CONNECTOR + "/callback";
        stubFor(post(urlEqualTo(callback)).willReturn(okJson("[]")));
        ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, "{\"algorithm\":{\"reference\":\"second\"}}");
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(matchingJsonPath("$.attributes", equalToJson("""
                [{"name":"algorithm","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"second","data":"RSA"}]}]
                """))));
    }

    @Test
    void dependencyAttributesKeepEveryMultiSelectReferenceInSelectionOrder() {
        IlmTestSupport.stubIlmVersion("2.20.0");
        JsonNode definitions = json("""
                [{"name":"algorithms","type":"data","version":3,"contentType":"string",
                  "properties":{"list":true,"multiSelect":true},
                  "content":[{"contentType":"string","reference":"first","data":"RSA"},
                             {"contentType":"string","reference":"second","data":"RSA"}]},
                 {"name":"group","type":"group","attributeCallback":{"dependsOn":["algorithms"]}}]
                """);
        String callback = "/v2/connectors/" + CONNECTOR + "/callback";
        stubFor(post(urlEqualTo(callback)).willReturn(okJson("[]")));
        ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, "{\"algorithms\":[{\"reference\":\"second\"},{\"reference\":\"first\"}]}");
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(matchingJsonPath("$.attributes", equalToJson("""
                [{"name":"algorithms","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"second","data":"RSA"},
                             {"contentType":"string","reference":"first","data":"RSA"}]}]
                """))));
    }

    @Test
    void legacyConsumerBeforeActiveListSourceWaitsForResolvedChoices() {
        JsonNode definitions = json("""
                [{"name":"consumer","type":"group","attributeCallback":{"callbackContext":"/consumer","mappings":[
                  {"from":"choice.data","to":"algorithm","targets":["body"]}]}},
                 {"name":"choice","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "attributeCallback":{"callbackContext":"/choices","mappings":[]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.name", equalTo("choice")))
                .willReturn(okJson("[{\"contentType\":\"string\",\"reference\":\"rsa-ref\",\"data\":\"RSA\"}]")));
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.name", equalTo("consumer")))
                .willReturn(okJson("[]")));
        assertThatCode(() -> ConfigurationAttributes
                .resolve(client, connector(), definitions, "{\"choice\":{\"reference\":\"rsa-ref\"}}"))
                .doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.body.algorithm", equalTo("RSA"))));
        verify(2, postRequestedFor(urlEqualTo(CALLBACK)));
    }

    @Test
    void dependencyConsumerBeforeActiveListSourceWaitsForResolvedChoices() {
        IlmTestSupport.stubIlmVersion("2.20.0");
        JsonNode definitions = json("""
                [{"name":"consumer","type":"group","attributeCallback":{"dependsOn":["choice"]}},
                 {"name":"choice","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "attributeCallback":{"dependsOn":[]}}]
                """);
        String callback = "/v2/connectors/" + CONNECTOR + "/callback";
        stubFor(post(urlEqualTo(callback))
                .withRequestBody(matchingJsonPath("$.name", equalTo("choice")))
                .willReturn(okJson("[{\"contentType\":\"string\",\"reference\":\"rsa-ref\",\"data\":\"RSA\"}]")));
        stubFor(post(urlEqualTo(callback))
                .withRequestBody(matchingJsonPath("$.name", equalTo("consumer")))
                .willReturn(okJson("[]")));
        assertThatCode(() -> ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, "{\"choice\":{\"reference\":\"rsa-ref\"}}"))
                .doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(matchingJsonPath("$.attributes", equalToJson("""
                [{"name":"choice","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"rsa-ref","data":"RSA"}]}]
                """))));
        verify(2, postRequestedFor(urlEqualTo(callback)));
    }

    @Test
    void invalidActiveListSourceValueNeverInvokesItsConsumer() {
        JsonNode definitions = json("""
                [{"name":"consumer","type":"group","attributeCallback":{"callbackContext":"/consumer","mappings":[
                  {"from":"choice.data","to":"algorithm","targets":["body"]}]}},
                 {"name":"choice","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "attributeCallback":{"callbackContext":"/choices","mappings":[]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.name", equalTo("choice")))
                .willReturn(okJson("[{\"contentType\":\"string\",\"reference\":\"rsa-ref\",\"data\":\"RSA\"}]")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes
                        .resolve(client, connector(), definitions, "{\"choice\":\"ECDSA\"}"))
                .withMessageContaining("offered option");
        verify(postRequestedFor(urlEqualTo(CALLBACK)).withRequestBody(matchingJsonPath("$.name", equalTo("choice"))));
        verify(0, postRequestedFor(urlEqualTo(CALLBACK))
                .withRequestBody(matchingJsonPath("$.name", equalTo("consumer"))));
    }

    @Test
    void explicitEmptyOptionalMultiSelectIsPresentInDependencyAttributes() {
        IlmTestSupport.stubIlmVersion("2.20.0");
        JsonNode definitions = json("""
                [{"name":"choices","type":"data","version":3,"contentType":"string",
                  "properties":{"list":true,"multiSelect":true}},
                 {"name":"consumer","type":"group","attributeCallback":{"dependsOn":["choices"]}}]
                """);
        String callback = "/v2/connectors/" + CONNECTOR + "/callback";
        stubFor(post(urlEqualTo(callback)).willReturn(okJson("[]")));
        assertThatCode(() -> ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                        definitions, "{\"choices\":[]}"))
                .doesNotThrowAnyException();
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(matchingJsonPath("$.attributes", equalToJson("""
                [{"name":"choices","version":"v3","contentType":"string","content":[]}]
                """))));
    }

    @Test
    void missingOptionalMultiSelectDoesNotInventAnEmptyDependency() {
        JsonNode definitions = json("""
                [{"name":"choices","type":"data","version":3,"contentType":"string",
                  "properties":{"list":true,"multiSelect":true}},
                 {"name":"consumer","type":"group","attributeCallback":{"dependsOn":["choices"]}}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.resolve(client, connector(), definitions, "{}"))
                .withMessageContaining("Unresolved attribute callbacks", "choices");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void explicitEmptyMultiSelectHasNoLegacyDataMappingValue() {
        JsonNode definitions = json("""
                [{"name":"choices","type":"data","version":3,"contentType":"string",
                  "properties":{"list":true,"multiSelect":true}},
                 {"name":"consumer","type":"group","attributeCallback":{"callbackContext":"/consumer","mappings":[
                  {"from":"choices.data","to":"selection","targets":["body"]}]}}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes.resolve(client, connector(), definitions, "{\"choices\":[]}"))
                .withMessageContaining("Unresolved attribute callbacks", "choices.data");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void emptyRequiredSelectionOrNonSelectionScalarIsRejectedBeforeCallback() {
        for (String properties : new String[]{
                "{\"list\":false,\"multiSelect\":false}",
                "{\"list\":true,\"multiSelect\":true,\"required\":true}"}) {
            JsonNode definitions = json("""
                    [{"name":"choices","type":"data","version":3,"contentType":"string","properties":%s},
                     {"name":"consumer","type":"group","attributeCallback":{"dependsOn":["choices"]}}]
                    """.formatted(properties));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ConfigurationAttributes
                            .resolve(client, connector(), definitions, "{\"choices\":[]}"));
        }
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void explicitEmptyOptionalSelectionsClearExistingOrOfferedValues() {
        for (String properties : new String[]{
                "{\"list\":true,\"multiSelect\":false}",
                "{\"list\":true,\"multiSelect\":true}",
                "{\"multiSelect\":true}"}) {
            JsonNode definitions = json("""
                    [{"name":"choices","type":"data","version":3,"contentType":"string","properties":%s,
                      "content":[{"contentType":"string","reference":"rsa-ref","data":"RSA"}]}]
                    """.formatted(properties));
            JsonNode existing = json("""
                    [{"name":"choices","content":[{"contentType":"string","reference":"rsa-ref","data":"RSA"}]}]
                    """);
            assertThat(ConfigurationAttributes.encode(definitions, "{\"choices\":[]}")).isEqualTo(json("[]"));
            assertThat(ConfigurationAttributes.replace(definitions, existing, "{\"choices\":[]}"))
                    .isEqualTo(json("[]"));
        }
    }

    @Test
    void emptyOptionalSelectionStillValidatesItsSchemaAndProtectedInput() {
        for (String field : new String[]{
                "{\"name\":\"choices\",\"type\":\"data\",\"version\":4,\"contentType\":\"string\"}",
                "{\"name\":\"choices\",\"type\":\"data\",\"version\":3,\"contentType\":\"secret\"}",
                "{\"name\":\"choices\",\"type\":\"data\",\"version\":2,\"contentType\":\"resource\"}"}) {
            JsonNode definition = json(field);
            ((ObjectNode) definition).set("properties", json("{\"multiSelect\":true}"));
            ArrayNode definitions = JSON.createArrayNode().add(definition);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ConfigurationAttributes.encode(definitions, "{\"choices\":[]}"));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigurationAttributes.encode(json("""
                [{"name":"choices","type":"data","version":3,"contentType":"string",
                  "properties":{"multiSelect":true}}]
                """), "{\"choices\":[\"********\"]}"));
    }

    @Test
    void deepPrerequisiteChainFailsBeforeInvokingCallbacks() {
        IlmTestSupport.stubIlmVersion("2.20.0");
        ArrayNode definitions = JSON.createArrayNode();
        ObjectNode values = JSON.createObjectNode();
        for (int index = 0; index < 40; index++) {
            ObjectNode definition = JSON
                    .createObjectNode()
                    .put("name", "choice" + index)
                    .put("type", "data")
                    .put("version", 3)
                    .put("contentType", "string");
            definition.putObject("properties").put("list", true);
            definition
                    .putObject("attributeCallback")
                    .putArray("dependsOn")
                    .add(index == 39 ? "seed" : "choice" + (index + 1));
            definitions.add(definition);
            values.put("choice" + index, "RSA");
        }
        definitions.add(json("""
                {"name":"seed","type":"data","version":3,"contentType":"string","properties":{},
                 "content":[{"contentType":"string","data":"RSA"}]}
                """));
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .willReturn(okJson("[{\"contentType\":\"string\",\"data\":\"RSA\"}]")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes
                        .resolve(client, new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE),
                                definitions, values.toString()))
                .withMessageContaining("bounded call limit");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void explicitReferenceSelectionCannotMatchAnotherOptionsObjectData() {
        JsonNode definitions = json("""
                [{"name":"choice","type":"data","version":3,"contentType":"object",
                  "properties":{"list":true,"extensibleList":true},
                  "content":[{"contentType":"object","reference":"other","data":{"reference":"selected"}},
                             {"contentType":"object","reference":"selected","data":{"name":"selected object"}}]}]
                """);
        assertThat(ConfigurationAttributes.encode(definitions, "{\"choice\":{\"reference\":\"selected\"}}"))
                .isEqualTo(
                        json("""
                                [{"name":"choice","version":"v3","contentType":"object",
                                  "content":[{"contentType":"object","reference":"selected","data":{"name":"selected object"}}]}]
                                """));
    }

    @Test
    void unmatchedReferenceSelectionCannotBecomeExtensibleObjectData() {
        JsonNode definitions = json("""
                [{"name":"choice","type":"data","version":3,"contentType":"object",
                  "properties":{"list":true,"extensibleList":true},"content":[]}]
                """);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ConfigurationAttributes
                        .encode(definitions, "{\"choice\":{\"reference\":\"unavailable\"}}"));
        assertThat(ConfigurationAttributes.encode(definitions, "{\"choice\":{\"name\":\"new object\"}}"))
                .isEqualTo(json("""
                        [{"name":"choice","version":"v3","contentType":"object",
                          "content":[{"contentType":"object","data":{"name":"new object"}}]}]
                        """));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumerBeforeNonListCallbackSourceUsesResolvedContentWithoutStaleRequiredFields(boolean modern) {
        IlmTestSupport.stubIlmVersion("2.20.0");
        String groupCallback = modern ? "{\"dependsOn\":[\"flag\"]}" : """
                {"callbackContext":"/dependent","mappings":[
                  {"from":"flag.data","to":"flag","targets":["body"]}]}
                """;
        String sourceCallback = modern ? "{\"dependsOn\":[]}" : "{\"callbackContext\":\"/flag\",\"mappings\":[]}";
        JsonNode definitions = json("""
                [{"name":"dependent","type":"group","attributeCallback":%s},
                 {"name":"flag","type":"data","version":3,"contentType":"boolean","properties":{"list":false},
                  "content":[{"contentType":"boolean","data":false}],"attributeCallback":%s}]
                """.formatted(groupCallback, sourceCallback));
        String callback = modern ? "/v2/connectors/" + CONNECTOR + "/callback" : CALLBACK;
        String flagPath = modern ? "$.attributes[0].content[0].data" : "$.body.flag";
        stubFor(post(urlEqualTo(callback))
                .withRequestBody(matchingJsonPath("$.name", equalTo("flag")))
                .willReturn(okJson("[{\"contentType\":\"boolean\",\"data\":true}]")));
        stubFor(post(urlEqualTo(callback))
                .withRequestBody(matchingJsonPath("$.name", equalTo("dependent")))
                .withRequestBody(matchingJsonPath(flagPath, equalTo("false")))
                .willReturn(
                        okJson("""
                                [{"name":"oldField","type":"data","version":3,"contentType":"string","properties":{"required":true}}]
                                """)));
        stubFor(post(urlEqualTo(callback))
                .withRequestBody(matchingJsonPath("$.name", equalTo("dependent")))
                .withRequestBody(matchingJsonPath(flagPath, equalTo("true")))
                .willReturn(
                        okJson("""
                                [{"name":"newField","type":"data","version":3,"contentType":"string","properties":{"required":true}}]
                                """)));
        ConfigurationAttributes.CallbackContext context = modern
                ? new ConfigurationAttributes.ConnectorInterfaceContext(CONNECTOR, ATTRIBUTE)
                : connector();
        assertThat(ConfigurationAttributes.encode(client, context, definitions, "{\"newField\":\"fresh\"}"))
                .isEqualTo(
                        json("""
                                [{"name":"flag","version":"v3","contentType":"boolean","content":[{"contentType":"boolean","data":true}]},
                                 {"name":"newField","version":"v3","contentType":"string","content":[{"contentType":"string","data":"fresh"}]}]
                                """));
        verify(0,
                postRequestedFor(urlEqualTo(callback))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("dependent")))
                        .withRequestBody(matchingJsonPath(flagPath, equalTo("false"))));
        verify(1,
                postRequestedFor(urlEqualTo(callback))
                        .withRequestBody(matchingJsonPath("$.name", equalTo("dependent")))
                        .withRequestBody(matchingJsonPath(flagPath, equalTo("true"))));
    }

}
