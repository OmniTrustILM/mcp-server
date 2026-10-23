package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.EnrollmentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class EnrollmentConfigurationTest {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private EnrollmentTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new EnrollmentTool(new EnrollmentServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlPathMatching("/v1/attributes/custom/resources/.*")).willReturn(okJson("[]")));
    }

    @Test
    void acmeUpdatePreservesOmittedUrlsAndDefaultRa() {
        stubFor(get(urlEqualTo("/v1/acmeProfiles/" + ID))
                .willReturn(
                        okJson("""
                                {"name":"ACME","description":"Original","websiteUrl":"https://example.test","raProfile":{"uuid":"%s"},
                                "requireContact":true,"issueCertificateAttributes":[],"revokeCertificateAttributes":[],"customAttributes":[]}
                                """
                                .formatted(ID))));
        stubFor(get(urlEqualTo("/v1/raProfiles/" + ID))
                .willReturn(okJson("{\"authorityInstanceUuid\":\"" + ID + "\"}")));
        stubFor(get(urlPathMatching("/v1/authorities/.*/raProfiles/.*/attributes/.*")).willReturn(okJson("[]")));
        stubFor(put(urlEqualTo("/v1/acmeProfiles/" + ID)).willReturn(okJson("{}")));
        assertThat(tool.updateAcmeProfile(ID, "{\"description\":\"Revised\"}")).contains(ID);
        verify(putRequestedFor(urlEqualTo("/v1/acmeProfiles/" + ID))
                .withRequestBody(matchingJsonPath("$.websiteUrl", equalTo("https://example.test")))
                .withRequestBody(matchingJsonPath("$.raProfileUuid", equalTo(ID)))
                .withRequestBody(matchingJsonPath("$.requireContact", equalTo("true"))));
    }

    @Test
    void oldPlatformsRejectUnsupportedEnrollmentFieldsBeforeWrite() {
        IlmTestSupport.stubIlmVersion("2.18.0");
        assertThat(tool.createAcmeProfile("{\"name\":\"ACME\",\"eabSecretUuids\":[]}")).contains("2.20");
        assertThat(tool.createScepProfile("{\"name\":\"SCEP\",\"enableChallengePassword\":true}")).contains("2.19");
        assertThat(tool.createCmpProfile("{\"name\":\"CMP\",\"challengeSource\":\"certificateRegistration\"}"))
                .contains("2.20");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void cmpUpdatesRequireFreshSharedSecretInsteadOfReplayingMaskedValue() {
        stubFor(get(urlEqualTo("/v1/cmpProfiles/" + ID))
                .willReturn(
                        okJson("""
                                {"name":"CMP","variant":"v2","requestProtectionMethod":"sharedSecret","responseProtectionMethod":"sharedSecret",
                                "sharedSecret":"********","issueCertificateAttributes":[],"revokeCertificateAttributes":[],"customAttributes":[]}
                                """)));
        assertThat(tool.updateCmpProfile(ID, "{\"description\":\"Revised\"}")).contains("sharedSecret", "fresh");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void oldScepReplacementCannotSilentlyClearAnUnobservableChallengePassword() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubFor(get(urlEqualTo("/v1/scepProfiles/" + ID))
                .willReturn(okJson("{\"name\":\"SCEP\",\"enableIntune\":false,\"customAttributes\":[]}")));
        assertThat(tool.updateScepProfile(ID, "{\"description\":\"Revised\"}")).contains("challengePassword", "fresh");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void protocolLifecyclesUseSeparateReleasedRoutesAndReturnMetadataOnly() {
        for (String protocol : new String[]{"acme", "scep", "cmp"}) {
            String base = "/v1/" + protocol + "Profiles";
            stubFor(get(urlEqualTo(base)).willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"name\":\"Enrollment\"}]")));
            stubFor(get(urlEqualTo(base + "/" + ID))
                    .willReturn(
                            okJson("{\"uuid\":\"" + ID + "\",\"name\":\"Enrollment\",\"sharedSecret\":\"SENTINEL\"}")));
            stubFor(post(urlEqualTo(base))
                    .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"sharedSecret\":\"SENTINEL\"}")));
            stubFor(delete(urlEqualTo(base + "/" + ID)).willReturn(noContent()));
            stubFor(patch(urlEqualTo(base + "/" + ID + "/enable")).willReturn(noContent()));
            stubFor(patch(urlEqualTo(base + "/" + ID + "/disable")).willReturn(noContent()));
            stubFor(patch(urlEqualTo(base + "/" + ID + (protocol.equals("acme") ? "/raprofile/" : "/raProfiles/") + ID))
                    .willReturn(noContent()));
        }
        assertThat(tool.listAcmeProfiles()).contains("Enrollment");
        assertThat(tool.listScepProfiles()).contains("Enrollment");
        assertThat(tool.listCmpProfiles()).contains("Enrollment");
        assertThat(tool.getAcmeProfile(ID)).contains(ID).doesNotContain("SENTINEL");
        assertThat(tool.getScepProfile(ID)).contains(ID).doesNotContain("SENTINEL");
        assertThat(tool.getCmpProfile(ID)).contains(ID).doesNotContain("SENTINEL");
        assertThat(tool.createAcmeProfile("{\"name\":\"Enrollment\"}")).contains(ID).doesNotContain("SENTINEL");
        assertThat(tool
                .createScepProfile("{\"name\":\"Enrollment\",\"caCertificateUuid\":\"" + ID
                        + "\",\"enableChallengePassword\":false}"))
                .contains(ID)
                .doesNotContain("SENTINEL");
        assertThat(tool
                .createCmpProfile(
                        "{\"name\":\"Enrollment\",\"variant\":\"v2\",\"requestProtectionMethod\":\"signature\",\"responseProtectionMethod\":\"signature\",\"signingCertificateUuid\":\""
                                + ID + "\"}"))
                .contains(ID)
                .doesNotContain("SENTINEL");
        assertThat(tool.enableAcmeProfile(ID)).contains("true");
        assertThat(tool.disableAcmeProfile(ID)).contains("false");
        assertThat(tool.enableScepProfile(ID)).contains("true");
        assertThat(tool.disableScepProfile(ID)).contains("false");
        assertThat(tool.enableCmpProfile(ID)).contains("true");
        assertThat(tool.disableCmpProfile(ID)).contains("false");
        assertThat(tool.setAcmeDefaultRaProfile(ID, ID)).contains(ID);
        assertThat(tool.setScepDefaultRaProfile(ID, ID)).contains(ID);
        assertThat(tool.setCmpDefaultRaProfile(ID, ID)).contains(ID);
        assertThat(tool.deleteAcmeProfile(ID)).contains(ID);
        assertThat(tool.deleteScepProfile(ID)).contains(ID);
        assertThat(tool.deleteCmpProfile(ID)).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/cmpProfiles"))
                .withRequestBody(matchingJsonPath("$.signingCertificateUuid", equalTo(ID))));
    }

    @Test
    void freshScepPasswordAndCmpSharedSecretAreSerializedButNeverReturned() {
        stubFor(get(urlEqualTo("/v1/scepProfiles/" + ID))
                .willReturn(okJson(
                        "{\"name\":\"SCEP\",\"enableChallengePassword\":true,\"includeCaCertificate\":true,\"renewThreshold\":7,\"customAttributes\":[],\"issueCertificateAttributes\":[]}")));
        stubFor(put(urlEqualTo("/v1/scepProfiles/" + ID)).willReturn(okJson("{\"challengePassword\":\"SENTINEL\"}")));
        stubFor(get(urlEqualTo("/v1/cmpProfiles/" + ID))
                .willReturn(okJson(
                        "{\"name\":\"CMP\",\"variant\":\"v2\",\"requestProtectionMethod\":\"sharedSecret\",\"responseProtectionMethod\":\"sharedSecret\",\"customAttributes\":[],\"issueCertificateAttributes\":[],\"revokeCertificateAttributes\":[]}")));
        stubFor(put(urlEqualTo("/v1/cmpProfiles/" + ID)).willReturn(okJson("{\"sharedSecret\":\"SENTINEL\"}")));
        assertThat(tool.updateScepProfile(ID, "{\"challengePassword\":\"fresh-scep-password\"}"))
                .contains(ID)
                .doesNotContain("SENTINEL", "fresh-scep-password");
        assertThat(tool.updateCmpProfile(ID, "{\"sharedSecret\":\"fresh-cmp-secret\"}"))
                .contains(ID)
                .doesNotContain("SENTINEL", "fresh-cmp-secret");
        verify(putRequestedFor(urlEqualTo("/v1/scepProfiles/" + ID))
                .withRequestBody(matchingJsonPath("$.challengePassword", equalTo("fresh-scep-password")))
                .withRequestBody(matchingJsonPath("$.includeCaCertificate", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.renewalThreshold", equalTo("7"))));
        verify(putRequestedFor(urlEqualTo("/v1/cmpProfiles/" + ID))
                .withRequestBody(matchingJsonPath("$.sharedSecret", equalTo("fresh-cmp-secret"))));
    }

    @Test
    void rejectsRequestEnvelopesInsteadOfSilentlyDroppingCallerOperationValues() {
        assertThat(tool.createAcmeProfile("{\"name\":\"ACME\",\"issueCertificateAttributes\":[]}")).contains("object");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void eabKeyIsExplicitSensitiveOutputAndCertificateChoicesAreMetadata() throws Exception {
        stubFor(post(urlEqualTo("/v1/acmeProfiles/eabKeys")).willReturn(okJson("{\"key\":\"synthetic-eab-key\"}")));
        stubFor(get(urlEqualTo("/v1/scepProfiles/caCertificates"))
                .willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"commonName\":\"SCEP CA\"}]")));
        stubFor(get(urlEqualTo("/v1/cmpProfiles/signingCertificates"))
                .willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"commonName\":\"CMP signer\"}]")));
        assertThat(tool.generateAcmeEabKey()).contains("synthetic-eab-key");
        assertThat(EnrollmentTool.class
                .getMethod("generateAcmeEabKey")
                .isAnnotationPresent(com.otilm.mcp.tool.SensitiveOutput.class)).isTrue();
        assertThat(tool.getEnrollmentCertificateChoices("scep")).contains(ID, "SCEP CA");
        assertThat(tool.getEnrollmentCertificateChoices("cmp")).contains(ID, "CMP signer");
    }

    @Test
    void retainedEnrollmentOperationValuesCanResolveProviderCallbacks() {
        stubFor(get(urlEqualTo("/v1/acmeProfiles/" + ID))
                .willReturn(
                        okJson("""
                                {"name":"ACME","raProfile":{"uuid":"%s"},"issueCertificateAttributes":[{"name":"template","content":[{"data":"existing-template"}]}],"revokeCertificateAttributes":[],"customAttributes":[]}
                                """
                                .formatted(ID))));
        stubFor(get(urlEqualTo("/v1/raProfiles/" + ID))
                .willReturn(okJson("{\"authorityInstanceUuid\":\"" + ID + "\"}")));
        stubFor(get(urlEqualTo("/v1/authorities/" + ID))
                .willReturn(okJson("{\"connectorInterface\":{\"uuid\":\"" + ID + "\"}}")));
        stubFor(get(urlEqualTo("/v1/authorities/" + ID + "/raProfiles/" + ID + "/attributes/issue"))
                .willReturn(
                        okJson("""
                                [{"name":"template","version":2,"type":"data","contentType":"string","properties":{}},
                                {"name":"callback","type":"group","attributeCallback":{"callbackContext":"options","callbackMethod":"GET","mappings":[{"from":"template","to":"template","targets":["body"]}]}}]
                                """)));
        stubFor(post(urlEqualTo("/v1/certificates/" + ID + "/callback")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/authorities/" + ID + "/raProfiles/" + ID + "/attributes/revoke"))
                .willReturn(okJson("[]")));
        stubFor(put(urlEqualTo("/v1/acmeProfiles/" + ID)).willReturn(okJson("{}")));
        assertThat(tool.updateAcmeProfile(ID, "{\"description\":\"Revised\"}")).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/certificates/" + ID + "/callback"))
                .withRequestBody(matchingJsonPath("$.body.template", equalTo("existing-template"))));
    }

    @Test
    void cmpRegistrationChallengeCreateAndMetadataUpdateDoNotRequireProfileSecret() {
        String profile = "{\"name\":\"CMP registration\",\"variant\":\"v2\",\"requestProtectionMethod\":\"sharedSecret\",\"responseProtectionMethod\":\"sharedSecret\",\"challengeSource\":\"certificateRegistration\"}";
        stubFor(post(urlEqualTo("/v1/cmpProfiles")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(get(urlEqualTo("/v1/cmpProfiles/" + ID)).willReturn(okJson(profile)));
        stubFor(put(urlEqualTo("/v1/cmpProfiles/" + ID)).willReturn(okJson("{}")));
        assertThat(tool.createCmpProfile(profile)).contains(ID);
        assertThat(tool.updateCmpProfile(ID, "{\"description\":\"Revised\"}")).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/cmpProfiles"))
                .withRequestBody(matchingJsonPath("$.challengeSource", equalTo("certificateRegistration")))
                .withRequestBody(notMatching(".*sharedSecret\"\\s*:.*")));
        verify(putRequestedFor(urlEqualTo("/v1/cmpProfiles/" + ID))
                .withRequestBody(matchingJsonPath("$.description", equalTo("Revised")))
                .withRequestBody(matchingJsonPath("$.requestProtectionMethod", equalTo("sharedSecret")))
                .withRequestBody(notMatching(".*sharedSecret\"\\s*:.*")));
    }

    @Test
    void cmpRegistrationChallengeRejectsProfileSecretAndUnsupportedProtectionBeforeWrites() {
        String base = "{\"name\":\"CMP registration\",\"variant\":\"v2\",\"requestProtectionMethod\":\"sharedSecret\",\"responseProtectionMethod\":\"sharedSecret\",\"challengeSource\":\"certificateRegistration\"";
        assertThat(tool.createCmpProfile(base + ",\"sharedSecret\":\"synthetic-secret\"}"))
                .contains("sharedSecret", "certificateRegistration")
                .doesNotContain("synthetic-secret");
        assertThat(tool.createCmpProfile((base + "}").replace("\"v2\"", "\"v3\""))).contains("v2");
        assertThat(tool
                .createCmpProfile((base + "}")
                        .replace("\"requestProtectionMethod\":\"sharedSecret\"",
                                "\"requestProtectionMethod\":\"signature\"")))
                .contains("sharedSecret");
        verify(0, postRequestedFor(anyUrl()));
    }

}
