package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.ConnectorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
class ConnectorConfigurationTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private static final String PROXY = "00000000-0000-4000-8000-000000000002";
    private ConnectorTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        tool = new ConnectorTool(new ConnectorServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/connectors")).willReturn(okJson("[]")));
    }

    @Test
    void createsFrameworkTwoConnectorWithTypedAuthenticationWithoutLeakingIt() {
        stubFor(get(urlEqualTo("/v1/connectors/auth/attributes/basic"))
                .willReturn(
                        okJson("""
                                [{"uuid":"00000000-0000-4000-8000-000000000011","name":"username","type":"data","contentType":"string","version":2,
                                  "properties":{"required":true}},
                                 {"uuid":"00000000-0000-4000-8000-000000000012","name":"password","type":"data","contentType":"secret","version":2,
                                  "properties":{"required":true}}]
                                """)));
        stubFor(post(urlEqualTo("/v2/connectors")).willReturn(okJson("""
                {"uuid":"%s","name":"Authority connector","version":"v2","status":"connected",
                 "authAttributes":[{"content":[{"data":{"secret":"private-sentinel"}}]}]}
                """.formatted(ID))));
        String result = tool.createConnector("""
                {"name":"Authority connector","version":"v2","url":"https://connector.example.test",
                 "authType":"basic","authAttributes":{"username":"operator","password":{"secret":"private-sentinel"}}}
                """);
        assertThat(result).contains(ID, "Authority connector", "connected").doesNotContain("private-sentinel");
        verify(postRequestedFor(urlEqualTo("/v2/connectors"))
                .withRequestBody(matchingJsonPath("$.version", equalTo("v2")))
                .withRequestBody(matchingJsonPath("$.authAttributes[1].version", equalTo("v2")))
                .withRequestBody(
                        matchingJsonPath("$.authAttributes[1].content[0].data.secret", equalTo("private-sentinel")))
                .withRequestBody(matchingJsonPath("$.customAttributes", equalToJson("[]"))));
    }

    @Test
    void updatesCustomValuesWithoutClearingUnchangedReadableValues() {
        stubFor(get(urlEqualTo("/v2/connectors/" + ID))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","url":"https://old.example.test","authType":"none","version":"v2",
                                 "customAttributes":[{"uuid":"00000000-0000-4000-8000-000000000013","name":"tag","contentType":"string","version":"v2",
                                 "content":[{"data":"retained"}]}]}
                                """
                                .formatted(ID))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/connectors"))
                .willReturn(
                        okJson("""
                                [{"uuid":"00000000-0000-4000-8000-000000000013","name":"tag","type":"custom","contentType":"string","version":2}]
                                """)));
        stubFor(put(urlEqualTo("/v2/connectors/" + ID)).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        assertThat(tool.updateConnector(ID, """
                {"url":"https://new.example.test","authType":"none","noProxy":true}
                """)).contains(ID);
        verify(putRequestedFor(urlEqualTo("/v2/connectors/" + ID))
                .withRequestBody(matchingJsonPath("$.customAttributes[0].content[0].data", equalTo("retained"))));
    }

    @Test
    void refusesMissingFreshCredentialsBeforeMutation() {
        stubFor(get(urlEqualTo("/v2/connectors/" + ID))
                .willReturn(okJson("{\"authType\":\"basic\",\"customAttributes\":[]}")));
        stubFor(get(urlEqualTo("/v1/connectors/auth/attributes/basic"))
                .willReturn(
                        okJson("""
                                [{"uuid":"00000000-0000-4000-8000-000000000012","name":"password","type":"data","contentType":"secret","version":2,
                                  "properties":{"required":true}}]
                                """)));
        assertThat(tool
                .updateConnector(ID,
                        "{\"url\":\"https://connector.example.test\",\"authType\":\"basic\",\"noProxy\":true}"))
                .contains("password", "fresh");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void directConnectionsWorkOnBaselineButProxySelectionIsVersionGated() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(tool.createConnector("""
                {"name":"Proxy connector","version":"v2","url":"https://connector.example.test",
                 "authType":"none","proxyUuid":"%s"}
                """.formatted(PROXY))).contains("2.18");
        verify(0, postRequestedFor(anyUrl()));
        stubFor(post(urlEqualTo("/v2/connectors")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        assertThat(tool.createConnector("""
                {"name":"Direct connector","version":"v2","url":"https://connector.example.test","authType":"none"}
                """)).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v2/connectors")).withRequestBody(notMatching(".*proxyUuid.*")));
    }

    @Test
    void probeUsesRegisteredProxyIdentityAndDoesNotRegisterAnything() {
        stubFor(get(urlEqualTo("/v1/proxies/" + PROXY))
                .willReturn(okJson("{\"uuid\":\"" + PROXY + "\",\"code\":\"proxy-one\"}")));
        stubFor(post(urlEqualTo("/v2/connectors/connect"))
                .willReturn(okJson("[{\"version\":\"v2\",\"status\":\"connected\"}]")));
        assertThat(tool.testConnectorConnection("""
                {"url":"https://connector.example.test","authType":"none","proxyUuid":"%s"}
                """.formatted(PROXY))).contains("v2");
        verify(postRequestedFor(urlEqualTo("/v2/connectors/connect"))
                .withRequestBody(matchingJsonPath("$.proxy.code", equalTo("proxy-one"))));
        verify(0, postRequestedFor(urlEqualTo("/v2/connectors")));
    }

    @Test
    void rejectsCredentialUrlsUnknownFieldsAndInvalidProxyIntentWithoutCallingIlm() {
        assertThat(tool.createConnector("""
                {"name":"Bad connector","version":"v2","url":"https://operator:private-sentinel@example.test",
                 "authType":"none"}
                """)).contains("URL").doesNotContain("private-sentinel");
        assertThat(tool.createConnector("{\"endpoint\":\"/unsafe\"}")).contains("field");
        assertThat(tool.updateConnector(ID, "{\"url\":\"https://connector.example.test\",\"authType\":\"none\"}"))
                .contains("proxy");
        verify(0, postRequestedFor(anyUrl()));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void ordinaryDetailOmitsCredentialsAndFrameworkOneInfoIsExplicitlyUnsupported() {
        stubFor(get(urlEqualTo("/v2/connectors/" + ID))
                .willReturn(
                        okJson("""
                                {"uuid":"%s","name":"Legacy","version":"v1","authType":"basic",
                                 "authAttributes":[{"data":"private-sentinel"}],"interfaces":[{"uuid":"00000000-0000-4000-8000-000000000014","code":"authority","version":"v3"}]}
                                """
                                .formatted(ID))));
        assertThat(tool.getConnector(ID))
                .contains("Legacy", "00000000-0000-4000-8000-000000000014")
                .doesNotContain("private-sentinel");
        assertThat(tool.getConnectorInfo(ID)).contains("framework v2");
        verify(0, getRequestedFor(urlEqualTo("/v2/connectors/" + ID + "/info")));
    }

    @Test
    void frameworkTwoInfoHealthReconnectAndApprovalUsePublishedOperations() {
        stubFor(get(urlEqualTo("/v2/connectors/" + ID)).willReturn(okJson("{\"version\":\"v2\"}")));
        stubFor(get(urlEqualTo("/v2/connectors/" + ID + "/info"))
                .willReturn(okJson(
                        "{\"id\":\"ConnectorSoftware\",\"name\":\"Provider\",\"version\":\"1.0\",\"metadata\":{\"privateKey\":\"private-sentinel\"}}")));
        stubFor(get(urlEqualTo("/v2/connectors/" + ID + "/health"))
                .willReturn(
                        okJson("{\"status\":\"ok\",\"components\":{\"auth\":{\"message\":\"private-sentinel\"}}}")));
        stubFor(post(urlEqualTo("/v2/connectors/" + ID + "/reconnect"))
                .willReturn(okJson("{\"version\":\"v2\",\"connectorUuid\":\"" + ID
                        + "\",\"errorMessage\":\"private-sentinel\"}")));
        stubFor(patch(urlEqualTo("/v2/connectors/" + ID + "/approve")).willReturn(noContent()));
        assertThat(tool.getConnectorInfo(ID))
                .contains("Provider", "ConnectorSoftware")
                .doesNotContain("private-sentinel");
        assertThat(tool.getConnectorHealth(ID)).contains("ok").doesNotContain("private-sentinel");
        assertThat(tool.reconnectConnector(ID)).contains("failed", ID).doesNotContain("private-sentinel");
        assertThat(tool.approveConnector(ID)).contains("approved");
        assertThat(tool.getConnectorAuthenticationAttributes("none")).contains("authentication");
        assertThat(tool.getConnectorAuthenticationAttributes("jwt")).contains("not implemented");
    }

    @Test
    void probesReportConnectionFailureWithoutEchoingProviderErrors() {
        stubFor(post(urlEqualTo("/v2/connectors/connect"))
                .willReturn(okJson("[{\"version\":\"v2\",\"errorMessage\":\"private-sentinel\"}]")));
        assertThat(tool.testConnectorConnection("{\"url\":\"https://connector.example.test\",\"authType\":\"none\"}"))
                .contains("failed")
                .doesNotContain("private-sentinel");
    }

    @Test
    void refusesDependenciesWithoutForcingDeletion() {
        stubFor(delete(urlEqualTo("/v2/connectors/" + ID))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Vault production still uses connector.\"}")));
        assertThat(tool.deleteConnector(ID)).contains("Vault production");
        verify(1, deleteRequestedFor(urlEqualTo("/v2/connectors/" + ID)));
        verify(0, deleteRequestedFor(urlPathMatching(".*force.*")));
    }
}
