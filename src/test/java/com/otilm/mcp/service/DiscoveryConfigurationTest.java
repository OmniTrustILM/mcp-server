package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.DiscoveryManagementTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class DiscoveryConfigurationTest {
    private static final String C = "11111111-1111-4111-8111-111111111111";
    private static final String D = "22222222-2222-4222-8222-222222222222";
    private DiscoveryManagementTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new DiscoveryManagementTool(new DiscoveryManagementServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/discoveries")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/connectors/" + C + "/attributes/discoveryProvider/IP-HostName"))
                .willReturn(okJson("[]")));
    }

    @Test
    void baselineStartValidatesSchemasAndReportsRunState() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubFor(post(urlEqualTo("/v1/discoveries")).willReturn(okJson("{\"uuid\":\"" + D + "\"}")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + D))
                .willReturn(okJson("{\"uuid\":\"" + D
                        + "\",\"status\":\"In Progress\",\"attributes\":[{\"data\":\"SENTINEL\"}]}")));
        assertThat(
                tool.startDiscovery("{\"name\":\"Network\",\"connectorUuid\":\"" + C + "\",\"kind\":\"IP-HostName\"}"))
                .contains(D, "accepted", "In Progress")
                .doesNotContain("SENTINEL", "completed");
        verify(postRequestedFor(urlEqualTo("/v1/discoveries"))
                .withRequestBody(equalToJson("{\"name\":\"Network\",\"connectorUuid\":\"" + C
                        + "\",\"kind\":\"IP-HostName\",\"attributes\":[],\"customAttributes\":[]}")));
    }

    @Test
    void resourceDiscoveryUsesAdvertisedChoicesAndResourceSchemas() {
        stubFor(get(urlEqualTo("/v1/discoveries/" + C + "/resources"))
                .willReturn(okJson("[{\"resource\":\"certificates\"}]")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + C + "/attributes")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + C + "/certificates/attributes")).willReturn(okJson("[]")));
        stubFor(post(urlEqualTo("/v1/discoveries")).willReturn(okJson("{\"uuid\":\"" + D + "\"}")));
        assertThat(tool
                .startDiscovery("{\"name\":\"Multi\",\"connectorUuid\":\"" + C
                        + "\",\"resources\":[\"certificates\"],\"resourceAttributes\":{\"certificates\":{}}}"))
                .contains(D, "accepted");
        verify(postRequestedFor(urlEqualTo("/v1/discoveries"))
                .withRequestBody(matchingJsonPath("$.resourceAttributes.certificates", equalToJson("[]"))));
        assertThat(tool.startDiscovery("{\"name\":\"Multi\",\"connectorUuid\":\"" + C + "\",\"resources\":[\"keys\"]}"))
                .contains("resource");
        verify(1, postRequestedFor(urlEqualTo("/v1/discoveries")));
    }

    @Test
    void schedulingStoresTheValidatedDiscoveryAndRunControlsAreVersionGated() {
        stubFor(post(urlEqualTo("/v1/discoveries/schedule")).willReturn(okJson("{\"uuid\":\"" + D + "\"}")));
        assertThat(tool
                .scheduleDiscovery("Nightly", "0 0 2 * * ?", false,
                        "{\"name\":\"Network\",\"connectorUuid\":\"" + C + "\",\"kind\":\"IP-HostName\"}"))
                .contains(D, "scheduled");
        verify(postRequestedFor(urlEqualTo("/v1/discoveries/schedule"))
                .withRequestBody(matchingJsonPath("$.request.connectorUuid", equalTo(C)))
                .withRequestBody(matchingJsonPath("$.cronExpression", equalTo("0 0 2 * * ?"))));
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(tool.stopDiscovery(D)).contains("2.20");
        assertThat(tool.getDiscoveryItems(D, null, null, 1, 10)).contains("2.20");
        verify(0, patchRequestedFor(anyUrl()));
    }

    @Test
    void resultSchemasRunMessagesControlsAndDeletionUseReleasedRoutes() {
        stubFor(get(urlEqualTo("/v1/discoveries/" + D))
                .willReturn(okJson(
                        "{\"uuid\":\"" + D + "\",\"status\":\"Stopped\",\"attributes\":[{\"data\":\"SENTINEL\"}]}")));
        stubFor(get(
                urlEqualTo("/v1/discoveries/" + D + "/certificates?pageNumber=1&itemsPerPage=10&newlyDiscovered=true"))
                .willReturn(okJson(
                        "{\"certificates\":[{\"inventoryUuid\":\"" + C + "\",\"commonName\":\"synthetic.test\"}]}")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + D
                + "/items?pageNumber=1&itemsPerPage=10&resource=certificates&newlyDiscovered=false"))
                .willReturn(okJson("{\"items\":[{\"inventory\":{\"uuid\":\"" + C
                        + "\"},\"sequence\":3,\"payload\":{\"keyData\":\"SENTINEL\"}}]}")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + D + "/messages?pageNumber=1&itemsPerPage=10"))
                .willReturn(okJson("{\"items\":[{\"severity\":\"warning\",\"code\":\"RETRY\",\"occurrences\":2}]}")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + C + "/resources"))
                .willReturn(okJson("[{\"resource\":\"certificates\"}]")));
        stubFor(get(urlEqualTo("/v1/discoveries/" + C + "/certificates/attributes")).willReturn(okJson("[]")));
        for (String action : new String[]{"stop", "resume", "cancel"}) {
            stubFor(patch(urlEqualTo("/v1/discoveries/" + D + "/" + action)).willReturn(noContent()));
        }
        stubFor(delete(urlEqualTo("/v1/discoveries/" + D)).willReturn(noContent()));
        assertThat(tool.getDiscovery(D)).contains("Stopped").doesNotContain("SENTINEL");
        assertThat(tool.getDiscoveryCertificates(D, true, 1, 10)).contains(C, "synthetic.test");
        assertThat(tool.getDiscoveryItems(D, "certificates", false, 1, 10)).contains(C, "3").doesNotContain("SENTINEL");
        assertThat(tool.getDiscoveryMessages(D, 1, 10)).contains("RETRY", "2");
        assertThat(tool.listDiscoveryResources(C)).contains("certificates");
        assertThat(tool.getDiscoveryAttributes(C, "IP-HostName", null, null, null)).contains("Discovery settings");
        assertThat(tool.getDiscoveryAttributes(C, null, null, "certificates", null)).contains("Discovery settings");
        assertThat(tool.stopDiscovery(D)).contains("accepted", "Stopped");
        assertThat(tool.resumeDiscovery(D)).contains("accepted");
        assertThat(tool.cancelDiscovery(D)).contains("accepted");
        assertThat(tool.deleteDiscovery(D)).contains("Deleted", D);
    }

    @ParameterizedTest
    @ValueSource(strings = {"certificates", "items", "messages"})
    void discoveryResultsUseConsecutiveOneBasedPages(String resultKind) {
        String itemField = switch (resultKind) {
            case "certificates" -> "commonName";
            case "items" -> "uniqueRef";
            default -> "code";
        };
        String arrayField = resultKind.equals("certificates") ? "certificates" : "items";
        String firstPage = "/v1/discoveries/" + D + "/" + resultKind + "?pageNumber=1&itemsPerPage=10";
        String secondPage = "/v1/discoveries/" + D + "/" + resultKind + "?pageNumber=2&itemsPerPage=10";
        stubFor(get(urlEqualTo(firstPage))
                .willReturn(okJson("{\"" + arrayField + "\":[{\"" + itemField + "\":\"first-page\"}]}")));
        stubFor(get(urlEqualTo(secondPage))
                .willReturn(okJson("{\"" + arrayField + "\":[{\"" + itemField + "\":\"second-page\"}]}")));
        assertThat(discoveryResults(resultKind, 1)).contains("first-page").doesNotContain("second-page");
        assertThat(discoveryResults(resultKind, 2)).contains("second-page").doesNotContain("first-page");
        verify(getRequestedFor(urlEqualTo(firstPage)));
        verify(getRequestedFor(urlEqualTo(secondPage)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"certificates", "items", "messages"})
    void discoveryResultsRejectPageZeroBeforeAnyHttpRequest(String resultKind) {
        stubFor(get(urlEqualTo("/v1/discoveries/" + D + "/" + resultKind + "?pageNumber=0&itemsPerPage=10"))
                .willReturn(okJson("{\"certificates\":[],\"items\":[]}")));
        assertThat(discoveryResults(resultKind, 0)).contains("page starts at 1");
        verify(0, anyRequestedFor(anyUrl()));
    }

    private String discoveryResults(String resultKind, int page) {
        return switch (resultKind) {
            case "certificates" -> tool.getDiscoveryCertificates(D, null, page, 10);
            case "items" -> tool.getDiscoveryItems(D, null, null, page, 10);
            case "messages" -> tool.getDiscoveryMessages(D, page, 10);
            default -> throw new IllegalArgumentException("Unknown discovery result kind");
        };
    }

    @Test
    void callbackFieldsAreResolvedBeforeDiscoveryMutationAndRunEventIsRefused() {
        stubFor(get(urlEqualTo("/v1/connectors/" + C + "/attributes/discoveryProvider/IP-HostName"))
                .willReturn(
                        okJson("""
                                [{"name":"options","version":2,"type":"data","contentType":"string","properties":{"list":true},"attributeCallback":{"dependsOn":[]}}]
                                """)));
        stubFor(post(urlEqualTo("/v1/connectors/" + C + "/discoveryProvider/IP-HostName/callback"))
                .willReturn(okJson("[{\"data\":\"Network A\",\"reference\":\"network-ref\"}]")));
        stubFor(post(urlEqualTo("/v1/discoveries")).willReturn(okJson("{\"uuid\":\"" + D + "\"}")));
        assertThat(tool
                .startDiscovery("{\"name\":\"Network\",\"connectorUuid\":\"" + C
                        + "\",\"kind\":\"IP-HostName\",\"attributes\":{\"options\":\"Network A\"}}"))
                .contains(D, "accepted");
        verify(postRequestedFor(urlEqualTo("/v1/discoveries"))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].reference", equalTo("network-ref"))));
        stubFor(get(urlEqualTo("/v1/workflows/triggers/" + D))
                .willReturn(okJson("{\"resource\":\"discoveries\",\"event\":\"discovery_finished\"}")));
        assertThat(tool
                .startDiscovery("{\"name\":\"Network\",\"connectorUuid\":\"" + C
                        + "\",\"kind\":\"IP-HostName\",\"attributes\":{\"options\":\"Network A\"},\"triggers\":[\"" + D
                        + "\"]}"))
                .contains("certificate_discovered", "discovery_finished");
        verify(1, postRequestedFor(urlEqualTo("/v1/discoveries")));
    }

}
