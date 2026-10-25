package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.SettingsTool;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SettingsDelegationTest {
    private static final String AUTHENTICATION = "/v1/settings/authentication";
    private static final String PROVIDER = AUTHENTICATION + "/oauth2Providers/example";
    private static final String BASE = "\"issuerUrl\":\"https://id.example.test\",\"clientId\":\"ilm\","
            + "\"jwkSetUrl\":\"https://id.example.test/jwks\"";
    private static final String DELEGATION = "\"allowedDelegates\":[\"ilm-mcp\"],\"delegateClaim\":\"client_id\"";
    private SettingsTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new SettingsTool(new SettingsServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.21.0");
        stubFor(put(urlEqualTo(PROVIDER)).willReturn(noContent()));
        stubFor(put(urlEqualTo(AUTHENTICATION)).willReturn(noContent()));
        stubFor(get(urlEqualTo(AUTHENTICATION)).willReturn(okJson("{\"disableLocalhostUser\":true}")));
    }

    @Test
    void unrelatedSkewEditPreservesDelegationWithoutRoundTrippingSecrets() {
        provider(BASE + "," + DELEGATION + """
                ,"clientSecret":"SECRET_SENTINEL","unexpectedSecret":"SECRET_SENTINEL",
                "jwkSetKeys":[{"publicKey":"SECRET_SENTINEL"}],"skew":30
                """);

        assertThat(tool.updateOAuthProviderSettings("example", "{\"skew\":45}"))
                .contains("updated")
                .doesNotContain("SECRET_SENTINEL", "clientSecret");

        verify(putRequestedFor(urlEqualTo(PROVIDER)).withRequestBody(equalToJson("""
                {"issuerUrl":"https://id.example.test","clientId":"ilm","jwkSetUrl":"https://id.example.test/jwks",
                "allowedDelegates":["ilm-mcp"],"delegateClaim":"client_id","skew":45}
                """)));
    }

    @Test
    void providerAndAuthenticationReadsDisplayDelegationWithoutSecrets() {
        String fields = BASE + "," + DELEGATION
                + ",\"clientSecret\":\"SECRET_SENTINEL\",\"jwkSet\":\"KEY_SENTINEL\",\"unknown\":\"SECRET_SENTINEL\"";
        provider(fields);
        stubFor(get(urlEqualTo(AUTHENTICATION))
                .willReturn(
                        okJson("{\"disableLocalhostUser\":true,\"oauth2Providers\":{\"example\":{" + fields + "}}}")));

        assertThat(tool.getOAuthProviderSettings("example"))
                .contains("allowedDelegates", "ilm-mcp", "delegateClaim", "client_id")
                .doesNotContain("SECRET_SENTINEL", "KEY_SENTINEL", "clientSecret", "jwkSet:");
        assertThat(tool.getAuthenticationSettings())
                .contains("allowedDelegates", "ilm-mcp", "delegateClaim", "client_id")
                .doesNotContain("SECRET_SENTINEL", "KEY_SENTINEL", "clientSecret", "jwkSet:");
        stubFor(get(urlEqualTo(AUTHENTICATION))
                .willReturn(okJson("{\"disableLocalhostUser\":true,\"oauth2Providers\":[{\"name\":\"example\"," + fields
                        + "}]}")));
        assertThat(tool.getAuthenticationSettings())
                .contains("allowedDelegates", "ilm-mcp", "delegateClaim", "client_id")
                .doesNotContain("SECRET_SENTINEL", "KEY_SENTINEL", "clientSecret");
    }

    @Test
    void explicitlyAddsDelegationAndReplacesTheAllowlistWithCaseSensitiveIdentifiers() {
        provider(BASE);
        assertThat(tool.updateOAuthProviderSettings("example", """
                {"allowedDelegates":["ilm-mcp","ILM-MCP"],"delegateClaim":"azp"}
                """)).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER)).withRequestBody(equalToJson("""
                {"issuerUrl":"https://id.example.test","clientId":"ilm","jwkSetUrl":"https://id.example.test/jwks",
                "allowedDelegates":["ilm-mcp","ILM-MCP"],"delegateClaim":"azp"}
                """)));
    }

    @Test
    void emptyAllowlistDisablesDelegationAndPreservesTheClaim() {
        provider(BASE + "," + DELEGATION);
        assertThat(tool.updateOAuthProviderSettings("example", "{\"allowedDelegates\":[]}")).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER)).withRequestBody(equalToJson("""
                {"issuerUrl":"https://id.example.test","clientId":"ilm","jwkSetUrl":"https://id.example.test/jwks",
                "allowedDelegates":[],"delegateClaim":"client_id"}
                """)));
    }

    @Test
    void changingOnlyTheClaimPreservesTheAllowlist() {
        provider(BASE + "," + DELEGATION);
        assertThat(tool.updateOAuthProviderSettings("example", "{\"delegateClaim\":\"azp\"}")).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER)).withRequestBody(equalToJson("""
                {"issuerUrl":"https://id.example.test","clientId":"ilm","jwkSetUrl":"https://id.example.test/jwks",
                "allowedDelegates":["ilm-mcp"],"delegateClaim":"azp"}
                """)));
    }

    @Test
    void completeProviderReplacementIncludesDelegationAndKeepsTheAuthenticationFlag() {
        assertThat(tool.replaceOAuthProviders(replacement(DELEGATION)))
                .contains("updated")
                .doesNotContain("FRESH_SENTINEL");
        verify(putRequestedFor(urlEqualTo(AUTHENTICATION)).withRequestBody(equalToJson("""
                {"disableLocalhostUser":true,"oauth2Providers":[{"name":"example","issuerUrl":"https://id.example.test",
                "clientId":"ilm","jwkSetUrl":"https://id.example.test/jwks","clientSecret":"FRESH_SENTINEL",
                "allowedDelegates":["ilm-mcp"],"delegateClaim":"client_id"}]}
                """)));
    }

    @Test
    void authenticationFlagUpdateLeavesProviderRegistrationsUntouched() {
        stubFor(get(urlEqualTo(AUTHENTICATION))
                .willReturn(okJson("{\"disableLocalhostUser\":true,\"oauth2Providers\":{\"example\":{" + BASE + ","
                        + DELEGATION + "}}}")));
        assertThat(tool.updateAuthenticationSettings("{\"disableLocalhostUser\":false}")).contains("updated");
        verify(putRequestedFor(urlEqualTo(AUTHENTICATION))
                .withRequestBody(equalToJson("{\"disableLocalhostUser\":false}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.17.0", "2.18.0", "2.19.0", "2.20.0"})
    void delegationChangesRequire221ButOrdinaryEditsWorkOnOlderIlm(String version) {
        IlmTestSupport.stubIlmVersion(version);
        provider(BASE);
        assertThat(tool.updateOAuthProviderSettings("example", "{\"skew\":45}")).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER)).withRequestBody(equalToJson("{" + BASE + ",\"skew\":45}")));
        assertThat(tool.updateOAuthProviderSettings("example", "{\"allowedDelegates\":[]}")).contains("2.21");
        assertThat(tool.updateOAuthProviderSettings("example", "{\"delegateClaim\":\"client_id\"}")).contains("2.21");
        assertThat(tool.replaceOAuthProviders(replacement(DELEGATION))).contains("2.21");
        verify(1, putRequestedFor(urlEqualTo(PROVIDER)));
        verify(0, putRequestedFor(urlEqualTo(AUTHENTICATION)));
        verify(1, getRequestedFor(urlEqualTo(PROVIDER)));
        verify(0, getRequestedFor(urlEqualTo(AUTHENTICATION)));
    }

    @ParameterizedTest
    @MethodSource("invalidDelegation")
    void rejectsInvalidDelegationInPartialAndCompleteUpdatesBeforeReadingSettings(String fields) {
        assertThat(tool.updateOAuthProviderSettings("example", "{" + fields + "}"))
                .doesNotContain("updated", "SECRET_SENTINEL");
        assertThat(tool.replaceOAuthProviders(replacement(fields)))
                .doesNotContain("updated", "SECRET_SENTINEL", "FRESH_SENTINEL");
        verify(0, putRequestedFor(anyUrl()));
        verify(0, getRequestedFor(urlEqualTo(PROVIDER)));
        verify(0, getRequestedFor(urlEqualTo(AUTHENTICATION)));
    }

    static Stream<String> invalidDelegation() {
        return Stream
                .of("\"allowedDelegates\":null", "\"allowedDelegates\":{}", "\"allowedDelegates\":\"ilm-mcp\"",
                        "\"allowedDelegates\":true", "\"allowedDelegates\":1", "\"allowedDelegates\":[null]",
                        "\"allowedDelegates\":[1]", "\"allowedDelegates\":[false]", "\"allowedDelegates\":[{}]",
                        "\"allowedDelegates\":[[]]", "\"allowedDelegates\":[\"\"]", "\"allowedDelegates\":[\"  \\t\"]",
                        "\"allowedDelegates\":[\"ilm-mcp\",\"ilm-mcp\"]",
                        "\"allowedDelegates\":[\"ilm-mcp\",\" ilm-mcp \" ]",
                        "\"allowedDelegates\":[\"" + "x".repeat(256) + "\"]",
                        "\"allowedDelegates\":[\"SECRET_SENTINEL\",\"\"]", "\"delegateClaim\":null",
                        "\"delegateClaim\":[]", "\"delegateClaim\":{}", "\"delegateClaim\":1", "\"delegateClaim\":true",
                        "\"delegateClaim\":\"\"", "\"delegateClaim\":\"   \"");
    }

    private void provider(String fields) {
        stubFor(get(urlEqualTo(PROVIDER)).willReturn(okJson("{" + fields + "}")));
    }

    private String replacement(String fields) {
        return "{\"oauth2Providers\":[{\"name\":\"example\",\"clientSecret\":\"FRESH_SENTINEL\"," + BASE + "," + fields
                + "}]}";
    }
}
