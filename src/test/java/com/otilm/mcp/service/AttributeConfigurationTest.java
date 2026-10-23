package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AttributeConfigurationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AttributeConfigurationTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private static final String OBJECT = "00000000-0000-4000-8000-000000000002";
    private AttributeConfigurationTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new AttributeConfigurationTool(new AttributeConfigurationServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build())));
    }

    @Test
    void createsFullDefinitionWithTypedContentAndFlags() {
        stubFor(post(urlEqualTo("/v1/attributes/custom"))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"name\":\"Environment\"}")));
        assertThat(tool
                .createCustomAttributeDefinition(
                        "{\"name\":\"Environment\",\"label\":\"Environment\",\"contentType\":\"string\",\"resources\":[\"certificates\"],\"required\":true,\"visible\":false,\"list\":true,\"multiSelect\":true,\"extensibleList\":true,\"protectionLevel\":\"none\",\"content\":[{\"reference\":\"production\",\"data\":\"prod\"}]}"))
                .contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/attributes/custom"))
                .withRequestBody(matchingJsonPath("$.content[0].data", equalTo("prod")))
                .withRequestBody(matchingJsonPath("$.required", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.visible", equalTo("false"))));
    }

    @Test
    void updatesPreserveResourceListsFlagsAndReadableDefaults() {
        stubFor(get(urlEqualTo("/v1/attributes/custom/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID
                        + "\",\"name\":\"Environment\",\"label\":\"Environment\",\"contentType\":\"string\",\"visible\":false,\"required\":true,\"resources\":[\"certificates\",\"keys\"],\"content\":[{\"data\":\"prod\"}]}")));
        stubFor(put(urlEqualTo("/v1/attributes/custom/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"label\":\"Changed\"}")));
        assertThat(tool.updateCustomAttribute(ID, "{\"label\":\"Changed\"}")).contains("Changed");
        verify(putRequestedFor(urlEqualTo("/v1/attributes/custom/" + ID))
                .withRequestBody(matchingJsonPath("$.visible", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.resources", equalToJson("[\"certificates\",\"keys\"]")))
                .withRequestBody(matchingJsonPath("$.content[0].data", equalTo("prod"))));
        assertThat(tool.getCustomAttribute(ID)).contains("Environment");
    }

    @Test
    void protectedDefinitionDefaultsRequireFreshContentBeforeReplacement() {
        stubFor(get(urlEqualTo("/v1/attributes/custom/" + ID))
                .willReturn(okJson(
                        "{\"name\":\"Password\",\"label\":\"Password\",\"contentType\":\"secret\",\"protectionLevel\":\"encrypted\",\"content\":[{\"data\":{\"secret\":\"private-sentinel\"}}]}")));
        assertThat(tool.updateCustomAttribute(ID, "{\"label\":\"Changed\"}"))
                .contains("fresh")
                .doesNotContain("private-sentinel");
        verify(0, putRequestedFor(anyUrl()));
        assertThat(tool.getCustomAttribute(ID)).doesNotContain("private-sentinel");
    }

    @Test
    void resourceReplacementRequiresExplicitIntentAndValidCodes() {
        stubFor(patch(urlEqualTo("/v1/attributes/custom/" + ID + "/resources")).willReturn(noContent()));
        assertThat(tool.setCustomAttributeResources(ID, "[\"keys\"]", false)).contains("replace=true");
        assertThat(tool.setCustomAttributeResources(ID, "[\"private-sentinel\"]", true))
                .contains("resource")
                .doesNotContain("private-sentinel");
        assertThat(tool.setCustomAttributeResources(ID, "[\"keys\"]", true)).contains("replaced");
        verify(1, patchRequestedFor(urlEqualTo("/v1/attributes/custom/" + ID + "/resources"))
                .withRequestBody(equalToJson("[\"keys\"]")));
    }

    @Test
    void targetedObjectValueWritesUseSchemaVersionAndNeverReplaceOtherFields() {
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/certificates"))
                .willReturn(okJson("[{\"uuid\":\"" + ID
                        + "\",\"name\":\"Environment\",\"type\":\"custom\",\"version\":3,\"contentType\":\"string\",\"properties\":{}}]")));
        String path = "/v1/attributes/custom/resources/certificates/objects/" + OBJECT + "/" + ID;
        stubFor(patch(urlEqualTo(path)).willReturn(okJson("[{\"content\":[{\"data\":\"private-sentinel\"}]}]")));
        assertThat(tool.setCustomAttributeValue("certificates", OBJECT, ID, "\"prod\""))
                .contains(ID)
                .doesNotContain("private-sentinel");
        verify(patchRequestedFor(urlEqualTo(path))
                .withRequestBody(equalToJson("[{\"contentType\":\"string\",\"data\":\"prod\"}]")));
        verify(0, putRequestedFor(anyUrl()));
        stubFor(delete(urlEqualTo(path)).willReturn(okJson("[]")));
        assertThat(tool.deleteCustomAttributeValue("certificates", OBJECT, ID)).contains("deleted");
        assertThat(tool.getResourceCustomAttributes("certificates")).contains("Environment");
    }

    @ParameterizedTest
    @CsvSource({"2, true, false", "2, false, true", "3, true, false", "3, false, true"})
    void targetedValueSetterRequiresExplicitDeleteForEmptyOptionalSelections(int version, boolean list,
            boolean multiSelect) {
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/certificates")).willReturn(okJson("""
                [{"uuid":"%s","name":"Environment","type":"custom","version":%d,
                "contentType":"string","properties":{"required":false,"list":%s,"multiSelect":%s},
                "content":[{"reference":"production","data":"prod"%s}]}]
                """.formatted(ID, version, list, multiSelect, version == 3 ? ",\"contentType\":\"string\"" : ""))));
        String path = "/v1/attributes/custom/resources/certificates/objects/" + OBJECT + "/" + ID;
        stubFor(patch(urlEqualTo(path)).willReturn(okJson("[]")));

        assertThat(tool.setCustomAttributeValue("certificates", OBJECT, ID, "[]"))
                .contains("use delete_custom_attribute_value to clear it");
        verify(0, patchRequestedFor(anyUrl()));
    }

    @Test
    void lifecycleReadsAndInvalidDefinitionsStayWithinPublishedRoutes() {
        stubFor(patch(urlEqualTo("/v1/attributes/custom/" + ID + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/attributes/custom/" + ID + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/attributes/custom/" + ID)).willReturn(noContent()));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources")).willReturn(okJson("[\"certificates\",\"keys\"]")));
        assertThat(tool.enableCustomAttribute(ID)).contains("enabled");
        assertThat(tool.disableCustomAttribute(ID)).contains("disabled");
        assertThat(tool.deleteCustomAttribute(ID)).contains("deleted");
        assertThat(tool.getCustomAttributeResources()).contains("certificates", "keys");
        assertThat(tool
                .createCustomAttributeDefinition(
                        "{\"name\":\"Bad\",\"label\":\"Bad\",\"contentType\":\"integer\",\"content\":[{\"data\":\"private-sentinel\"}]}"))
                .contains("integer")
                .doesNotContain("private-sentinel");
        verify(0, postRequestedFor(anyUrl()));
    }
}
