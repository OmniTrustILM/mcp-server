package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.TokenTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
class TokenConfigurationTest {
    private static final String TOKEN = KeyManagementTest.TOKEN;
    private static final String PROFILE = KeyManagementTest.PROFILE;
    private static final String CONNECTOR = "00000000-0000-4000-8000-000000000805";
    private static final String COMPLIANCE = "00000000-0000-4000-8000-000000000806";
    private static final String TOKEN_PATH = "/v1/tokens/" + TOKEN;
    private static final String PROFILE_PATH = TOKEN_PATH + "/tokenProfiles/" + PROFILE;
    private static final String LEGACY_SCHEMA = "/v1/connectors/" + CONNECTOR + "/attributes/cryptographyProvider/SOFT";
    private TokenTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new TokenTool(new TokenServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/tokens")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/tokenProfiles")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo(LEGACY_SCHEMA)).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo(TOKEN_PATH + "/tokenProfiles/attributes")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR))
                .willReturn(okJson("{\"uuid\":\"" + CONNECTOR
                        + "\",\"functionGroups\":[{\"functionGroupCode\":\"cryptographyProvider\",\"kinds\":[\"SOFT\"]}]}")));
        instance();
        profile();
    }

    @Test
    void legacyInstanceCreationAndProviderMetadataNeverExposeAttributes() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubFor(post(urlEqualTo("/v1/tokens"))
                .willReturn(okJson("{\"uuid\":\"" + TOKEN
                        + "\",\"name\":\"new\",\"attributes\":[{\"data\":\"SENTINEL_SECRET\"}]}")));
        assertThat(tool
                .createTokenInstance("{\"name\":\"new\",\"connectorUuid\":\"" + CONNECTOR + "\",\"kind\":\"SOFT\"}"))
                .contains(TOKEN)
                .doesNotContain("SENTINEL_SECRET");
        verify(postRequestedFor(urlEqualTo("/v1/tokens"))
                .withRequestBody(equalToJson("{\"name\":\"new\",\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"SOFT\",\"attributes\":[],\"customAttributes\":[]}")));
        assertThat(tool.getTokenInstance(TOKEN)).contains("token").doesNotContain("SENTINEL_SECRET");
    }

    @Test
    void modernInstanceSchemaIsGatedAndLegacyKindIsValidatedBeforeWrites() {
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(tool.getTokenInstanceAttributes(CONNECTOR, null, null)).contains("2.20");
        assertThat(tool
                .createTokenInstance("{\"name\":\"new\",\"connectorUuid\":\"" + CONNECTOR + "\",\"kind\":\"UNKNOWN\"}"))
                .containsIgnoringCase("kind");
        verify(0, postRequestedFor(urlEqualTo("/v1/tokens")));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/tokens/" + CONNECTOR + "/attributes"))
                .willReturn(okJson(KeyManagementTest.SCHEMA)));
        assertThat(tool.getTokenInstanceAttributes(CONNECTOR, null, null)).contains("algorithm", "RSA");
    }

    @Test
    void completeUpdatePreservesReadableAttributesAndRefusesMaskedSecret() {
        stubFor(get(urlEqualTo(LEGACY_SCHEMA))
                .willReturn(okJson(
                        "[{\"name\":\"setting\",\"type\":\"data\",\"contentType\":\"string\",\"properties\":{}}]")));
        stubFor(put(urlEqualTo(TOKEN_PATH)).willReturn(okJson("{\"uuid\":\"" + TOKEN + "\"}")));
        assertThat(tool.updateTokenInstance(TOKEN, "{\"description\":\"updated\"}")).contains(TOKEN);
        verify(putRequestedFor(urlEqualTo(TOKEN_PATH))
                .withRequestBody(matchingJsonPath("$.name", equalTo("token")))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("readable"))));
        stubFor(get(urlEqualTo(LEGACY_SCHEMA))
                .willReturn(okJson(
                        "[{\"name\":\"setting\",\"type\":\"data\",\"contentType\":\"secret\",\"properties\":{}}]")));
        assertThat(tool.updateTokenInstance(TOKEN, "{\"description\":\"again\"}")).containsIgnoringCase("fresh");
        verify(1, putRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    @Test
    void activationUsesSchemaAndLifecycleReportsAcceptedStatus() {
        stubFor(get(urlEqualTo(TOKEN_PATH + "/activate/attributes"))
                .willReturn(okJson(
                        "[{\"name\":\"pin\",\"type\":\"data\",\"contentType\":\"secret\",\"properties\":{\"required\":true}}]")));
        stubFor(patch(urlEqualTo(TOKEN_PATH + "/activate")).willReturn(noContent()));
        assertThat(tool.getTokenActivationAttributes(TOKEN, null)).contains("pin").doesNotContain("SENTINEL_SECRET");
        assertThat(tool.activateTokenInstance(TOKEN, "{\"pin\":{\"secret\":\"synthetic-pin\"}}"))
                .contains("accepted")
                .doesNotContain("synthetic-pin");
        verify(patchRequestedFor(urlEqualTo(TOKEN_PATH + "/activate"))
                .withRequestBody(equalToJson(
                        "[{\"name\":\"pin\",\"version\":\"v2\",\"contentType\":\"secret\",\"content\":[{\"data\":{\"secret\":\"synthetic-pin\"}}]}]")));
        stubFor(patch(urlEqualTo(TOKEN_PATH + "/deactivate")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(TOKEN_PATH))
                .willReturn(okJson("{\"uuid\":\"" + TOKEN + "\",\"status\":{\"status\":\"Activated\"}}")));
        stubFor(patch(urlEqualTo(TOKEN_PATH + "/sync")).willReturn(noContent()));
        stubFor(delete(urlEqualTo(TOKEN_PATH)).willReturn(noContent()));
        assertThat(tool.deactivateTokenInstance(TOKEN)).contains("accepted");
        assertThat(tool.refreshTokenStatus(TOKEN)).contains("Activated");
        assertThat(tool.syncTokenKeys(TOKEN)).contains("accepted");
        assertThat(tool.deleteTokenInstance(TOKEN)).contains("Deleted");
    }

    @Test
    void profileCreateUpdateEnableUsageDeletePreserveCompleteComposition() {
        stubFor(post(urlEqualTo(TOKEN_PATH + "/tokenProfiles"))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"name\":\"profile\"}")));
        assertThat(tool.createTokenProfile(TOKEN, "{\"name\":\"profile\",\"usage\":[\"sign\"],\"enabled\":true}"))
                .contains(PROFILE);
        verify(postRequestedFor(urlEqualTo(TOKEN_PATH + "/tokenProfiles"))
                .withRequestBody(equalToJson(
                        "{\"name\":\"profile\",\"usage\":[\"sign\"],\"enabled\":true,\"attributes\":[],\"customAttributes\":[]}")));
        stubFor(put(urlEqualTo(PROFILE_PATH)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool.updateTokenProfile(TOKEN, PROFILE, "{\"description\":\"updated\"}")).contains(PROFILE);
        verify(putRequestedFor(urlEqualTo(PROFILE_PATH))
                .withRequestBody(equalToJson(
                        "{\"description\":\"updated\",\"enabled\":true,\"usage\":[\"sign\"],\"attributes\":[],\"customAttributes\":[]}")));
        stubFor(patch(urlEqualTo(PROFILE_PATH + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(PROFILE_PATH + "/disable")).willReturn(noContent()));
        stubFor(put(urlEqualTo(PROFILE_PATH + "/usages")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/tokenProfiles/" + PROFILE)).willReturn(noContent()));
        assertThat(tool.enableTokenProfile(TOKEN, PROFILE)).contains("accepted");
        assertThat(tool.disableTokenProfile(TOKEN, PROFILE)).contains("accepted");
        assertThat(tool.updateTokenProfileUsages(TOKEN, PROFILE, "[\"verify\"]")).contains("accepted");
        assertThat(tool.deleteTokenProfile(PROFILE)).contains("Deleted");
        verify(putRequestedFor(urlEqualTo(PROFILE_PATH + "/usages"))
                .withRequestBody(equalToJson("{\"usage\":[\"verify\"]}")));
    }

    @Test
    void profileCapabilitiesAndComplianceUseMetadataAndTargetedRoutes() {
        stubFor(get(urlEqualTo("/v1/tokenProfiles"))
                .willReturn(okJson("[{\"uuid\":\"" + PROFILE + "\",\"name\":\"profile\"}]")));
        stubFor(get(urlEqualTo(PROFILE_PATH + "/keys/types")).willReturn(okJson("[\"keyPair\",\"secret\"]")));
        assertThat(tool.listTokenProfiles()).contains("profile");
        assertThat(tool.getTokenProfile(TOKEN, PROFILE)).contains("profile").doesNotContain("SENTINEL_SECRET");
        assertThat(tool.getTokenProfileAttributes(TOKEN, null)).contains("settings");
        assertThat(tool.getSupportedKeyTypes(TOKEN, PROFILE)).contains("keyPair", "secret");
        assertThat(tool.getTokenProfileCapabilities(TOKEN, PROFILE))
                .contains("RSA", "sign")
                .doesNotContain("SENTINEL_SECRET");
        String association = "/v2/complianceProfiles/" + COMPLIANCE + "/associations/tokenProfiles/" + PROFILE;
        stubFor(patch(urlEqualTo(association)).willReturn(noContent()));
        stubFor(delete(urlEqualTo(association)).willReturn(noContent()));
        stubFor(get(urlEqualTo("/v2/complianceProfiles/associations/tokenProfiles/" + PROFILE))
                .willReturn(okJson("[{\"uuid\":\"" + COMPLIANCE + "\",\"name\":\"policy\"}]")));
        assertThat(tool.getTokenProfileCompliance(PROFILE)).contains("policy");
        assertThat(tool.associateTokenProfileCompliance(PROFILE, COMPLIANCE)).contains("accepted");
        assertThat(tool.detachTokenProfileCompliance(PROFILE, COMPLIANCE)).contains("accepted");
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(tool.getSupportedKeyTypes(TOKEN, PROFILE)).contains("2.20");
        assertThat(tool.getTokenProfileCapabilities(TOKEN, PROFILE)).contains("2.20");
    }

    @Test
    void invalidUsagesUnknownFieldsAndProtectedUpstreamErrorsAreSafe() {
        assertThat(tool.updateTokenProfileUsages(TOKEN, PROFILE, "[\"SENTINEL_SECRET\"]"))
                .doesNotContain("SENTINEL_SECRET");
        assertThat(tool.createTokenProfile(TOKEN, "{\"name\":\"x\",\"arbitraryUrl\":\"SENTINEL_SECRET\"}"))
                .doesNotContain("SENTINEL_SECRET");
        stubFor(patch(urlEqualTo(TOKEN_PATH + "/deactivate"))
                .willReturn(aResponse().withStatus(502).withBody("SENTINEL_SECRET")));
        assertThat(tool.deactivateTokenInstance(TOKEN)).contains("502").doesNotContain("SENTINEL_SECRET");
        verify(0, putRequestedFor(urlEqualTo(PROFILE_PATH + "/usages")));
    }

    @Test
    void modernInstanceCallbacksUseReleasedInterfaceContextBeforeCreate() {
        String interfaceUuid = "00000000-0000-4000-8000-000000000807";
        stubFor(get(urlEqualTo("/v2/connectors/" + CONNECTOR))
                .willReturn(okJson("{\"interfaces\":[{\"uuid\":\"" + interfaceUuid
                        + "\",\"code\":\"cryptography\",\"version\":\"v2\"}]}")));
        stubFor(get(urlEqualTo("/v1/tokens/" + CONNECTOR + "/attributes"))
                .willReturn(
                        okJson("""
                                [{"name":"algorithm","type":"data","version":3,"contentType":"string","properties":{"list":true,"required":true},"attributeCallback":{"dependsOn":[]}}]
                                """)));
        stubFor(post(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .willReturn(okJson("[{\"reference\":\"rsa-ref\",\"contentType\":\"string\",\"data\":\"RSA\"}]")));
        stubFor(post(urlEqualTo("/v1/tokens")).willReturn(okJson("{\"uuid\":\"" + TOKEN + "\"}")));
        assertThat(tool
                .createTokenInstance("{\"name\":\"modern\",\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"attributes\":{\"algorithm\":\"RSA\"}}"))
                .contains(TOKEN);
        verify(postRequestedFor(urlEqualTo("/v2/connectors/" + CONNECTOR + "/callback"))
                .withRequestBody(matchingJsonPath("$.interfaceUuid", equalTo(interfaceUuid))));
        verify(postRequestedFor(urlEqualTo("/v1/tokens"))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].reference", equalTo("rsa-ref"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.19.0", "2.20.0"})
    void profileCallbacksUseTokenProfileScopeWithTokenInstanceParent(String version) {
        IlmTestSupport.stubIlmVersion(version);
        stubFor(get(urlEqualTo(TOKEN_PATH + "/tokenProfiles/attributes")).willReturn(okJson("""
                [{"name":"choices","type":"group","attributeCallback":{"dependsOn":[]}}]
                """)));
        stubFor(post(urlEqualTo("/v1/tokenProfiles/" + TOKEN + "/callback"))
                .willReturn(okJson(KeyManagementTest.SCHEMA)));
        assertThat(tool.getTokenProfileAttributes(TOKEN, null)).contains("algorithm", "RSA");
        verify(postRequestedFor(urlEqualTo("/v1/tokenProfiles/" + TOKEN + "/callback")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.17.0", "2.18.0"})
    void legacyProfileSettingsCreateAndUpdateUseConnectorCallbacks(String version) {
        IlmTestSupport.stubIlmVersion(version);
        String callback = "/v1/connectors/" + CONNECTOR + "/cryptographyProvider/SOFT/callback";
        stubFor(get(urlEqualTo(TOKEN_PATH + "/tokenProfiles/attributes")).willReturn(okJson("""
                [{"name":"choices","type":"group","attributeCallback":{"dependsOn":[]}}]
                """)));
        stubFor(post(urlEqualTo(callback)).willReturn(okJson("""
                [{"name":"algorithm","type":"data","contentType":"string","properties":{},"content":[{"data":"RSA"}]}]
                """)));
        stubFor(post(urlEqualTo(TOKEN_PATH + "/tokenProfiles")).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        stubFor(put(urlEqualTo(PROFILE_PATH)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool.getTokenProfileAttributes(TOKEN, null)).contains("algorithm", "RSA");
        assertThat(tool.createTokenProfile(TOKEN, "{\"name\":\"legacy\"}")).contains(PROFILE);
        assertThat(tool.updateTokenProfile(TOKEN, PROFILE, "{\"description\":\"legacy\"}")).contains(PROFILE);
        verify(3, postRequestedFor(urlEqualTo(callback)));
        verify(0, postRequestedFor(urlEqualTo("/v1/tokenProfiles/" + TOKEN + "/callback")));
        verify(postRequestedFor(urlEqualTo(TOKEN_PATH + "/tokenProfiles"))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("RSA"))));
        verify(putRequestedFor(urlEqualTo(PROFILE_PATH))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("RSA"))));
    }

    private void instance() {
        stubFor(get(urlEqualTo(TOKEN_PATH))
                .willReturn(okJson("{\"uuid\":\"" + TOKEN
                        + "\",\"name\":\"token\",\"description\":\"old\",\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"SOFT\",\"customAttributes\":[],\"attributes\":[{\"name\":\"setting\",\"content\":[{\"data\":\"readable\"}]}],\"metadata\":[{\"content\":\"SENTINEL_SECRET\"}]}")));
    }

    private void profile() {
        stubFor(get(urlEqualTo(PROFILE_PATH))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE
                        + "\",\"name\":\"profile\",\"description\":\"old\",\"enabled\":true,\"usages\":[\"sign\"],\"attributes\":[],\"customAttributes\":[],\"keyTransfer\":{\"importAvailable\":true,\"exportAvailable\":true,\"exportableKeyTypes\":{\"keyPair\":[\"RSA\"]}},\"keyData\":\"SENTINEL_SECRET\"}")));
    }
}
