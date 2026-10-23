package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.SettingsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SettingsConfigurationTest {
    private SettingsTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new SettingsTool(new SettingsServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
    }

    @Test
    void mergesUtilityPatchAndPreservesUrlsAndTunables() {
        platform();
        writes("platform");
        assertThat(updateSettings("cbom", "{\"cbomSyncAssetBatchSize\":200}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/platform"))
                .withRequestBody(equalToJson(
                        """
                                {"utils":{"utilsServiceUrl":"https://utils.example.test","cbomRepositoryUrl":"https://cbom.example.test",
                                "cbomSyncPageSize":100,"cbomSyncAssetBatchSize":200}}
                                """)));
    }

    @Test
    void mergesCertificateGroupsWithoutClearingOmittedFieldsOrOtherSections() {
        platform();
        writes("platform");
        assertThat(updateSettings("validation", "{\"frequency\":2}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/platform")).withRequestBody(equalToJson("""
                {"certificates":{"validation":{"enabled":true,"frequency":2,"expiringThreshold":30}}}
                """)));
        assertThat(updateSettings("requestAttributes", "{\"externalCsrValidationStrict\":true}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/platform")).withRequestBody(equalToJson("""
                {"certificates":{"requestAttributes":{"externalCsrValidationStrict":true,"requestAttributes":[]}}}
                """)));
        assertThat(updateSettings("registration", "{\"maxFailedAttempts\":9}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/platform")).withRequestBody(equalToJson("""
                {"certificates":{"registration":{"defaultIssuanceWindowDays":7,"maxFailedAttempts":9}}}
                """)));
    }

    @Test
    void readsEverySectionWithoutProtectedOrUnknownContents() {
        platform();
        stubFor(get(urlEqualTo("/v1/settings/authentication")).willReturn(okJson("""
                {"disableLocalhostUser":false,"oauth2Providers":{"example":{"name":"example","clientId":"public-client",
                "clientSecret":"PROTECTED_SENTINEL","jwkSet":"PROTECTED_SENTINEL"}}}
                """)));
        stubFor(get(urlEqualTo("/v1/settings/logging"))
                .willReturn(
                        okJson("""
                                {"auditLogs":{"output":"CONSOLE","verbose":true,"password":"PROTECTED_SENTINEL"},"eventLogs":{"logAllModules":true}}
                                """)));
        for (String section : new String[]{
                "platform",
                "utils",
                "cbom",
                "validation",
                "requestAttributes",
                "registration",
                "authentication",
                "logging"}) {
            assertThat(getSettings(section)).doesNotContain("PROTECTED_SENTINEL", "branding", "clientSecret", "jwkSet");
        }
        assertThat(getSettings("authentication")).contains("example", "public-client");
        assertThat(tool.getPlatformSettings(null)).contains("Settings: platform");
    }

    @Test
    void authenticationPatchKeepsProviderListAbsentAndPreservesFlagWhenUnspecified() {
        stubFor(get(urlEqualTo("/v1/settings/authentication")).willReturn(okJson("""
                {"disableLocalhostUser":true,"oauth2Providers":{"other":{"clientSecret":"PROTECTED_SENTINEL"}}}
                """)));
        writes("authentication");
        assertThat(updateSettings("authentication", "{\"disableLocalhostUser\":false}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/authentication"))
                .withRequestBody(equalToJson("{\"disableLocalhostUser\":false}")));
    }

    @Test
    void providerPatchRetainsReadableFieldsAndUsesFreshSecretOnly() {
        stubFor(get(urlEqualTo("/v1/settings/authentication/oauth2Providers/example"))
                .willReturn(
                        okJson("""
                                {"name":"example","issuerUrl":"https://id.example.test","clientId":"old","tokenUrl":"https://id.example.test/token",
                                "clientSecret":"PROTECTED_SENTINEL","jwkSetUrl":"https://id.example.test/jwks","scope":["openid"],"jwkSetKeys":[{"publicKey":"PROTECTED_SENTINEL"}]}
                                """)));
        stubFor(put(urlEqualTo("/v1/settings/authentication/oauth2Providers/example")).willReturn(noContent()));
        assertThat(tool.getOAuthProviderSettings("example")).contains("old").doesNotContain("PROTECTED_SENTINEL");
        assertThat(
                tool.updateOAuthProviderSettings("example", "{\"clientId\":\"new\",\"clientSecret\":\"fresh-value\"}"))
                .contains("updated")
                .doesNotContain("fresh-value");
        verify(putRequestedFor(urlEqualTo("/v1/settings/authentication/oauth2Providers/example"))
                .withRequestBody(equalToJson(
                        """
                                {"issuerUrl":"https://id.example.test","clientId":"new","tokenUrl":"https://id.example.test/token",
                                "scope":["openid"],"jwkSetUrl":"https://id.example.test/jwks","clientSecret":"fresh-value"}
                                """)));
        assertThat(tool.updateOAuthProviderSettings("example", "{\"clientSecret\":\"********\"}"))
                .containsIgnoringCase("fresh");
        verify(1, putRequestedFor(urlEqualTo("/v1/settings/authentication/oauth2Providers/example")));
    }

    @Test
    void versionGatesNewFieldsBeforeWritesAndRejectsUnsupportedInputSafely() {
        IlmTestSupport.stubIlmVersion("2.18.0");
        assertThat(updateSettings("cbom", "{\"cbomSyncPageSize\":50}")).contains("2.20");
        assertThat(updateSettings("registration", "{\"maxFailedAttempts\":5}")).contains("2.19");
        assertThat(updateSettings("requestAttributes", "{\"externalCsrValidationStrict\":false}")).contains("2.19");
        assertThat(updateSettings("utils", "{\"PROTECTED_SENTINEL\":1}")).doesNotContain("PROTECTED_SENTINEL");
        assertThat(updateSettings("utils", "{\"utilsServiceUrl\":\"PROTECTED_SENTINEL\"}"))
                .doesNotContain("PROTECTED_SENTINEL");
        assertThat(updateSettings("logging", "{\"auditLogs\":{\"output\":\"PROTECTED_SENTINEL\"}}"))
                .doesNotContain("PROTECTED_SENTINEL");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void loggingMergesGroupsAndProviderRemovalUsesDelete() {
        stubFor(get(urlEqualTo("/v1/settings/logging"))
                .willReturn(
                        okJson("""
                                {"auditLogs":{"output":"NONE","verbose":false,"loggedModules":["core"]},"eventLogs":{"logAllModules":true}}
                                """)));
        writes("logging");
        assertThat(updateSettings("logging", "{\"auditLogs\":{\"verbose\":true}}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/logging"))
                .withRequestBody(equalToJson(
                        """
                                {"auditLogs":{"output":"NONE","verbose":true,"loggedModules":["core"]},"eventLogs":{"logAllModules":true}}
                                """)));
        stubFor(delete(urlEqualTo("/v1/settings/authentication/oauth2Providers/example")).willReturn(noContent()));
        assertThat(tool.removeOAuthProvider("example")).contains("removed");
        verify(deleteRequestedFor(urlEqualTo("/v1/settings/authentication/oauth2Providers/example")));
    }

    @Test
    void eventPatchKeepsOtherAssociationsAndNamesExecutionIdentityEffect() {
        stubFor(get(urlEqualTo("/v1/settings/events"))
                .willReturn(
                        okJson("""
                                {"eventsMapping":{"certificate_expiring":["613094ba-8f40-4c51-ab36-887a46a32475"],"discovery_finished":[]}}
                                """)));
        writes("events");
        assertThat(getSettings("events")).contains("certificate_expiring");
        assertThat(updateSettings("events", "{\"eventsMapping\":{\"discovery_finished\":[]}}"))
                .contains("execution identity");
        verify(putRequestedFor(urlEqualTo("/v1/settings/events"))
                .withRequestBody(equalToJson(
                        """
                                {"eventsMapping":{"certificate_expiring":["613094ba-8f40-4c51-ab36-887a46a32475"],"discovery_finished":[]}}
                                """)));
    }

    @Test
    void authenticationProviderReplacementNeedsFreshCredentialsBeforeWriting() {
        assertThat(tool.replaceOAuthProviders("""
                {"oauth2Providers":[{"name":"example","issuerUrl":"https://id.example.test","clientId":"client"}]}
                """)).containsIgnoringCase("fresh");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void malformedAndServerErrorsDoNotEchoSecrets() {
        assertThat(updateSettings("utils", "{PROTECTED_SENTINEL")).doesNotContain("PROTECTED_SENTINEL");
        stubFor(get(urlEqualTo("/v1/settings/platform"))
                .willReturn(aResponse().withStatus(400).withBody("{\"message\":\"PROTECTED_SENTINEL\"}")));
        assertThat(getSettings("platform")).doesNotContain("PROTECTED_SENTINEL");
    }

    @Test
    void ordinaryUrlMetadataOmitsEmbeddedCredentialsAndQueryParameters() {
        stubFor(get(urlEqualTo("/v1/settings/platform"))
                .willReturn(
                        okJson("""
                                {"utils":{"utilsServiceUrl":"https://user:PROTECTED_SENTINEL@utils.example.test/path?token=PROTECTED_SENTINEL"}}
                                """)));
        assertThat(getSettings("utils"))
                .contains("utils.example.test")
                .doesNotContain("PROTECTED_SENTINEL", "user:", "token=");
    }

    @Test
    void missingJwkConfigurationRefusesProviderMutation() {
        stubFor(get(urlEqualTo("/v1/settings/authentication/oauth2Providers/example"))
                .willReturn(okJson("{\"clientId\":\"id\"}")));
        assertThat(tool.updateOAuthProviderSettings("example", "{\"clientId\":\"updated\"}"))
                .containsIgnoringCase("JWK");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void refusesInvalidSettingsShapesAndBoundsBeforeAnyMutation() {
        String[][] invalid = {
                {"utils", "{\"cbomSyncPageSize\":0}"},
                {"utils", "{\"cbomSyncAssetBatchSize\":10001}"},
                {"utils", "{\"cbomSyncOverlapSeconds\":86401}"},
                {"utils", "{\"cbomSyncSkippedRetryRuns\":1001}"},
                {"utils", "{\"cbomSyncSkipRetentionDays\":3651}"},
                {"utils", "{\"cbomSyncAssetIngestEnabled\":1}"},
                {"cbom", "{\"utilsServiceUrl\":null}"},
                {"validation", "{\"enabled\":\"yes\"}"},
                {"registration", "{\"maxFailedAttempts\":0}"},
                {"requestAttributes", "{\"requestAttributes\":{}}"},
                {"authentication", "{\"oauth2Providers\":null}"},
                {"logging", "{\"auditLogs\":{\"ignoredModules\":[\"unknown\"]}}"},
                {"logging", "{\"eventLogs\":{\"loggedResources\":[false]}}"},
                {"events", "{\"eventsMapping\":{\"unknown\":[]}}"},
                {"platform", "{\"certificates\":{\"unknown\":{}}}"},
                {"platform", "{\"utils\":null}"}};
        for (String[] input : invalid) {
            assertThat(updateSettings(input[0], input[1])).doesNotContain("updated");
        }
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void nestedPlatformUpdatesAndOAuthUsernameClaimHaveFieldLevelVersionGates() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(updateSettings("platform", "{\"certificates\":{\"requestAttributes\":{}}}")).contains("2.19");
        assertThat(updateSettings("platform", "{\"certificates\":{\"registration\":{}}}")).contains("2.19");
        assertThat(updateSettings("platform", "{\"utils\":{\"cbomSyncPageSize\":100}}")).contains("2.20");
        assertThat(tool.updateOAuthProviderSettings("example", "{\"usernameClaim\":\"sub\"}")).contains("2.18");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void allUtilityTunablesAndNullableResetKeepThePublishedFlatShape() {
        platform();
        writes("platform");
        String patch = """
                {"utilsServiceUrl":null,"cbomSyncOverlapSeconds":0,"cbomSyncSkippedRetryRuns":0,
                "cbomSyncMaxIngestDocuments":0,"cbomSyncSkipRetentionDays":1,"cbomSyncPageSize":1,
                "cbomSyncAssetIngestEnabled":false,"cbomSyncAssetBatchSize":1,"cbomSyncIngestRetryAfterSeconds":0}
                """;
        assertThat(updateSettings("utils", patch)).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/platform"))
                .withRequestBody(matchingJsonPath("$.utils.cbomRepositoryUrl", equalTo("https://cbom.example.test")))
                .withRequestBody(matchingJsonPath("$.utils.cbomSyncAssetIngestEnabled", equalTo("false"))));
    }

    @Test
    void requestDefinitionWireDataIsPreservedButOrdinaryReadShowsOnlyMetadata() {
        platform();
        writes("platform");
        String patch = """
                {"requestAttributes":[{"name":"subject","uuid":"613094ba-8f40-4c51-ab36-887a46a32475","version":3,
                "schemaVersion":"v3","type":"data","contentType":"string","properties":{"label":"Subject"},
                "content":[{"contentType":"string","data":"PROTECTED_SENTINEL"}],
                "fieldMapping":{"objectType":"x509Certificate","fields":[{"fieldType":"rdn","rdn":"CN"}]}}]}
                """;
        assertThat(updateSettings("requestAttributes", patch)).contains("updated").doesNotContain("PROTECTED_SENTINEL");
        verify(putRequestedFor(urlEqualTo("/v1/settings/platform"))
                .withRequestBody(
                        matchingJsonPath("$.certificates.requestAttributes.requestAttributes[0].content[0].data",
                                equalTo("PROTECTED_SENTINEL"))));
        stubFor(get(urlEqualTo("/v1/settings/platform"))
                .willReturn(okJson("{\"certificates\":{\"requestAttributes\":" + patch + "}}")));
        assertThat(getSettings("requestAttributes"))
                .contains("subject")
                .doesNotContain("PROTECTED_SENTINEL", "content:");
    }

    @Test
    void safeFailuresForProviderNamesUnknownSectionsAndDeleteErrors() {
        assertThat(tool.getOAuthProviderSettings("../PROTECTED_SENTINEL")).doesNotContain("PROTECTED_SENTINEL");
        assertThat(tool.removeOAuthProvider(null)).containsIgnoringCase("required");
        assertThat(getSettings("PROTECTED_SENTINEL")).doesNotContain("PROTECTED_SENTINEL");
        stubFor(delete(urlEqualTo("/v1/settings/authentication/oauth2Providers/example"))
                .willReturn(aResponse().withStatus(422).withBody("{\"message\":\"PROTECTED_SENTINEL\"}")));
        assertThat(tool.removeOAuthProvider("example")).contains("422").doesNotContain("PROTECTED_SENTINEL");
    }

    @Test
    void ordinaryAuthenticationUpdateCannotRemoveProviderRegistrations() {
        stubFor(get(urlEqualTo("/v1/settings/authentication")).willReturn(okJson("{\"disableLocalhostUser\":true}")));
        writes("authentication");
        assertThat(tool.updateAuthenticationSettings("{\"oauth2Providers\":[]}")).contains("replace_oauth_providers");
        assertThat(tool.updatePlatformSettings("authentication", "{\"oauth2Providers\":[]}"))
                .contains("replace_oauth_providers");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void explicitFullProviderReplacementPreservesAuthenticationFlagAndSendsFreshCredentials() {
        stubFor(get(urlEqualTo("/v1/settings/authentication")).willReturn(okJson("{\"disableLocalhostUser\":true}")));
        writes("authentication");
        String config = """
                {"oauth2Providers":[{"name":"example","issuerUrl":"https://id.example.test","clientId":"client",
                "jwkSetUrl":"https://id.example.test/jwks","clientSecret":"FRESH_SENTINEL"}]}
                """;
        assertThat(tool.replaceOAuthProviders(config)).contains("updated").doesNotContain("FRESH_SENTINEL");
        verify(putRequestedFor(urlEqualTo("/v1/settings/authentication"))
                .withRequestBody(equalToJson(
                        """
                                {"disableLocalhostUser":true,"oauth2Providers":[{"name":"example","issuerUrl":"https://id.example.test","clientId":"client",
                                "jwkSetUrl":"https://id.example.test/jwks","clientSecret":"FRESH_SENTINEL"}]}
                                """)));
        assertThat(tool.replaceOAuthProviders("{\"oauth2Providers\":[]}")).contains("updated");
        verify(putRequestedFor(urlEqualTo("/v1/settings/authentication"))
                .withRequestBody(equalToJson("{\"disableLocalhostUser\":true,\"oauth2Providers\":[]}")));
    }

    @Test
    void malformedNestedResponseValuesCannotEscapeFieldAllowlists() {
        stubFor(get(urlEqualTo("/v1/settings/platform")).willReturn(okJson("""
                {"utils":{"cbomSyncPageSize":{"unexpected":"PROTECTED_SENTINEL"}},
                "certificates":{"validation":{"enabled":{"unexpected":"PROTECTED_SENTINEL"}}}}
                """)));
        assertThat(getSettings("platform")).doesNotContain("PROTECTED_SENTINEL", "unexpected");
        stubFor(get(urlEqualTo("/v1/settings/logging")).willReturn(okJson("""
                {"auditLogs":{"loggedModules":[{"unexpected":"PROTECTED_SENTINEL"}]}}
                """)));
        assertThat(getSettings("logging")).doesNotContain("PROTECTED_SENTINEL", "unexpected");
        stubFor(get(urlEqualTo("/v1/settings/events")).willReturn(okJson("""
                {"eventsMapping":{"unexpected":["PROTECTED_SENTINEL"]}}
                """)));
        assertThat(getSettings("events")).doesNotContain("PROTECTED_SENTINEL", "unexpected");
    }

    private String getSettings(String section) {
        return switch (section) {
            case "authentication" -> tool.getAuthenticationSettings();
            case "logging" -> tool.getLoggingSettings();
            case "events" -> tool.getEventSettings();
            default -> tool.getPlatformSettings(section);
        };
    }

    private String updateSettings(String section, String settings) {
        return switch (section) {
            case "authentication" -> tool.updateAuthenticationSettings(settings);
            case "logging" -> tool.updateLoggingSettings(settings);
            case "events" -> tool.updateEventSettings(settings);
            default -> tool.updatePlatformSettings(section, settings);
        };
    }

    private void platform() {
        stubFor(get(urlEqualTo("/v1/settings/platform"))
                .willReturn(
                        okJson("""
                                {"utils":{"utilsServiceUrl":"https://utils.example.test","cbomRepositoryUrl":"https://cbom.example.test",
                                "cbomSyncPageSize":100,"cbomSyncAssetBatchSize":100},"certificates":{
                                "validation":{"enabled":true,"frequency":1,"expiringThreshold":30},
                                "requestAttributes":{"externalCsrValidationStrict":false,"requestAttributes":[]},
                                "registration":{"defaultIssuanceWindowDays":7,"maxFailedAttempts":5}},
                                "branding":{"lightLogo":"PROTECTED_SENTINEL"},"password":"PROTECTED_SENTINEL"}
                                """)));
    }

    private void writes(String section) {
        stubFor(put(urlEqualTo("/v1/settings/" + section)).willReturn(noContent()));
    }
}
