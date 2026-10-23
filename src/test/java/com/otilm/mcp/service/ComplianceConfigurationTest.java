package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.ComplianceTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
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
class ComplianceConfigurationTest {
    private static final String PROFILE = "00000000-0000-4000-8000-000000000931";
    private static final String INTERNAL = "00000000-0000-4000-8000-000000000932";
    private static final String CONNECTOR = "00000000-0000-4000-8000-000000000933";
    private static final String RULE = "00000000-0000-4000-8000-000000000934";
    private static final String GROUP = "00000000-0000-4000-8000-000000000935";
    private static final String OBJECT = "00000000-0000-4000-8000-000000000936";
    private static final String BASE = "/v2/complianceProfiles";
    private static final String PROVIDER_RULES = BASE + "/rules?connectorUuid=" + CONNECTOR + "&kind=TEST";
    private static final String PROVIDER_GROUPS = BASE + "/groups?connectorUuid=" + CONNECTOR + "&kind=TEST";
    private static final String SCHEMA = "[{\"name\":\"threshold\",\"type\":\"data\",\"contentType\":\"integer\",\"properties\":{\"required\":true},\"content\":[{\"reference\":\"provider-default\",\"data\":90}]}]";
    private ComplianceTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new ComplianceTool(new ComplianceServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build())));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/complianceProfiles")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/resources"))
                .willReturn(okJson(
                        "[{\"resource\":\"certificates\",\"complianceSubject\":true,\"hasComplianceProfiles\":false},{\"resource\":\"raProfiles\",\"complianceSubject\":false,\"hasComplianceProfiles\":true}]")));
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR))
                .willReturn(okJson(
                        "{\"functionGroups\":[{\"functionGroupCode\":\"complianceProviderV2\",\"kinds\":[\"TEST\"]}]}")));
        stubFor(get(urlEqualTo(PROVIDER_RULES))
                .willReturn(okJson("[{\"uuid\":\"" + RULE
                        + "\",\"name\":\"Provider rule\",\"resource\":\"certificates\",\"type\":\"X.509\",\"format\":\"PEM\",\"attributes\":"
                        + SCHEMA + "}]")));
        stubFor(get(urlEqualTo(PROVIDER_GROUPS))
                .willReturn(okJson(
                        "[{\"uuid\":\"" + GROUP + "\",\"name\":\"Provider group\",\"resource\":\"certificates\"}]")));
        internal();
        profile();
    }

    @Test
    void profileMetadataPutRetainsAllInternalProviderRulesGroupsAndReferences() {
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"description\":\"updated\"}")));
        assertThat(tool.updateComplianceProfile(PROFILE, "{\"description\":\"updated\"}")).contains(PROFILE);
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(equalToJson("{\"description\":\"updated\",\"internalRules\":[\"" + INTERNAL
                        + "\"],\"providerRules\":[{\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"TEST\",\"groups\":[\"" + GROUP + "\"],\"rules\":[{\"uuid\":\"" + RULE
                        + "\",\"attributes\":[{\"name\":\"threshold\",\"version\":\"v2\",\"contentType\":\"integer\",\"content\":[{\"reference\":\"existing-reference\",\"data\":45}]}]}]}],\"customAttributes\":[]}")));
    }

    @Test
    void createProfileBuildsProviderSchemasBeforeWritingAndDetailHidesContent() {
        stubFor(post(urlEqualTo(BASE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE + "\",\"name\":\"new\",\"secret\":\"SENTINEL_SECRET\"}")));
        assertThat(tool
                .createComplianceProfile("{\"name\":\"new\",\"internalRules\":[\"" + INTERNAL
                        + "\"],\"providerRules\":[{\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"TEST\",\"rules\":[{\"uuid\":\"" + RULE + "\"}],\"groups\":[\"" + GROUP
                        + "\"]}]}"))
                .contains(PROFILE)
                .doesNotContain("SENTINEL_SECRET");
        verify(postRequestedFor(urlEqualTo(BASE))
                .withRequestBody(matchingJsonPath("$.providerRules[0].rules[0].attributes[0].content[0].reference",
                        equalTo("provider-default"))));
        stubFor(get(urlEqualTo(BASE)).willReturn(okJson("[{\"uuid\":\"" + PROFILE + "\",\"name\":\"profile\"}]")));
        assertThat(tool.listComplianceProfiles()).contains("profile");
        assertThat(tool.getComplianceProfile(PROFILE))
                .contains("Provider rule", "Provider group")
                .doesNotContain("existing-reference", "SENTINEL_SECRET");
        stubFor(delete(urlEqualTo(BASE + "/" + PROFILE)).willReturn(noContent()));
        assertThat(tool.deleteComplianceProfile(PROFILE)).contains("Deleted");
    }

    @Test
    void protectedExistingRuleValuesRequireFreshInputWithoutClearingOtherComposition() {
        stubFor(get(urlEqualTo(PROVIDER_RULES))
                .willReturn(okJson("[{\"uuid\":\"" + RULE
                        + "\",\"name\":\"Provider rule\",\"attributes\":[{\"name\":\"threshold\",\"type\":\"data\",\"contentType\":\"secret\",\"properties\":{}}]}]")));
        assertThat(tool.updateComplianceProfile(PROFILE, "{\"description\":\"updated\"}")).contains("fresh");
        verify(0, putRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool
                .updateComplianceProfile(PROFILE,
                        "{\"providerRules\":[{\"connectorUuid\":\"" + CONNECTOR
                                + "\",\"kind\":\"TEST\",\"rules\":[{\"uuid\":\"" + RULE
                                + "\",\"attributes\":{\"threshold\":{\"secret\":\"fresh-synthetic\"}}}]}]}"))
                .contains(PROFILE)
                .doesNotContain("fresh-synthetic");
        verify(putRequestedFor(urlEqualTo(BASE + "/" + PROFILE))
                .withRequestBody(matchingJsonPath("$.providerRules[0].groups[0]", equalTo(GROUP)))
                .withRequestBody(matchingJsonPath("$.internalRules[0]", equalTo(INTERNAL))));
    }

    @Test
    void providerCatalogRuleSchemasAndGroupsUseEmbeddedDefinitions() {
        stubFor(get(urlEqualTo(BASE + "/groups/" + GROUP + "/rules?connectorUuid=" + CONNECTOR + "&kind=TEST"))
                .willReturn(okJson("[{\"uuid\":\"" + RULE + "\",\"name\":\"rule\"}]")));
        assertThat(tool.getComplianceRules(CONNECTOR, "TEST", null, null, null)).contains("Provider rule");
        assertThat(tool.getComplianceRuleAttributes(CONNECTOR, "TEST", RULE, null)).contains("threshold");
        assertThat(tool.getComplianceGroups(CONNECTOR, "TEST", null)).contains("Provider group");
        assertThat(tool.getComplianceGroupRules(CONNECTOR, "TEST", GROUP)).contains(RULE);
        assertThat(tool.getComplianceResources()).contains("certificates", "raProfiles");
        assertThat(tool.getComplianceCatalog(CONNECTOR, "TEST")).contains("X.509", "PEM", "certificates");
    }

    @Test
    void ruleAndGroupPatchesTouchOnlyTheChosenAssociation() {
        stubFor(patch(urlEqualTo(BASE + "/" + PROFILE + "/rules")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(BASE + "/" + PROFILE + "/groups")).willReturn(noContent()));
        assertThat(tool.addComplianceRule(PROFILE, RULE, CONNECTOR, "TEST", "{\"threshold\":60}")).contains("accepted");
        verify(patchRequestedFor(urlEqualTo(BASE + "/" + PROFILE + "/rules"))
                .withRequestBody(equalToJson("{\"removal\":false,\"ruleUuid\":\"" + RULE + "\",\"connectorUuid\":\""
                        + CONNECTOR
                        + "\",\"kind\":\"TEST\",\"attributes\":[{\"name\":\"threshold\",\"version\":\"v2\",\"contentType\":\"integer\",\"content\":[{\"data\":60}]}]}")));
        assertThat(tool.removeComplianceRule(PROFILE, RULE, CONNECTOR, "TEST")).contains("accepted");
        assertThat(tool.addComplianceRule(PROFILE, INTERNAL, null, null, null)).contains("accepted");
        assertThat(tool.addComplianceGroup(PROFILE, CONNECTOR, "TEST", GROUP)).contains("accepted");
        assertThat(tool.removeComplianceGroup(PROFILE, CONNECTOR, "TEST", GROUP)).contains("accepted");
        verify(patchRequestedFor(urlEqualTo(BASE + "/" + PROFILE + "/groups"))
                .withRequestBody(equalToJson("{\"removal\":true,\"groupUuid\":\"" + GROUP + "\",\"connectorUuid\":\""
                        + CONNECTOR + "\",\"kind\":\"TEST\"}")));
        verify(0, putRequestedFor(urlEqualTo(BASE + "/" + PROFILE)));
    }

    @Test
    void internalRuleCrudValidatesConditionFieldsAndOperatorsAgainstResourceSchema() {
        stubFor(post(urlEqualTo(BASE + "/rules"))
                .willReturn(okJson("{\"uuid\":\"" + INTERNAL + "\",\"name\":\"new\"}")));
        stubFor(put(urlEqualTo(BASE + "/rules/" + INTERNAL)).willReturn(okJson("{\"uuid\":\"" + INTERNAL + "\"}")));
        stubFor(delete(urlEqualTo(BASE + "/rules/" + INTERNAL)).willReturn(noContent()));
        assertThat(tool.getComplianceConditionFields("certificates"))
                .contains("CERTIFICATE_SUBJECT_CN")
                .doesNotContain("SENTINEL_SECRET");
        assertThat(tool.getComplianceInternalRule(INTERNAL)).contains("internal").doesNotContain("SENTINEL_SECRET");
        assertThat(tool
                .createComplianceInternalRule(
                        "{\"name\":\"new\",\"resource\":\"certificates\",\"conditionItems\":[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"CERTIFICATE_SUBJECT_CN\",\"operator\":\"CONTAINS\",\"value\":\"example\"}]}"))
                .contains(INTERNAL);
        assertThat(tool.updateComplianceInternalRule(INTERNAL, "{\"description\":\"updated\"}")).contains(INTERNAL);
        verify(putRequestedFor(urlEqualTo(BASE + "/rules/" + INTERNAL))
                .withRequestBody(matchingJsonPath("$.conditionItems[0].operator", equalTo("CONTAINS"))));
        assertThat(tool
                .createComplianceInternalRule(
                        "{\"name\":\"bad\",\"resource\":\"certificates\",\"conditionItems\":[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"CERTIFICATE_SUBJECT_CN\",\"operator\":\"GREATER\",\"value\":5}]}"))
                .containsIgnoringCase("operator");
        verify(1, postRequestedFor(urlEqualTo(BASE + "/rules")));
        assertThat(tool.deleteComplianceInternalRule(INTERNAL)).contains("Deleted");
    }

    @Test
    void resourceAssociationsAndChecksUseTargetedRoutesAndReportAcceptedNotPassed() {
        String association = BASE + "/" + PROFILE + "/associations/raProfiles/" + OBJECT;
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE + "/associations"))
                .willReturn(okJson("[{\"resource\":\"raProfiles\",\"uuid\":\"" + OBJECT + "\",\"name\":\"RA\"}]")));
        stubFor(get(urlEqualTo(BASE + "/associations/raProfiles/" + OBJECT))
                .willReturn(okJson("[{\"uuid\":\"" + PROFILE + "\",\"name\":\"profile\"}]")));
        stubFor(patch(urlEqualTo(association)).willReturn(noContent()));
        stubFor(delete(urlEqualTo(association)).willReturn(noContent()));
        assertThat(tool.getComplianceProfileAssociations(PROFILE)).contains("RA");
        assertThat(tool.getResourceComplianceProfiles("raProfiles", OBJECT)).contains(PROFILE);
        assertThat(tool.associateComplianceProfile(PROFILE, "raProfiles", OBJECT)).contains("accepted");
        assertThat(tool.detachComplianceProfile(PROFILE, "raProfiles", OBJECT)).contains("accepted");
        stubFor(post(urlEqualTo("/v2/compliance/certificates/" + OBJECT)).willReturn(noContent()));
        stubFor(post(urlEqualTo("/v2/compliance?resource=certificates&type=X.509")).willReturn(noContent()));
        stubFor(get(urlEqualTo("/v2/compliance/certificates/" + OBJECT))
                .willReturn(okJson(
                        "{\"status\":\"non_compliant\",\"timestamp\":\"2026-10-23T20:00:00Z\",\"message\":\"SENTINEL_SECRET\",\"failedRules\":[{\"uuid\":\""
                                + RULE
                                + "\",\"name\":\"failed\",\"status\":\"failed\",\"attributes\":[{\"content\":\"SENTINEL_SECRET\"}]}]}")));
        assertThat(tool.checkCompliance("certificates", OBJECT)).contains("accepted").doesNotContain("passed");
        assertThat(tool.checkComplianceProfiles("[\"" + PROFILE + "\"]", "certificates", "X.509")).contains("accepted");
        verify(postRequestedFor(urlEqualTo("/v2/compliance?resource=certificates&type=X.509"))
                .withRequestBody(equalToJson("[\"" + PROFILE + "\"]")));
        assertThat(tool.getComplianceResult("certificates", OBJECT))
                .contains("non_compliant", "failed")
                .doesNotContain("SENTINEL_SECRET");
    }

    @Test
    void invalidProviderChoicesUnknownFieldsAndErrorsNeverLeakSensitiveInput() {
        assertThat(tool.addComplianceRule(PROFILE, RULE, CONNECTOR, "OTHER", "{}")).contains("kind");
        assertThat(tool.updateComplianceProfile(PROFILE, "{\"SENTINEL_SECRET\":true}"))
                .doesNotContain("SENTINEL_SECRET");
        assertThat(tool.checkCompliance("../../secrets", OBJECT))
                .doesNotContain("../../secrets")
                .containsIgnoringCase("resource");
        verify(0, patchRequestedFor(anyUrl()));
        verify(0, postRequestedFor(urlPathMatching("/v2/compliance/.*")));
        stubFor(delete(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(aResponse().withStatus(422).withBody("SENTINEL_SECRET")));
        assertThat(tool.deleteComplianceProfile(PROFILE)).contains("422").doesNotContain("SENTINEL_SECRET");
    }

    @Test
    void metadataReplacementCallbacksConsumeStoredReadableRuleValues() {
        stubFor(get(urlEqualTo(PROVIDER_RULES))
                .willReturn(okJson("[{\"uuid\":\"" + RULE
                        + "\",\"name\":\"Provider rule\",\"attributes\":[{\"name\":\"threshold\",\"type\":\"data\",\"contentType\":\"integer\",\"properties\":{}},{\"name\":\"dependent\",\"type\":\"group\",\"attributeCallback\":{\"callbackContext\":\"provider\",\"mappings\":[{\"from\":\"threshold\",\"to\":\"limit\",\"targets\":[\"body\"]}]}}]}]")));
        String callback = "/v1/connectors/" + CONNECTOR + "/complianceProviderV2/TEST/callback";
        stubFor(post(urlEqualTo(callback)).willReturn(okJson("[]")));
        stubFor(put(urlEqualTo(BASE + "/" + PROFILE)).willReturn(okJson("{\"uuid\":\"" + PROFILE + "\"}")));
        assertThat(tool.updateComplianceProfile(PROFILE, "{\"description\":\"updated\"}")).contains(PROFILE);
        verify(postRequestedFor(urlEqualTo(callback)).withRequestBody(matchingJsonPath("$.body.limit", equalTo("45"))));
    }

    @Test
    void addingAnExistingProviderRulePreservesItsOmittedReadableAttributes() {
        stubFor(patch(urlEqualTo(BASE + "/" + PROFILE + "/rules")).willReturn(noContent()));
        assertThat(tool.addComplianceRule(PROFILE, RULE, CONNECTOR, "TEST", null)).contains("accepted");
        verify(patchRequestedFor(urlEqualTo(BASE + "/" + PROFILE + "/rules"))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("45")))
                .withRequestBody(
                        matchingJsonPath("$.attributes[0].content[0].reference", equalTo("existing-reference"))));
    }

    private void internal() {
        stubFor(get(urlEqualTo(BASE + "/rules"))
                .willReturn(okJson("[{\"uuid\":\"" + INTERNAL
                        + "\",\"name\":\"internal\",\"description\":\"old\",\"resource\":\"certificates\",\"conditionItems\":[{\"fieldSource\":\"property\",\"fieldIdentifier\":\"CERTIFICATE_SUBJECT_CN\",\"operator\":\"CONTAINS\",\"value\":\"SENTINEL_SECRET\"}]}]")));
        stubFor(get(urlEqualTo("/v1/resources/certificates/filters/rules"))
                .willReturn(okJson(
                        "[{\"filterFieldSource\":\"property\",\"searchFieldData\":[{\"fieldIdentifier\":\"CERTIFICATE_SUBJECT_CN\",\"fieldLabel\":\"Common name\",\"type\":\"string\",\"conditions\":[\"CONTAINS\",\"EQUALS\"],\"value\":\"SENTINEL_SECRET\"}]}]")));
    }

    private void profile() {
        stubFor(get(urlEqualTo(BASE + "/" + PROFILE))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE
                        + "\",\"name\":\"profile\",\"description\":\"old\",\"internalRules\":[{\"uuid\":\"" + INTERNAL
                        + "\",\"name\":\"internal\"}],\"providerRules\":[{\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"connectorName\":\"provider\",\"kind\":\"TEST\",\"rules\":[{\"uuid\":\"" + RULE
                        + "\",\"name\":\"Provider rule\",\"attributes\":[{\"name\":\"threshold\",\"content\":[{\"reference\":\"existing-reference\",\"data\":45}]}]}],\"groups\":[{\"uuid\":\""
                        + GROUP
                        + "\",\"name\":\"Provider group\"}]}],\"customAttributes\":[],\"secret\":\"SENTINEL_SECRET\"}")));
    }
}
