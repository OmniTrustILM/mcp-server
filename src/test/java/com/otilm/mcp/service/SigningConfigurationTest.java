package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.SigningConfigurationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
class SigningConfigurationTest {
    static final String PROFILE = "00000000-0000-4000-8000-000000000911";
    static final String CERTIFICATE = "00000000-0000-4000-8000-000000000912";
    static final String CONNECTOR = "00000000-0000-4000-8000-000000000913";
    static final String TSP = "00000000-0000-4000-8000-000000000914";
    static final String BASE = "/v1/signingProfiles";
    private SigningConfigurationTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new SigningConfigurationTool(new SigningConfigurationServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/signingProfiles")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo(BASE + "/certificates/" + CERTIFICATE + "/signatureAttributes"))
                .willReturn(okJson("[]")));
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE
                        + "\",\"name\":\"profile\",\"description\":\"old\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificate\":{\"uuid\":\""
                        + CERTIFICATE
                        + "\"},\"signingOperationAttributes\":[]},\"workflow\":{\"type\":\"raw_signing\"},\"customAttributes\":[]}")));
    }

    @Test
    void createAndUpdateSigningProfilePreserveReadableConfigurationAndReportVersion() {
        stubFor(post(urlEqualTo(BASE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE
                        + "\",\"version\":1,\"attributes\":[{\"data\":\"SENTINEL_SECRET\"}]}")));
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"version\":2}")));
        assertThat(tool
                .createSigningProfile(
                        "{\"name\":\"profile\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificateUuid\":\""
                                + CERTIFICATE + "\"},\"workflow\":{\"type\":\"raw_signing\"}}"))
                .contains(PROFILE, "1")
                .doesNotContain("SENTINEL_SECRET");
        verify(postRequestedFor(urlEqualTo(BASE))
                .withRequestBody(matchingJsonPath("$.signingScheme.signingOperationAttributes", equalToJson("[]"))));
        assertThat(tool.updateSigningProfile(PROFILE, "{\"description\":\"updated\"}")).contains(PROFILE, "2");
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(matchingJsonPath("$.name", equalTo("profile")))
                .withRequestBody(matchingJsonPath("$.signingScheme.certificateUuid", equalTo(CERTIFICATE))));
    }

    @Test
    void partialRecordPolicyPreservesDisabledRecordingAndOtherPolicySettings() {
        String policy = "{\"recordingEnabled\":false,\"recordRequestMetadata\":true,\"recordSignature\":true,\"recordSignedDocument\":false,\"recordDtbs\":true,\"retentionDays\":30,\"deleteAfterRetrieval\":true,\"persistenceMode\":\"best_effort\"}";
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson(
                        "{\"name\":\"profile\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificateUuid\":\""
                                + CERTIFICATE
                                + "\",\"signingOperationAttributes\":[]},\"workflow\":{\"type\":\"raw_signing\"},\"recordPolicy\":"
                                + policy + ",\"customAttributes\":[]}")));
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool.updateSigningProfile(PROFILE, "{\"recordPolicy\":{\"retentionDays\":90}}")).contains(PROFILE);
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(matchingJsonPath("$.recordPolicy", equalToJson(policy.replace(":30", ":90")))));
    }

    @Test
    void recordRetentionNullSupportsDocumentedIndefiniteRetention() {
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool.updateSigningProfile(PROFILE, "{\"recordPolicy\":{\"retentionDays\":null}}")).contains(PROFILE);
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(equalToJson("{\"recordPolicy\":{\"retentionDays\":null}}", false, true)));
    }

    @Test
    void clearDocumentSizeCapPreservesOtherWorkflowAndRecordPolicyFields() {
        String policy = "{\"recordingEnabled\":false,\"recordSignature\":true,\"retentionDays\":30,\"persistenceMode\":\"immediate\"}";
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson(
                        "{\"name\":\"profile\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificateUuid\":\""
                                + CERTIFICATE
                                + "\",\"signingOperationAttributes\":[]},\"workflow\":{\"type\":\"content_signing\",\"family\":\"pades\",\"maxLevel\":\"signed\",\"documentSizeCap\":1024,\"signatureFormattingConnectorUuid\":\""
                                + CONNECTOR + "\",\"signatureFormattingConnectorAttributes\":[]},\"recordPolicy\":"
                                + policy + ",\"customAttributes\":[]}")));
        stubFor(get(urlEqualTo(BASE + "/signatureFormattingConnectors/" + CONNECTOR
                + "/contentSigningFormattingAttributes?family=pades&maxLevel=signed")).willReturn(okJson("[]")));
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool
                .updateSigningProfile(PROFILE,
                        "{\"workflow\":{\"documentSizeCap\":null},\"recordPolicy\":{\"retentionDays\":null}}"))
                .contains(PROFILE);
        String expectedPolicy = policy.replace(":30", ":null");
        String expectedWorkflow = "{\"type\":\"content_signing\",\"family\":\"pades\",\"maxLevel\":\"signed\",\"documentSizeCap\":null,\"signatureFormattingConnectorUuid\":\""
                + CONNECTOR + "\",\"signatureFormattingConnectorAttributes\":[]}";
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(
                        equalToJson("{\"recordPolicy\":" + expectedPolicy + ",\"workflow\":" + expectedWorkflow + "}",
                                false, true)));

    }

    @Test
    void nullRecordPolicyFailsBeforeAnyProfileReadOrMutation() {
        assertThat(tool.updateSigningProfile(PROFILE, "{\"recordPolicy\":null}")).containsIgnoringCase("object");
        verify(0, getRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
        verify(0, putRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
    }

    @Test
    void formattingRouteAndWireFieldNamesFollowConnectedVersion() {
        IlmTestSupport.stubIlmVersion("2.18.0");
        stubFor(get(urlEqualTo(BASE + "/signatureFormatterConnectors/" + CONNECTOR + "/formatterAttributes"))
                .willReturn(okJson(EntityLocationConfigurationTest.SCHEMA)));
        assertThat(tool.getSigningFormattingAttributes(CONNECTOR, null, null, null)).contains("path");
        stubFor(post(urlEqualTo(BASE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool
                .createSigningProfile(
                        "{\"name\":\"timestamp\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificateUuid\":\""
                                + CERTIFICATE
                                + "\"},\"workflow\":{\"type\":\"timestamping\",\"signatureFormattingConnectorUuid\":\""
                                + CONNECTOR + "\"}}"))
                .contains(PROFILE);
        verify(postRequestedFor(urlEqualTo(BASE))
                .withRequestBody(matchingJsonPath("$.workflow.signatureFormatterConnectorUuid", equalTo(CONNECTOR)))
                .withRequestBody(
                        matchingJsonPath("$.workflow.signatureFormatterConnectorAttributes[0].content[0].reference",
                                equalTo("default"))));
        IlmTestSupport.stubIlmVersion("2.19.0");
        stubFor(get(urlEqualTo(BASE + "/signatureFormattingConnectors/" + CONNECTOR + "/formattingAttributes"))
                .willReturn(okJson(EntityLocationConfigurationTest.SCHEMA)));
        assertThat(tool.getSigningFormattingAttributes(CONNECTOR, null, null, null)).contains("path");
    }

    @Test
    void newRecordAndContentFormattingFieldsFailBeforeOldServerMutation() {
        IlmTestSupport.stubIlmVersion("2.18.0");
        assertThat(tool.updateSigningProfile(PROFILE, "{\"recordPolicy\":{\"recordingEnabled\":true}}"))
                .contains("2.19");
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(tool
                .updateSigningProfile(PROFILE,
                        "{\"workflow\":{\"type\":\"content_signing\",\"family\":\"PADES\",\"maxLevel\":\"SIGNED\"}}"))
                .contains("2.20");
        verify(0, putRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(tool.createSigningProfile("{}")).contains("2.18");
    }

    @Test
    void signingCapabilityAndTspAssociationOperationsUseSupportedReadsAndPatches() {
        stubFor(get(urlEqualTo(BASE + "/supportedProtocols?signingWorkflowType=timestamping"))
                .willReturn(okJson("[\"tsp\"]")));
        stubFor(get(urlEqualTo(BASE + "/signingCertificates?signingWorkflowType=timestamping"))
                .willReturn(okJson("[{\"uuid\":\"" + CERTIFICATE
                        + "\",\"commonName\":\"certificate\",\"privateKey\":\"SENTINEL_SECRET\"}]")));
        assertThat(tool.getSigningSchemes()).contains("managed", "delegated", "static_key", "one_time_key");
        assertThat(tool.getSigningProtocols("timestamping")).contains("tsp");
        assertThat(tool.getSigningCertificates("timestamping")).contains(CERTIFICATE).doesNotContain("SENTINEL_SECRET");
        assertThat(tool.getSigningOperationAttributes(CERTIFICATE)).contains("settings");
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE + "/protocols/tsp"))
                .willReturn(okJson("{\"enabled\":true,\"tspProfile\":{\"uuid\":\"" + TSP + "\"}}")));
        stubFor(patch(urlEqualTo(BASE + "/" + PROFILE + "/protocols/tsp/activate/" + TSP))
                .willReturn(okJson("{\"enabled\":true}")));
        stubFor(patch(urlEqualTo(BASE + "/" + PROFILE + "/protocols/tsp/deactivate")).willReturn(noContent()));
        assertThat(tool.getSigningTspActivation(PROFILE)).contains(TSP);
        assertThat(tool.activateSigningTsp(PROFILE, TSP)).contains("accepted");
        assertThat(tool.deactivateSigningTsp(PROFILE)).contains("accepted");
        for (String operation : new String[]{"enable", "disable"}) {
            stubFor(patch(urlEqualTo(BASE + "/" + PROFILE + "/" + operation)).willReturn(noContent()));
        }
        stubFor(delete(urlEqualTo(BASE + "/" + PROFILE)).willReturn(noContent()));
        assertThat(tool.enableSigningProfile(PROFILE)).contains("accepted");
        assertThat(tool.disableSigningProfile(PROFILE)).contains("accepted");
        assertThat(tool.deleteSigningProfile(PROFILE)).contains("Deleted");
    }

    @Test
    void modernContentFormattingAndRecordPolicyAreEncodedWithOwnedSchemas() {
        stubFor(get(urlEqualTo(BASE + "/signatureFormattingConnectors/" + CONNECTOR
                + "/contentSigningFormattingAttributes?family=pades&maxLevel=signed"))
                .willReturn(okJson(EntityLocationConfigurationTest.SCHEMA)));
        stubFor(post(urlEqualTo(BASE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"version\":1}")));
        assertThat(tool.getSigningFormattingAttributes(CONNECTOR, "pades", "signed", null)).contains("path");
        assertThat(tool
                .createSigningProfile(
                        "{\"name\":\"content\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificateUuid\":\""
                                + CERTIFICATE
                                + "\"},\"workflow\":{\"type\":\"content_signing\",\"signatureFormattingConnectorUuid\":\""
                                + CONNECTOR
                                + "\",\"family\":\"pades\",\"maxLevel\":\"signed\"},\"recordPolicy\":{\"recordingEnabled\":true,\"recordSignedDocument\":true}}"))
                .contains(PROFILE);
        verify(postRequestedFor(urlEqualTo(BASE))
                .withRequestBody(
                        matchingJsonPath("$.workflow.signatureFormattingConnectorAttributes[0].content[0].reference",
                                equalTo("default")))
                .withRequestBody(matchingJsonPath("$.recordPolicy.recordSignedDocument", equalTo("true"))));
    }

    @Test
    void workflowSpecificOptionsAndTimeSourceShapesFailBeforeMutation() {
        assertThat(tool
                .updateSigningProfile(PROFILE, "{\"workflow\":{\"type\":\"raw_signing\",\"qualifiedTimestamp\":true}}"))
                .containsIgnoringCase("workflow");
        verify(0, putRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
    }

    @Test
    void modernManagedContentRequiresFamilyAndLevelBeforeMutation() {
        assertThat(tool
                .updateSigningProfile(PROFILE,
                        "{\"workflow\":{\"type\":\"content_signing\",\"signatureFormattingConnectorUuid\":\""
                                + CONNECTOR + "\"}}"))
                .containsIgnoringCase("family");
        verify(0, putRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
    }

    @Test
    void timestampSourceReferenceIsPreservedOnUnrelatedMetadataUpdate() {
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE
                        + "\",\"name\":\"profile\",\"signingScheme\":{\"signingScheme\":\"managed\",\"managedSigningType\":\"static_key\",\"certificate\":{\"uuid\":\""
                        + CERTIFICATE
                        + "\"},\"signingOperationAttributes\":[]},\"workflow\":{\"type\":\"content_signing\",\"family\":\"pades\",\"maxLevel\":\"timestamped\",\"signatureFormattingConnector\":{\"uuid\":\""
                        + CONNECTOR
                        + "\"},\"signatureFormattingConnectorAttributes\":[],\"timestampSource\":{\"type\":\"internal\",\"signingProfile\":{\"uuid\":\""
                        + TSP + "\"}}},\"customAttributes\":[]}")));
        stubFor(get(urlEqualTo(BASE + "/signatureFormattingConnectors/" + CONNECTOR
                + "/contentSigningFormattingAttributes?family=pades&maxLevel=timestamped")).willReturn(okJson("[]")));
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"version\":2}")));
        assertThat(tool.updateSigningProfile(PROFILE, "{\"description\":\"updated\"}")).contains(PROFILE);
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(matchingJsonPath("$.workflow.timestampSource.signingProfileUuid", equalTo(TSP))));
    }

}
