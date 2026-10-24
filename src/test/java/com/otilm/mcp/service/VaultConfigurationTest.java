package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.VaultConfigurationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
class VaultConfigurationTest {
    static final String VAULT = "613094ba-8f40-4c51-ab36-887a46a32475";
    static final String CONNECTOR = "32c0396e-d280-4fb4-a878-826eb040b957";
    static final String INTERFACE = "4fb266cb-2f3a-49b5-b172-d43dfc3f17b2";
    static final String PROFILE = "75124e12-4efa-4ad3-8aa7-ae0337a92978";
    private VaultConfigurationTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new VaultConfigurationTool(new VaultConfigurationServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build())));
        stubs();
    }

    @Test
    void readsDetailsAndSchemasWithProtectedValuesOmitted() {
        assertThat(tool.getVaultInstance(VAULT))
                .contains("Vault", VAULT)
                .doesNotContain("PROTECTED_SENTINEL", "attributes:");
        assertThat(tool.getVaultProfile(VAULT, PROFILE))
                .contains("Profile", PROFILE)
                .doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool.getVaultAttributes(CONNECTOR, INTERFACE, null))
                .contains("region", "password")
                .doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool.getVaultProfileAttributes(VAULT, null)).contains("region").doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool.getSecretAttributes(VAULT, PROFILE, "generic", null))
                .contains("region")
                .doesNotContain("PROTECTED_SENTINEL");
    }

    @Test
    void createsInstancesAndProfilesWithInterfaceAndEncodedValues() {
        stubFor(post(urlEqualTo("/v1/vaults")).willReturn(okJson("{\"uuid\":\"" + VAULT + "\",\"name\":\"Vault\"}")));
        stubFor(post(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles"))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"name\":\"Profile\"}")));
        assertThat(tool
                .createVaultInstance(
                        """
                                {"name":"Vault","connectorUuid":"32c0396e-d280-4fb4-a878-826eb040b957",
                                "interfaceUuid":"4fb266cb-2f3a-49b5-b172-d43dfc3f17b2","attributes":{"region":"eu","password":{"secret":"fresh"}}}
                                """))
                .contains(VAULT)
                .doesNotContain("fresh");
        verify(postRequestedFor(urlEqualTo("/v1/vaults"))
                .withRequestBody(matchingJsonPath("$.interfaceUuid", equalTo(INTERFACE)))
                .withRequestBody(matchingJsonPath("$.attributes[0].version", equalTo("v2")))
                .withRequestBody(matchingJsonPath("$.attributes[1].content[0].data.secret", equalTo("fresh"))));
        assertThat(tool
                .createVaultProfile(VAULT,
                        "{\"name\":\"Profile\",\"attributes\":{\"region\":\"eu\",\"password\":{\"secret\":\"fresh\"}}}"))
                .contains(PROFILE);
        verify(postRequestedFor(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles"))
                .withRequestBody(matchingJsonPath("$.name", equalTo("Profile"))));
    }

    @Test
    void updatesPreserveReadableValuesButRequireFreshProtectedValuesBeforePut() {
        stubFor(put(urlEqualTo("/v1/vaults/" + VAULT)).willReturn(okJson("{\"uuid\":\"" + VAULT + "\"}")));
        stubFor(put(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool.updateVaultInstance(VAULT, "{\"description\":\"updated\"}")).containsIgnoringCase("fresh");
        verify(0, putRequestedFor(anyUrl()));
        assertThat(tool.updateVaultInstance(VAULT, "{\"attributes\":{\"password\":{\"secret\":\"fresh\"}}}"))
                .contains(VAULT);
        verify(putRequestedFor(urlEqualTo("/v1/vaults/" + VAULT))
                .withRequestBody(matchingJsonPath("$.description", equalTo("existing")))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("eu")))
                .withRequestBody(matchingJsonPath("$.customAttributes[0].content[0].data", equalTo("keep"))));
        assertThat(tool.updateVaultProfile(VAULT, PROFILE, "{\"attributes\":{\"password\":{\"secret\":\"fresh\"}}}"))
                .contains(PROFILE);
        verify(putRequestedFor(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE))
                .withRequestBody(matchingJsonPath("$.description", equalTo("existing"))));
    }

    @Test
    void usesActualStateDeletePathsAndDoesNotEchoFailures() {
        String profilePath = "/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE;
        stubFor(patch(urlEqualTo(profilePath + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(profilePath + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo(profilePath)).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/vaults/" + VAULT))
                .willReturn(aResponse().withStatus(422).withBody("{\"message\":\"PROTECTED_SENTINEL\"}")));
        assertThat(tool.enableVaultProfile(VAULT, PROFILE)).contains(PROFILE);
        assertThat(tool.disableVaultProfile(VAULT, PROFILE)).contains(PROFILE);
        assertThat(tool.deleteVaultProfile(VAULT, PROFILE)).contains(PROFILE);
        assertThat(tool.deleteVaultInstance(VAULT)).contains("422").doesNotContain("PROTECTED_SENTINEL");
        verify(patchRequestedFor(urlEqualTo(profilePath + "/enable")));
        verify(patchRequestedFor(urlEqualTo(profilePath + "/disable")));
        verify(deleteRequestedFor(urlEqualTo(profilePath)));
    }

    @Test
    void rejectsUnknownInputsMalformedValuesAndWrongInterfaceBeforeMutation() {
        assertThat(tool.createVaultInstance("{PROTECTED_SENTINEL")).doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool.updateVaultInstance(VAULT, "{\"connectorUuid\":\"PROTECTED_SENTINEL\"}"))
                .doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool.getSecretAttributes(VAULT, PROFILE, "PROTECTED_SENTINEL", null))
                .doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool
                .createVaultInstance("{\"name\":\"v\",\"connectorUuid\":\"" + CONNECTOR + "\",\"interfaceUuid\":\""
                        + PROFILE + "\"}"))
                .containsIgnoringCase("interface");
        verify(0, postRequestedFor(urlEqualTo("/v1/vaults")));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void emptyRedactedProtectedRecordsAndProtectedCustomValuesRequireFreshReplacement() {
        stubFor(get(urlEqualTo("/v1/vaults/" + VAULT))
                .willReturn(okJson(detail("Vault", VAULT)
                        .replace("\"name\":\"password\",\"content\":[{\"data\":{\"secret\":\"PROTECTED_SENTINEL\"}}]",
                                "\"name\":\"password\",\"content\":[]"))));
        assertThat(tool.updateVaultInstance(VAULT, "{\"description\":\"new\"}")).containsIgnoringCase("fresh");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/vaults")).willReturn(okJson("""
                [{"name":"tag","type":"custom","version":2,"contentType":"secret","properties":{}}]
                """)));
        assertThat(tool.updateVaultInstance(VAULT, "{\"attributes\":{\"password\":{\"secret\":\"fresh\"}}}"))
                .containsIgnoringCase("fresh");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void callbacksUseConnectorInterfaceAndOmittedReadableDependenciesFromCurrentValues() {
        String definitions = schema().stripTrailing();
        definitions = definitions.substring(0, definitions.length() - 1) + """
                ,{"name":"providerFields","type":"group","attributeCallback":{"callbackContext":"provider/schema",
                "callbackMethod":"POST","mappings":[{"from":"region","to":"zone","targets":["body"]}]}}]
                """;
        stubFor(get(urlEqualTo("/v1/vaults/" + CONNECTOR + "/attributes")).willReturn(okJson(definitions)));
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback")).willReturn(okJson("""
                [{"name":"size","type":"data","version":3,"contentType":"integer","properties":{}}]
                """)));
        stubFor(put(urlEqualTo("/v1/vaults/" + VAULT)).willReturn(okJson("{\"uuid\":\"" + VAULT + "\"}")));
        assertThat(
                tool.updateVaultInstance(VAULT, "{\"attributes\":{\"password\":{\"secret\":\"fresh\"},\"size\":2048}}"))
                .contains("Updated", VAULT);
        verify(postRequestedFor(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .withRequestBody(matchingJsonPath("$.interfaceUuid", equalTo(INTERFACE)))
                .withRequestBody(matchingJsonPath("$.body.zone", equalTo("eu"))));
        verify(putRequestedFor(urlEqualTo("/v1/vaults/" + VAULT))
                .withRequestBody(matchingJsonPath("$.attributes[2].content[0].data", equalTo("2048"))));
    }

    private void stubs() {
        stubFor(get(urlEqualTo("/v2/connectors/" + CONNECTOR))
                .willReturn(okJson("{\"interfaces\":[{\"uuid\":\"" + INTERFACE + "\",\"code\":\"secret\"}]}")));
        stubFor(get(urlEqualTo("/v1/vaults/" + VAULT)).willReturn(okJson(detail("Vault", VAULT))));
        stubFor(get(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE))
                .willReturn(okJson(detail("Profile", PROFILE))));
        for (String path : new String[]{
                "/v1/vaults/" + CONNECTOR + "/attributes",
                "/v1/vaults/" + VAULT + "/vaultProfiles/attributes",
                "/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE + "/secrets/generic/attributes"}) {
            stubFor(get(urlEqualTo(path)).willReturn(okJson(schema())));
        }
        for (String resource : new String[]{"vaults", "vaultProfiles", "secrets"}) {
            stubFor(get(urlEqualTo("/v1/attributes/custom/resources/" + resource)).willReturn(okJson("""
                    [{"uuid":"11c31b4e-d8a4-4ee8-a478-da232174c5aa","name":"tag","type":"custom","version":2,
                    "contentType":"string","properties":{"required":false}}]
                    """)));
        }
    }

    static String detail(String name, String uuid) {
        return """
                {"uuid":"%s","name":"%s","description":"existing","enabled":true,
                "connector":{"uuid":"32c0396e-d280-4fb4-a878-826eb040b957","name":"Provider"},
                "connectorInterface":{"uuid":"4fb266cb-2f3a-49b5-b172-d43dfc3f17b2","code":"secret","version":"1.0"},
                "vaultInstance":{"uuid":"613094ba-8f40-4c51-ab36-887a46a32475","name":"Vault"},
                "attributes":[{"name":"region","content":[{"data":"eu"}]},{"name":"password","content":[{"data":{"secret":"PROTECTED_SENTINEL"}}]}],
                "customAttributes":[{"name":"tag","content":[{"data":"keep"}]}],"secret":{"content":"PROTECTED_SENTINEL"}}
                """
                .formatted(uuid, name);
    }

    static String schema() {
        return """
                [{"uuid":"b368ff52-002a-4f7c-8ac7-87cb6197bb71","name":"region","type":"data","version":2,
                "contentType":"string","properties":{"required":true}},
                {"uuid":"989e3aeb-8f90-46bc-a09d-d00c46aa0e5d","name":"password","type":"data","version":2,
                "contentType":"secret","properties":{"required":false},"content":[{"data":{"secret":"PROTECTED_SENTINEL"}}]}]
                """;
    }
}
