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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
class SettingsApplicationsTest {
    private static final String AUTHENTICATION = "/v1/settings/authentication";
    private static final String PROVIDER = AUTHENTICATION + "/oauth2Providers/example";
    private static final String BASE = "\"issuerUrl\":\"https://id.example.test\",\"clientId\":\"ilm\","
            + "\"jwkSetUrl\":\"https://id.example.test/jwks\"";
    private static final String APPLICATIONS = "\"applications\":{\"enabled\":true,\"name\":\"Example ID\","
            + "\"clientId\":\"ilm-apps\",\"scopes\":[\"openid\",\"ilm\"],\"resource\":\"https://ilm.example.test/api\"}";
    private SettingsTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new SettingsTool(new SettingsServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.21.0");
        stubFor(put(urlEqualTo(PROVIDER)).willReturn(noContent()));
        stubFor(put(urlEqualTo(AUTHENTICATION)).willReturn(noContent()));
    }

    @Test
    void returnsApplications_whenReadingTheProvider() {
        // given
        provider(BASE + "," + APPLICATIONS.replace("}", ",\"unknown\":\"SECRET_SENTINEL\"}"));

        // when
        String result = tool.getOAuthProviderSettings("example");

        // then
        assertThat(result)
                .contains("applications", "Example ID", "ilm-apps", "openid", "https://ilm.example.test/api")
                .doesNotContain("SECRET_SENTINEL");
    }

    @Test
    void returnsApplications_whenReadingAuthenticationSettings() {
        // given
        stubFor(get(urlEqualTo(AUTHENTICATION))
                .willReturn(okJson("{\"disableLocalhostUser\":true,\"oauth2Providers\":{\"example\":{" + BASE + ","
                        + APPLICATIONS + "}}}")));

        // when
        String result = tool.getAuthenticationSettings();

        // then
        assertThat(result).contains("applications", "Example ID", "ilm-apps", "https://ilm.example.test/api");
    }

    @Test
    void keepsStoredApplications_whenUpdatingAnotherField() {
        // given
        provider(BASE + "," + APPLICATIONS);

        // when
        String result = tool.updateOAuthProviderSettings("example", "{\"skew\":45}");

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER))
                .withRequestBody(equalToJson("{" + BASE + "," + APPLICATIONS + ",\"skew\":45}")));
    }

    @Test
    void mergesApplications_whenUpdatingPartOfTheBlock() {
        // given
        provider(BASE + "," + APPLICATIONS);

        // when
        String result = tool.updateOAuthProviderSettings("example", "{\"applications\":{\"enabled\":false}}");

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER))
                .withRequestBody(equalToJson("{" + BASE + "," + APPLICATIONS.replace("true", "false") + "}")));
    }

    @Test
    void removesApplications_whenUpdatingWithNull() {
        // given
        provider(BASE + "," + APPLICATIONS);

        // when
        String result = tool.updateOAuthProviderSettings("example", "{\"applications\":null}");

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER))
                .withRequestBody(equalToJson("{" + BASE + ",\"applications\":null}")));
    }

    @Test
    void sendsApplications_whenReplacingProviders() {
        // given
        stubFor(get(urlEqualTo(AUTHENTICATION)).willReturn(okJson("{\"disableLocalhostUser\":true}")));

        // when
        String result = tool.replaceOAuthProviders(replacement(APPLICATIONS));

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(AUTHENTICATION))
                .withRequestBody(
                        equalToJson("{\"disableLocalhostUser\":true,\"oauth2Providers\":[{\"name\":\"example\","
                                + "\"clientSecret\":\"FRESH_SENTINEL\"," + BASE + "," + APPLICATIONS + "}]}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.17.0", "2.20.0"})
    void refusesApplications_whenIlmIsOlderThan221(String version) {
        // given
        IlmTestSupport.stubIlmVersion(version);
        provider(BASE);

        // when
        String update = tool.updateOAuthProviderSettings("example", "{" + APPLICATIONS + "}");
        String replace = tool.replaceOAuthProviders(replacement(APPLICATIONS));

        // then
        assertThat(update).contains("2.21").doesNotContain("updated");
        assertThat(replace).contains("2.21").doesNotContain("updated");
        verify(0, putRequestedFor(anyUrl()));
        verify(0, getRequestedFor(urlEqualTo(PROVIDER)));
    }

    @ParameterizedTest
    @MethodSource("invalidApplications")
    void refusesApplications_whenTheShapeIsInvalid(String applications) {
        // given
        var fields = "\"applications\":" + applications;

        // when
        String update = tool.updateOAuthProviderSettings("example", "{" + fields + "}");
        String replace = tool.replaceOAuthProviders(replacement(fields));

        // then
        assertThat(update).doesNotContain("updated");
        assertThat(replace).doesNotContain("updated", "FRESH_SENTINEL");
        verify(0, putRequestedFor(anyUrl()));
        verify(0, getRequestedFor(urlEqualTo(PROVIDER)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":null,\"clientId\":null,\"resource\":null,\"scopes\":[]}",
            "{\"resource\":\"  \"}"})
    void acceptsApplications_whenOptionalFieldsAreEmptyOrAbsent(String applications) {
        // given
        provider(BASE);

        // when
        String result = tool.updateOAuthProviderSettings("example", "{\"applications\":" + applications + "}");

        // then
        assertThat(result).contains("updated");
    }

    @ParameterizedTest
    @MethodSource("conformantResources")
    void acceptsResource_whenItIsConformant(String conformantResource) {
        // given
        provider(BASE);
        var applications = "{\"applications\":{\"resource\":\"" + conformantResource + "\"}}";

        // when
        String result = tool.updateOAuthProviderSettings("example", applications);

        // then
        assertThat(result).contains("updated");
        verify(putRequestedFor(urlEqualTo(PROVIDER))
                .withRequestBody(matchingJsonPath("$.applications.resource", equalTo(conformantResource))));
    }

    @ParameterizedTest
    @MethodSource("nonConformantResources")
    void refusesResource_whenItIsNotConformant(String nonConformantResource) {
        // given
        provider(BASE);
        var applications = "{\"applications\":{\"resource\":\"" + nonConformantResource + "\"}}";

        // when
        String result = tool.updateOAuthProviderSettings("example", applications);

        // then
        assertThat(result)
                .isEqualTo(
                        "The resource for applications must be an absolute http or https URI without credentials or a fragment.");
        verify(0, putRequestedFor(anyUrl()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static Stream<String> invalidApplications() {
        return Stream
                .of("[]", "\"enabled\"", "true", "{\"unknown\":1}", "{\"enabled\":null}", "{\"enabled\":\"true\"}",
                        "{\"name\":1}", "{\"clientId\":{}}", "{\"scopes\":\"openid\"}", "{\"scopes\":[1]}",
                        "{\"scopes\":[\"\"]}", "{\"scopes\":[\" \"]}", "{\"resource\":1}");
    }

    static Stream<String> conformantResources() {
        return Stream
                .of("https://ilm.example.com/api", "http://localhost:8080/api?tenant=a", "https://ilm.example.com",
                        "https://10.0.0.1/api", "https://[2001:db8::1]/api", "https://ilm.example.com/a%20b",
                        "HTTPS://ILM.example.com/api", "https://ilm.example.com/api?x=1&y=%2F",
                        "https://xn--ilm-9na.example/api", "https://ilm.example.com:99999/api");
    }

    static Stream<String> nonConformantResources() {
        return Stream
                .of("ilm.example.com/api", "ftp://ilm.example.com/api", "https://ilm.example.com/api#part",
                        "https://ilm.example.com/api#", "https://user:secret@ilm.example.com/api",
                        "https://user@ilm.example.com/api", "https://@ilm.example.com/api",
                        "https://ilm.example.com/a b", "https://ilm.example.com/a%zz", "https://exa_mple.com/api",
                        "https:///api", "https://[bad", "https://ilm.example.com/a|b", "https://-ilm.example.com/api");
    }

    private void provider(String fields) {
        stubFor(get(urlEqualTo(PROVIDER)).willReturn(okJson("{" + fields + "}")));
    }

    private String replacement(String fields) {
        return "{\"oauth2Providers\":[{\"name\":\"example\",\"clientSecret\":\"FRESH_SENTINEL\"," + BASE + "," + fields
                + "}]}";
    }
}
