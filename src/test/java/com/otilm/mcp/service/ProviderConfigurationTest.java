package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.CredentialConfigurationTool;
import com.otilm.mcp.tool.ProxyTool;
import com.otilm.mcp.tool.ToolSpecifications;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class ProviderConfigurationTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private static final String CONNECTOR = "00000000-0000-4000-8000-000000000002";
    private static final String FIELD = "00000000-0000-4000-8000-000000000003";
    private static final String SCHEMA = "[{\"uuid\":\"" + FIELD
            + "\",\"name\":\"password\",\"type\":\"data\",\"version\":2,\"contentType\":\"secret\",\"properties\":{\"required\":true}}]";
    private static final String CALLBACK_ENDPOINT = "/v1/connectors/" + CONNECTOR
            + "/credentialProvider/Basic/callback";
    private static final String CALLBACK_FIELDS = """
            [{"name":"port","type":"data","version":3,"contentType":"integer","properties":{"required":true}},
             {"name":"password","type":"data","version":2,"contentType":"secret","properties":{},
              "content":[{"data":{"secret":"PROTECTED_CALLBACK_SENTINEL"}}]},
             {"name":"protectedText","type":"data","version":3,"contentType":"string",
              "properties":{"protectionLevel":"encrypted"},"content":[{"data":"PROTECTED_TEXT_SENTINEL"}]}]
            """;
    private CredentialConfigurationTool credentials;
    private ProxyTool proxies;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        ConfigurationApiClient api = new ConfigurationApiClient(
                IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build());
        credentials = new CredentialConfigurationTool(new CredentialConfigurationServiceImpl(api));
        proxies = new ProxyTool(new ProxyServiceImpl(api, new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/credentials")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR + "/attributes/credentialProvider/Basic"))
                .willReturn(okJson(SCHEMA)));
    }

    @Test
    void createsTypedCredentialsAndReturnsMetadataOnly() {
        stubFor(post(urlEqualTo("/v1/credentials"))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"attributes\":[{\"data\":\"private-sentinel\"}]}")));
        assertThat(credentials
                .createCredential("{\"name\":\"Basic credential\",\"kind\":\"Basic\",\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"attributes\":{\"password\":{\"secret\":\"private-sentinel\"}}}"))
                .contains(ID)
                .doesNotContain("private-sentinel");
        verify(postRequestedFor(urlEqualTo("/v1/credentials"))
                .withRequestBody(
                        matchingJsonPath("$.attributes[0].content[0].data.secret", equalTo("private-sentinel"))));
    }

    @Test
    void updateRequiresFreshProtectedValuesAndRetainsReadableCustomValues() {
        stubFor(get(urlEqualTo("/v1/credentials/" + ID))
                .willReturn(okJson("{\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"Basic\",\"attributes\":[{\"uuid\":\"" + FIELD
                        + "\",\"name\":\"password\",\"contentType\":\"secret\",\"content\":[{\"data\":\"********\"}]}],\"customAttributes\":[]}")));
        assertThat(credentials.updateCredential(ID, "{}")).contains("fresh");
        verify(0, putRequestedFor(anyUrl()));
        stubFor(put(urlEqualTo("/v1/credentials/" + ID)).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        assertThat(credentials.updateCredential(ID, "{\"attributes\":{\"password\":{\"secret\":\"new-value\"}}}"))
                .contains(ID);
        verify(putRequestedFor(urlEqualTo("/v1/credentials/" + ID))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data.secret", equalTo("new-value"))));
    }

    @Test
    void credentialsNeverExposeDecryptedDetailOrErrors() {
        stubFor(get(urlEqualTo("/v1/credentials"))
                .willReturn(okJson("[{\"uuid\":\"" + ID
                        + "\",\"name\":\"Basic\",\"attributes\":[{\"name\":\"password\",\"content\":[{\"data\":\"private-sentinel\"}]}]}]")));
        assertThat(credentials.listCredentials()).contains("Basic").doesNotContain("private-sentinel");
        stubFor(get(urlEqualTo("/v1/credentials/" + ID))
                .willReturn(aResponse().withStatus(500).withBody("private-sentinel")));
        assertThat(credentials.getCredential(ID)).contains("500").doesNotContain("private-sentinel");
    }

    @Test
    void credentialsUseTargetedLifecycleAndNormalDelete() {
        stubFor(patch(urlEqualTo("/v1/credentials/" + ID + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/credentials/" + ID + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/credentials/" + ID)).willReturn(noContent()));
        assertThat(credentials.enableCredential(ID)).contains("enabled");
        assertThat(credentials.disableCredential(ID)).contains("disabled");
        assertThat(credentials.deleteCredential(ID)).contains("deleted");
        assertThat(credentialSettings(Map.of("connectorUuid", CONNECTOR, "kind", "Basic")))
                .contains("password")
                .doesNotContain("private-sentinel");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.17.0", "2.19.0", "2.20.0"})
    void credentialSettingsResolveFormOpenListsAndGroupsWithoutOptionalValues(String version) {
        IlmTestSupport.stubIlmVersion(version);
        stubCredentialSettings("""
                [{"name":"choice","type":"data","version":2,"contentType":"string",
                  "properties":{"list":true},"content":[],
                  "attributeCallback":{"callbackContext":"choices","callbackMethod":"GET","mappings":[]}},
                 {"name":"fields","type":"group",
                  "attributeCallback":{"callbackContext":"fields","callbackMethod":"GET","mappings":[]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("choice")))
                .willReturn(okJson("[{\"reference\":\"choice-ref\",\"data\":\"Visible choice\"}]")));
        stubFor(post(urlEqualTo(CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("fields")))
                .willReturn(okJson(CALLBACK_FIELDS)));
        assertThat(credentialSettings(Map.of("connectorUuid", CONNECTOR, "kind", "Basic")))
                .contains("Visible choice", "port", "password", "protectedText")
                .doesNotContain("PROTECTED_CALLBACK_SENTINEL", "PROTECTED_TEXT_SENTINEL");
        verify(postRequestedFor(urlEqualTo(CALLBACK_ENDPOINT)).withRequestBody(equalToJson("""
                {"name":"choice","pathVariable":{},"requestParameter":{},"body":{}}
                """)));
        verify(postRequestedFor(urlEqualTo(CALLBACK_ENDPOINT)).withRequestBody(equalToJson("""
                {"name":"fields","pathVariable":{},"requestParameter":{},"body":{}}
                """)));
        verifyNoCredentialMutation();
    }

    @Test
    void legacyCredentialSettingsMapSuppliedValuesIntoTheirProviderCallback() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubCredentialSettings("""
                [{"name":"region","type":"data","version":2,"contentType":"string","properties":{}},
                 {"name":"password","type":"data","version":2,"contentType":"secret","properties":{}},
                 {"name":"fields","type":"group","attributeCallback":{"callbackContext":"fields","callbackMethod":"GET",
                  "mappings":[{"from":"region","to":"zone","targets":["requestParameter"]},
                   {"from":"password.data.secret","to":"token","targets":["body"]}]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK_ENDPOINT)).willReturn(okJson(CALLBACK_FIELDS)));
        assertThat(credentialSettings(Map
                .of("connectorUuid", CONNECTOR, "kind", "Basic", "values",
                        "{\"region\":\"eu\",\"password\":{\"secret\":\"FRESH_INPUT_SENTINEL\"}}")))
                .contains("port", "password")
                .doesNotContain("FRESH_INPUT_SENTINEL", "PROTECTED_CALLBACK_SENTINEL", "PROTECTED_TEXT_SENTINEL");
        verify(postRequestedFor(urlEqualTo(CALLBACK_ENDPOINT)).withRequestBody(equalToJson("""
                {"name":"fields","pathVariable":{},"requestParameter":{"zone":"eu"},
                 "body":{"token":"FRESH_INPUT_SENTINEL"}}
                """)));
        verifyNoCredentialMutation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.19.0", "2.20.0"})
    void modernCredentialSettingsResolveDependentChoicesAndFieldsWithEncodedInputs(String version) {
        IlmTestSupport.stubIlmVersion(version);
        stubCredentialSettings("""
                [{"name":"region","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "content":[{"contentType":"string","reference":"eu-ref","data":"Europe"}]},
                 {"name":"password","type":"data","version":2,"contentType":"secret","properties":{}},
                 {"name":"fields","type":"group","attributeCallback":{"dependsOn":["choice","password"]}},
                 {"name":"choice","type":"data","version":3,"contentType":"string","properties":{"list":true},
                  "content":[],"attributeCallback":{"dependsOn":["region"]}}]
                """);
        stubFor(post(urlEqualTo(CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("choice")))
                .willReturn(okJson(
                        "[{\"contentType\":\"string\",\"reference\":\"choice-ref\",\"data\":\"Visible choice\"}]")));
        stubFor(post(urlEqualTo(CALLBACK_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.name", equalTo("fields")))
                .willReturn(okJson(CALLBACK_FIELDS)));
        assertThat(credentialSettings(Map.of("connectorUuid", CONNECTOR, "kind", "Basic", "values", """
                {"region":{"reference":"eu-ref"},"choice":{"reference":"choice-ref"},
                 "password":{"secret":"FRESH_INPUT_SENTINEL"}}
                """)))
                .contains("Visible choice", "port", "password")
                .doesNotContain("FRESH_INPUT_SENTINEL", "PROTECTED_CALLBACK_SENTINEL", "PROTECTED_TEXT_SENTINEL");
        verify(postRequestedFor(urlEqualTo(CALLBACK_ENDPOINT)).withRequestBody(equalToJson("""
                {"name":"choice","pathVariable":{},"requestParameter":{},"body":{},"attributes":[
                 {"name":"region","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"eu-ref","data":"Europe"}]}]}
                """)));
        verify(postRequestedFor(urlEqualTo(CALLBACK_ENDPOINT)).withRequestBody(equalToJson("""
                {"name":"fields","pathVariable":{},"requestParameter":{},"body":{},"attributes":[
                 {"name":"choice","version":"v3","contentType":"string",
                  "content":[{"contentType":"string","reference":"choice-ref","data":"Visible choice"}]},
                 {"name":"password","version":"v2","contentType":"secret",
                  "content":[{"data":{"secret":"FRESH_INPUT_SENTINEL"}}]}]}
                """)));
        verifyNoCredentialMutation();
    }

    private void stubCredentialSettings(String schema) {
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR + "/attributes/credentialProvider/Basic"))
                .willReturn(okJson(schema)));
    }

    private String credentialSettings(Map<String, Object> arguments) {
        var tools = ToolSpecifications
                .allowed(AccessLevel.READ_ONLY, List.of(credentials))
                .stream()
                .filter(specification -> specification.tool().name().equals("get_credential_attributes"))
                .toList();
        assertThat(tools).hasSize(1);
        return tools
                .get(0)
                .callHandler()
                .apply(null,
                        McpSchema.CallToolRequest.builder("get_credential_attributes").arguments(arguments).build())
                .content()
                .stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .collect(Collectors.joining("\n"));
    }

    private void verifyNoCredentialMutation() {
        verify(0, postRequestedFor(urlEqualTo("/v1/credentials")));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void proxiesAreGatedAndOnlyDescriptionIsEditable() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(proxies.createProxy("{\"name\":\"Proxy\"}")).contains("2.18");
        verify(0, postRequestedFor(anyUrl()));
        assertThat(proxies.updateProxy(ID, "{\"name\":\"Rename\"}")).contains("field");
    }

    @Test
    void proxyCrudAndInstructionsUsePublishedFields() {
        stubFor(post(urlEqualTo("/v1/proxies")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(put(urlEqualTo("/v1/proxies/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"description\":\"Updated\"}")));
        stubFor(get(urlEqualTo("/v1/proxies")).willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"name\":\"Proxy\"}]")));
        stubFor(get(urlEqualTo("/v1/proxies/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"status\":\"connected\"}")));
        stubFor(get(urlEqualTo("/v1/proxies/" + ID + "/instructions"))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"installationInstructions\":\"helm install proxy\"}")));
        stubFor(delete(urlEqualTo("/v1/proxies/" + ID)).willReturn(noContent()));
        assertThat(proxies.createProxy("{\"name\":\"Proxy\",\"description\":\"DMZ\"}")).contains(ID);
        assertThat(proxies.updateProxy(ID, "{\"description\":\"Updated\"}")).contains("Updated");
        assertThat(proxies.listProxies()).contains("Proxy");
        assertThat(proxies.getProxy(ID)).contains("connected");
        assertThat(proxies.getProxyInstallationInstructions(ID)).contains("helm install");
        assertThat(proxies.deleteProxy(ID)).contains("deleted");
    }
}
