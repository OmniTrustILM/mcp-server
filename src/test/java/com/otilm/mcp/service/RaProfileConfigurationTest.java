package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AuthorityTool;
import com.otilm.mcp.tool.RaProfileTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class RaProfileConfigurationTest {
    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String R = "22222222-2222-4222-8222-222222222222";
    private static final String BASE = "/v1/authorities/" + A + "/raProfiles/" + R;
    private AuthorityTool authorities;
    private RaProfileTool profiles;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        ConfigurationApiClient client = new ConfigurationApiClient(
                IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build());
        PlatformVersionCheck versions = new PlatformVersionCheck(IlmTestSupport.client(wm));
        authorities = new AuthorityTool(new AuthorityServiceImpl(client, versions));
        profiles = new RaProfileTool(new RaProfileServiceImpl(client, versions));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlPathMatching("/v1/attributes/custom/resources/.*")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/authorities/" + A + "/attributes")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/authorities/" + A + "/attributes/raProfile")).willReturn(okJson("[]")));
    }

    @Test
    void authorityCreateAndUpdateUseSchemaValidatedReplacementAndOmitContent() {
        stubFor(post(urlEqualTo("/v1/authorities")).willReturn(okJson("{\"uuid\":\"" + R + "\",\"name\":\"CA\"}")));
        stubFor(get(urlEqualTo("/v1/authorities/" + R))
                .willReturn(okJson("{\"uuid\":\"" + R + "\",\"name\":\"CA\",\"connectorUuid\":\"" + A
                        + "\",\"kind\":\"CA\",\"attributes\":[],\"customAttributes\":[]}")));
        stubFor(put(urlEqualTo("/v1/authorities/" + R))
                .willReturn(okJson("{\"uuid\":\"" + R + "\",\"attributes\":[{\"secret\":\"SENTINEL\"}]}")));
        assertThat(authorities.createAuthority("{\"name\":\"CA\",\"connectorUuid\":\"" + A + "\",\"kind\":\"CA\"}"))
                .contains(R, "CA");
        assertThat(authorities.updateAuthority(R, "{}")).contains(R).doesNotContain("SENTINEL");
        verify(putRequestedFor(urlEqualTo("/v1/authorities/" + R))
                .withRequestBody(equalToJson("{\"attributes\":[],\"customAttributes\":[]}")));
    }

    @Test
    void raCreateAndUpdatePreserveEnabledAndDescription() {
        stubFor(post(urlEqualTo("/v1/authorities/" + A + "/raProfiles"))
                .willReturn(okJson("{\"uuid\":\"" + R + "\"}")));
        stubFor(get(urlEqualTo(BASE))
                .willReturn(okJson("{\"uuid\":\"" + R
                        + "\",\"description\":\"Existing\",\"enabled\":true,\"attributes\":[],\"customAttributes\":[]}")));
        stubFor(put(urlEqualTo(BASE)).willReturn(okJson("{\"uuid\":\"" + R + "\"}")));
        assertThat(profiles.createRaProfile(A, "{\"name\":\"TLS\",\"enabled\":true}")).contains(R);
        assertThat(profiles.updateRaProfile(A, R, "{}")).contains(R);
        verify(putRequestedFor(urlEqualTo(BASE))
                .withRequestBody(equalToJson(
                        "{\"description\":\"Existing\",\"enabled\":true,\"attributes\":[],\"customAttributes\":[]}")));
    }

    @Test
    void protectedProviderValuesRequireFreshInputBeforePut() {
        stubFor(get(urlEqualTo("/v1/authorities/" + A + "/attributes/raProfile")).willReturn(okJson("""
                [{"name":"pin","version":2,"type":"data","contentType":"secret","properties":{}}]
                """)));
        stubFor(get(urlEqualTo(BASE))
                .willReturn(
                        okJson("""
                                {"attributes":[{"name":"pin","version":"v2","contentType":"secret","content":[{"data":{"secret":"********","protectionLevel":"encrypted"}}]}],"customAttributes":[]}
                                """)));
        assertThat(profiles.updateRaProfile(A, R, "{\"description\":\"New\"}")).contains("pin", "fresh");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void baselineValidationPreservesFrequencyAndNewRequestSettingsAreVersionGated() {
        stubFor(get(urlEqualTo(BASE))
                .willReturn(okJson(
                        "{\"certificateValidationSettings\":{\"enabled\":true,\"frequency\":12,\"expiringThreshold\":30}}")));
        stubFor(patch(urlEqualTo(BASE + "/validation")).willReturn(okJson("{}")));
        assertThat(profiles.updateRaProfileValidation(A, R, "{\"enabled\":false}")).contains(R);
        verify(patchRequestedFor(urlEqualTo(BASE + "/validation"))
                .withRequestBody(equalToJson("{\"enabled\":false,\"frequency\":12,\"expiringThreshold\":30}")));
        IlmTestSupport.stubIlmVersion("2.18.0");
        assertThat(profiles.updateRaProfileRequestAttributes(A, R, "{\"requestAttributes\":[]}")).contains("2.19");
        verify(0, patchRequestedFor(urlEqualTo(BASE + "/requestAttributes")));
    }

    @Test
    void protocolActivationUsesOrderedEncodedAttributesAndTargetedRoute() {
        stubFor(get(urlEqualTo(BASE + "/attributes/issue")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo(BASE + "/attributes/revoke")).willReturn(okJson("[]")));
        stubFor(patch(urlEqualTo(BASE + "/protocols/acme/activate/" + R)).willReturn(okJson("{}")));
        assertThat(profiles.setRaProfileProtocol(A, R, "acme", R, "{}")).contains(R);
        verify(patchRequestedFor(urlEqualTo(BASE + "/protocols/acme/activate/" + R))
                .withRequestBody(
                        equalToJson("{\"issueCertificateAttributes\":[],\"revokeCertificateAttributes\":[]}")));
        assertThat(profiles.setRaProfileProtocol(A, R, "../users", R, "{}")).contains("protocol");
    }

    @Test
    void authorityReadsChoicesAndDeletionUseFixedRoutesAndMetadata() {
        stubFor(get(urlEqualTo("/v1/authorities/" + R))
                .willReturn(
                        okJson("{\"uuid\":\"" + R + "\",\"name\":\"CA\",\"attributes\":[{\"data\":\"SENTINEL\"}]}")));
        stubFor(get(urlEqualTo("/v1/authorities/" + R + "/endentityprofiles"))
                .willReturn(okJson("[{\"id\":12,\"name\":\"TLS entities\"}]")));
        stubFor(get(urlEqualTo("/v1/authorities/" + R + "/endentityprofiles/12/certificateprofiles"))
                .willReturn(okJson("[{\"id\":34,\"name\":\"TLS certificates\"}]")));
        stubFor(get(urlEqualTo("/v1/authorities/" + R + "/endentityprofiles/12/cas"))
                .willReturn(okJson("[{\"id\":56,\"name\":\"Root CA\"}]")));
        stubFor(delete(urlEqualTo("/v1/authorities/" + R)).willReturn(noContent()));
        assertThat(authorities.getAuthority(R)).contains("CA", R).doesNotContain("SENTINEL");
        assertThat(authorities.getAuthorityAttributes(A, null, null, null)).contains("Authority settings");
        assertThat(authorities.getAuthorityChoices(R, null)).contains("TLS entities", "12");
        assertThat(authorities.getAuthorityChoices(R, "12")).contains("TLS certificates", "Root CA");
        assertThat(authorities.deleteAuthority(R)).contains("Deleted", R);
        IlmTestSupport.stubIlmVersion("2.18.0");
        assertThat(authorities.getAuthorityAttributes(A, R, null, null)).contains("2.19");
        verify(0, getRequestedFor(urlPathEqualTo("/v1/authorities/" + A + "/attributes"))
                .withQueryParam("interfaceUuid", matching(".*")));
    }

    @Test
    void raReadStateRequestConfigurationAndAssociationsUseReleasedRoutes() {
        stubFor(get(urlEqualTo("/v1/raProfiles/" + R))
                .willReturn(okJson("{\"uuid\":\"" + R
                        + "\",\"name\":\"TLS\",\"certificateRequestAttributes\":{\"requestAttributes\":[]}}")));
        stubFor(get(urlEqualTo(BASE))
                .willReturn(okJson(
                        "{\"certificateRequestAttributes\":{\"requestAttributes\":[],\"mergeMode\":\"staticOnly\",\"externalCsrValidationStrict\":true}}")));
        stubFor(patch(urlEqualTo(BASE + "/requestAttributes")).willReturn(okJson("{}")));
        stubFor(patch(urlEqualTo(BASE + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(BASE + "/disable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(BASE + "/protocols/acme/deactivate")).willReturn(noContent()));
        stubFor(get(urlEqualTo(BASE + "/protocols/scep"))
                .willReturn(okJson("{\"enabled\":true,\"scepProfile\":{\"uuid\":\"" + R
                        + "\",\"name\":\"SCEP\"},\"challengePassword\":\"SENTINEL\"}")));
        stubFor(get(urlEqualTo(BASE + "/complianceProfiles")).willReturn(okJson("[{\"name\":\"Compliance\"}]")));
        stubFor(get(urlEqualTo(BASE + "/approvalProfiles")).willReturn(okJson("[{\"name\":\"Approval\"}]")));
        stubFor(get(urlEqualTo(BASE + "/caCertificates")).willReturn(okJson("[{\"subjectDn\":\"CN=Root CA\"}]")));
        stubFor(delete(urlEqualTo(BASE)).willReturn(noContent()));
        assertThat(profiles.getRaProfile(R)).contains(R, "TLS");
        assertThat(profiles.getRaProfileAttributes(A, null)).contains("RA profile settings");
        assertThat(profiles.enableRaProfile(A, R)).contains("true");
        assertThat(profiles.disableRaProfile(A, R)).contains("false");
        assertThat(profiles.updateRaProfileRequestAttributes(A, R, "{\"mergeMode\":\"merge\"}")).contains(R);
        verify(patchRequestedFor(urlEqualTo(BASE + "/requestAttributes"))
                .withRequestBody(equalToJson(
                        "{\"requestAttributes\":[],\"mergeMode\":\"merge\",\"externalCsrValidationStrict\":true}")));
        assertThat(profiles.getRaProfileProtocol(A, R, "scep")).contains("SCEP").doesNotContain("SENTINEL");
        assertThat(profiles.setRaProfileProtocol(A, R, "acme", null, null)).contains("Deactivated", R);
        assertThat(profiles.getRaProfileAssociations(A, R)).contains("Compliance", "Approval");
        assertThat(profiles.getAuthorityChain(A, R)).contains("CN=Root CA");
        assertThat(profiles.deleteRaProfile(A, R)).contains("Deleted", R);
    }

    @Test
    void validationCanExplicitlyRestorePlatformInheritance() {
        stubFor(get(urlEqualTo(BASE))
                .willReturn(okJson(
                        "{\"certificateValidationSettings\":{\"enabled\":true,\"frequency\":12,\"expiringThreshold\":30}}")));
        stubFor(patch(urlEqualTo(BASE + "/validation")).willReturn(okJson("{}")));
        assertThat(profiles.updateRaProfileValidation(A, R, "{\"enabled\":null}")).contains(R);
        verify(patchRequestedFor(urlEqualTo(BASE + "/validation"))
                .withRequestBody(equalToJson("{\"enabled\":null,\"frequency\":12,\"expiringThreshold\":30}")));
    }

    @Test
    void authoritySchemaCallbacksUseTheSelectedProviderKind() {
        stubFor(get(urlEqualTo("/v1/authorities/" + A + "/attributes"))
                .willReturn(
                        okJson("""
                                [{"name":"options","version":2,"type":"data","contentType":"string","properties":{"list":true},"attributeCallback":{"dependsOn":[]}}]
                                """)));
        stubFor(post(urlEqualTo("/v1/connectors/" + A + "/authorityProvider/CA/callback"))
                .willReturn(okJson("[{\"data\":\"RSA\"}]")));
        assertThat(authorities.getAuthorityAttributes(A, null, "CA", "{}")).contains("RSA");
        verify(postRequestedFor(urlEqualTo("/v1/connectors/" + A + "/authorityProvider/CA/callback")));
    }

}
