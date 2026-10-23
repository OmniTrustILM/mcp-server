package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.CredentialConfigurationTool;
import com.otilm.mcp.tool.ProxyTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
class ProviderConfigurationTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private static final String CONNECTOR = "00000000-0000-4000-8000-000000000002";
    private static final String FIELD = "00000000-0000-4000-8000-000000000003";
    private static final String SCHEMA = "[{\"uuid\":\"" + FIELD
            + "\",\"name\":\"password\",\"type\":\"data\",\"version\":2,\"contentType\":\"secret\",\"properties\":{\"required\":true}}]";
    private CredentialConfigurationTool credentials;
    private ProxyTool proxies;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        ConfigurationApiClient api = new ConfigurationApiClient(
                IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build());
        credentials = new CredentialConfigurationTool(new CredentialConfigurationServiceImpl(api));
        proxies = new ProxyTool(new ProxyServiceImpl(api, new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/credentials")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR + "/attributes/credentialProvider/Basic"))
                .willReturn(okJson(SCHEMA)));
    }

    @Test
    void createsTypedCredentialsAndReturnsMetadataOnly() {
        stubFor(post(urlEqualTo("/v1/credentials"))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"attributes\":[{\"data\":\"private-sentinel\"}]}")));
        assertThat(credentials
                .createCredential("{\"name\":\"Basic credential\",\"kind\":\"Basic\",\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"attributes\":{\"password\":{\"secret\":\"private-sentinel\"}}}"))
                .contains(ID)
                .doesNotContain("private-sentinel");
        verify(postRequestedFor(urlEqualTo("/v1/credentials"))
                .withRequestBody(
                        matchingJsonPath("$.attributes[0].content[0].data.secret", equalTo("private-sentinel"))));
    }

    @Test
    void updateRequiresFreshProtectedValuesAndRetainsReadableCustomValues() {
        stubFor(get(urlEqualTo("/v1/credentials/" + ID))
                .willReturn(okJson("{\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"Basic\",\"attributes\":[{\"uuid\":\"" + FIELD
                        + "\",\"name\":\"password\",\"contentType\":\"secret\",\"content\":[{\"data\":\"********\"}]}],\"customAttributes\":[]}")));
        assertThat(credentials.updateCredential(ID, "{}")).contains("fresh");
        verify(0, putRequestedFor(anyUrl()));
        stubFor(put(urlEqualTo("/v1/credentials/" + ID)).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        assertThat(credentials.updateCredential(ID, "{\"attributes\":{\"password\":{\"secret\":\"new-value\"}}}"))
                .contains(ID);
        verify(putRequestedFor(urlEqualTo("/v1/credentials/" + ID))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data.secret", equalTo("new-value"))));
    }

    @Test
    void credentialsNeverExposeDecryptedDetailOrErrors() {
        stubFor(get(urlEqualTo("/v1/credentials"))
                .willReturn(okJson("[{\"uuid\":\"" + ID
                        + "\",\"name\":\"Basic\",\"attributes\":[{\"name\":\"password\",\"content\":[{\"data\":\"private-sentinel\"}]}]}]")));
        assertThat(credentials.listCredentials()).contains("Basic").doesNotContain("private-sentinel");
        stubFor(get(urlEqualTo("/v1/credentials/" + ID))
                .willReturn(aResponse().withStatus(500).withBody("private-sentinel")));
        assertThat(credentials.getCredential(ID)).contains("500").doesNotContain("private-sentinel");
    }

    @Test
    void credentialsUseTargetedLifecycleAndNormalDelete() {
        stubFor(patch(urlEqualTo("/v1/credentials/" + ID + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/credentials/" + ID + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/credentials/" + ID)).willReturn(noContent()));
        assertThat(credentials.enableCredential(ID)).contains("enabled");
        assertThat(credentials.disableCredential(ID)).contains("disabled");
        assertThat(credentials.deleteCredential(ID)).contains("deleted");
        assertThat(credentials.getCredentialAttributes(CONNECTOR, "Basic"))
                .contains("password")
                .doesNotContain("private-sentinel");
    }

    @Test
    void proxiesAreGatedAndOnlyDescriptionIsEditable() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(proxies.createProxy("{\"name\":\"Proxy\"}")).contains("2.18");
        verify(0, postRequestedFor(anyUrl()));
        assertThat(proxies.updateProxy(ID, "{\"name\":\"Rename\"}")).contains("field");
    }

    @Test
    void proxyCrudAndInstructionsUsePublishedFields() {
        stubFor(post(urlEqualTo("/v1/proxies")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(put(urlEqualTo("/v1/proxies/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"description\":\"Updated\"}")));
        stubFor(get(urlEqualTo("/v1/proxies")).willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"name\":\"Proxy\"}]")));
        stubFor(get(urlEqualTo("/v1/proxies/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"status\":\"connected\"}")));
        stubFor(get(urlEqualTo("/v1/proxies/" + ID + "/instructions"))
                .willReturn(okJson("{\"uuid\":\"" + ID + "\",\"installationInstructions\":\"helm install proxy\"}")));
        stubFor(delete(urlEqualTo("/v1/proxies/" + ID)).willReturn(noContent()));
        assertThat(proxies.createProxy("{\"name\":\"Proxy\",\"description\":\"DMZ\"}")).contains(ID);
        assertThat(proxies.updateProxy(ID, "{\"description\":\"Updated\"}")).contains("Updated");
        assertThat(proxies.listProxies()).contains("Proxy");
        assertThat(proxies.getProxy(ID)).contains("connected");
        assertThat(proxies.getProxyInstallationInstructions(ID)).contains("helm install");
        assertThat(proxies.deleteProxy(ID)).contains("deleted");
    }
}
