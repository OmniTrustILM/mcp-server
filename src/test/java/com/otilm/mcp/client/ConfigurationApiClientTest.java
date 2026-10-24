package com.otilm.mcp.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest
@ExtendWith(OutputCaptureExtension.class)
class ConfigurationApiClientTest {

    private ConfigurationApiClient api;

    @BeforeEach
    void setUp(WireMockRuntimeInfo server) {
        api = new ConfigurationApiClient(IlmApiClient.restClientBuilder(server.getHttpBaseUrl()).build());
    }

    @Test
    void readsJsonWithEncodedPathVariables() {
        stubFor(get(urlEqualTo("/objects/a%2Fb"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(okJson("{\"name\":\"example\"}")));
        assertThat(api.get("/objects/{id}", "a/b").path("name").asString()).isEqualTo("example");
    }

    @Test
    void writesExactJsonAndReadsCreatedIdentifier() {
        stubFor(post(urlEqualTo("/objects"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"name\":\"example\"}"))
                .willReturn(created()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"uuid\":\"00000000-0000-4000-8000-000000000001\"}")));
        assertThat(api.post("/objects", Map.of("name", "example")).path("uuid").asString())
                .isEqualTo("00000000-0000-4000-8000-000000000001");
    }

    @Test
    void explicitNullsSurviveNestedJsonConfigurationWrites() {
        stubFor(put(urlEqualTo("/configuration")).willReturn(noContent()));
        var body = tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode();
        body.putNull("ownerUuid");
        body.putObject("recordPolicy").putNull("retentionDays");
        api.put("/configuration", body);
        verify(putRequestedFor(urlEqualTo("/configuration")).withRequestBody(equalToJson("""
                {"ownerUuid":null,"recordPolicy":{"retentionDays":null}}
                """)));
    }

    @Test
    void supportsPatchPutAndBodyBearingDeletionWithoutResponseBodies() {
        stubFor(patch(urlEqualTo("/objects/one")).willReturn(noContent()));
        stubFor(put(urlEqualTo("/objects/one")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/objects/one")).willReturn(noContent()));
        assertThat(api.patch("/objects/{id}", Map.of("enabled", true), "one").isNull()).isTrue();
        assertThat(api.put("/objects/{id}", Map.of("name", "new"), "one").isNull()).isTrue();
        api.deleteBody("/objects/{id}", List.of("selected-item"), "one");
        verify(deleteRequestedFor(urlEqualTo("/objects/one")).withRequestBody(equalToJson("[\"selected-item\"]")));
    }

    @Test
    void errorDoesNotEchoCredentialOrUpstreamBody() {
        stubFor(post(urlEqualTo("/objects"))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"private-sentinel\"}")));
        Map<String, String> body = Map.of("password", "private-sentinel");
        assertThatThrownBy(() -> api.post("/objects", body))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("422")
                .hasMessageNotContaining("private-sentinel")
                .hasNoCause();
    }

    @Test
    void deletionReportsSafeDependencyRefusal() {
        stubFor(delete(urlEqualTo("/objects/one"))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Cannot delete: vault example still uses the connector.\"}")));
        assertThatThrownBy(() -> api.deleteWithDependencies("/objects/{id}", "one"))
                .hasMessage("ILM answered HTTP 422. Deletion was refused; check dependencies in ILM.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"message\":\"Dependent credentials: OAuth client_secret=private-sentinel at https://user:private-sentinel@connector.example.test\"}",
            "[\"Dependent Vault Instances: production\",\"OAuth client secret is private-sentinel\",\"-----BEGIN PRIVATE KEY----- private-sentinel\"]"})
    void dependencyRefusalNeverExposesUpstreamDiagnostics(String diagnostic, CapturedOutput output) {
        stubFor(delete(urlEqualTo("/objects/one"))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody(diagnostic)));

        assertThatThrownBy(() -> api.deleteWithDependencies("/objects/{id}", "one"))
                .isInstanceOf(ConfigurationApiClient.ApiException.class)
                .hasMessage("ILM answered HTTP 422. Deletion was refused; check dependencies in ILM.")
                .hasNoCause();
        assertThat(output.getAll()).doesNotContain("private-sentinel", "-----BEGIN PRIVATE KEY-----");
        verify(1, deleteRequestedFor(urlEqualTo("/objects/one")));
    }

    @Test
    void ordinaryDeletionDoesNotExposeAnUpstreamAuthenticationDiagnostic() {
        stubFor(delete(urlEqualTo("/provider"))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"OAuth client secret is private-sentinel\"}")));
        assertThatThrownBy(() -> api.delete("/provider"))
                .hasMessageContaining("422")
                .hasMessageNotContaining("private-sentinel");
    }

    @Test
    void downloadsMaterialWithItsMediaTypeAndSafeFilename() {
        stubFor(post(urlEqualTo("/export")).atPriority(10).willReturn(aResponse().withStatus(406)));
        stubFor(post(urlEqualTo("/export"))
                .atPriority(1)
                .withHeader("Accept", equalTo("*/*"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"passphrase\":\"test-passphrase\"}"))
                .willReturn(ok("encrypted-material")
                        .withHeader("Content-Type", "application/octet-stream")
                        .withHeader("Content-Disposition", "attachment; filename=key.pem")));
        ConfigurationApiClient.BinaryContent result = api.download("/export", Map.of("passphrase", "test-passphrase"));
        assertThat(result.filename()).isEqualTo("key.pem");
        assertThat(result.contentType()).isEqualTo("application/octet-stream");
        assertThat(result.data()).isEqualTo("encrypted-material".getBytes(StandardCharsets.UTF_8));
        assertThat(result.toString()).doesNotContain("encrypted-material", "test-passphrase");
    }
}
