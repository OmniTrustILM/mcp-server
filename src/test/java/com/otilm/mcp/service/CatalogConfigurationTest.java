package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.MetadataTool;
import com.otilm.mcp.tool.OidTool;
import com.otilm.mcp.tool.TrustTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class CatalogConfigurationTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private MetadataTool metadata;
    private OidTool oids;
    private TrustTool trust;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        var api = new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build());
        var versions = new PlatformVersionCheck(IlmTestSupport.client(wm));
        metadata = new MetadataTool(new MetadataServiceImpl(api));
        oids = new OidTool(new OidServiceImpl(api, versions));
        trust = new TrustTool(new TrustServiceImpl(api, versions));
        IlmTestSupport.stubIlmVersion("2.20.0");
    }

    @Test
    void metadataPatchesRetainOmittedPropertiesWithoutEchoingContent() {
        stubFor(get(urlEqualTo("/v1/attributes/metadata/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID
                        + "\",\"name\":\"source\",\"label\":\"Original\",\"visible\":false,\"group\":\"Fleet\",\"description\":\"Existing\",\"content\":[{\"data\":\"private-sentinel\"}]}")));
        stubFor(put(urlEqualTo("/v1/attributes/metadata/" + ID))
                .willReturn(okJson("{\"uuid\":\"" + ID
                        + "\",\"label\":\"Changed\",\"content\":[{\"data\":\"private-sentinel\"}]}")));
        assertThat(metadata.updateGlobalMetadata(ID, "{\"label\":\"Changed\"}"))
                .contains("Changed", ID)
                .doesNotContain("private-sentinel");
        verify(putRequestedFor(urlEqualTo("/v1/attributes/metadata/" + ID))
                .withRequestBody(equalToJson(
                        "{\"label\":\"Changed\",\"visible\":false,\"group\":\"Fleet\",\"description\":\"Existing\"}")));
        assertThat(metadata.getGlobalMetadata(ID)).contains("Original").doesNotContain("private-sentinel");
    }

    @Test
    void metadataCrudAndPromotionHaveFixedRoutes() {
        stubFor(get(urlEqualTo("/v1/attributes/metadata"))
                .willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"name\":\"source\"}]")));
        stubFor(post(urlEqualTo("/v1/attributes/metadata")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(get(urlEqualTo("/v1/attributes/metadata/promote?connectorUuid=" + ID))
                .willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"name\":\"source\"}]")));
        stubFor(post(urlEqualTo("/v1/attributes/metadata/promote")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(delete(urlEqualTo("/v1/attributes/metadata/" + ID)).willReturn(noContent()));
        assertThat(metadata.listGlobalMetadata()).contains("source");
        assertThat(
                metadata.createGlobalMetadata("{\"name\":\"source\",\"label\":\"Source\",\"contentType\":\"string\"}"))
                .contains(ID);
        assertThat(metadata.listConnectorMetadata(ID)).contains("source");
        assertThat(metadata.promoteConnectorMetadata(ID, ID)).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/attributes/metadata/promote"))
                .withRequestBody(equalToJson("{\"uuid\":\"" + ID + "\",\"connectorUuid\":\"" + ID + "\"}")));
        assertThat(metadata.deleteGlobalMetadata(ID)).contains("deleted");
    }

    @Test
    void oidUpdatesRetainCategoryAndTypedProperties() {
        stubFor(get(urlEqualTo("/v1/oids/1.2.3"))
                .willReturn(okJson(
                        "{\"oid\":\"1.2.3\",\"displayName\":\"Old\",\"description\":\"Keep\",\"category\":\"rdnAttributeType\",\"additionalProperties\":{\"code\":\"CN\",\"altCodes\":[\"commonName\"]}}")));
        stubFor(put(urlEqualTo("/v1/oids/1.2.3")).willReturn(okJson("{\"oid\":\"1.2.3\",\"displayName\":\"New\"}")));
        assertThat(oids.updateCustomOid("1.2.3", "{\"displayName\":\"New\"}")).contains("New");
        verify(putRequestedFor(urlEqualTo("/v1/oids/1.2.3"))
                .withRequestBody(matchingJsonPath("$.additionalProperties.code", equalTo("CN")))
                .withRequestBody(matchingJsonPath("$.category", equalTo("rdnAttributeType"))));
        assertThat(oids.updateCustomOid("1.2.3", "{\"category\":\"generic\"}")).contains("category");
        assertThat(oids.getCustomOid("1.2.3")).contains("CN");
    }

    @Test
    void oidAndTrustNewerFieldsAreVersionGatedBeforeWrites() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(oids.listSystemOids()).contains("2.19");
        assertThat(oids
                .createCustomOid(
                        "{\"oid\":\"1.2.3\",\"displayName\":\"Extension\",\"category\":\"certificateExtension\",\"additionalProperties\":{\"defaultCritical\":false,\"valueEncoding\":\"DER\"}}"))
                .contains("2.19");
        assertThat(trust.addTrustedCertificate("YWJj")).contains("2.18");
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(oids
                .createCustomOid(
                        "{\"oid\":\"1.2.3\",\"displayName\":\"Extension\",\"category\":\"certificateExtension\",\"additionalProperties\":{\"defaultCritical\":false,\"valueEncoding\":\"DER\",\"valueSchema\":\"Module\"}}"))
                .contains("2.20");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void oidCrudAndSearchUseStrictInputsAndPaginatedResults() {
        stubFor(post(urlEqualTo("/v1/oids")).willReturn(okJson("{\"oid\":\"1.2.3\",\"displayName\":\"Example\"}")));
        stubFor(post(urlEqualTo("/v1/oids/list"))
                .willReturn(okJson(
                        "{\"items\":[{\"oid\":\"1.2.3\",\"displayName\":\"Example\"}],\"totalItems\":1,\"pageNumber\":1}")));
        stubFor(get(urlEqualTo("/v1/oids/system")).willReturn(okJson("[{\"oid\":\"1.2.3\"}]")));
        stubFor(delete(urlEqualTo("/v1/oids/1.2.3")).willReturn(noContent()));
        assertThat(oids.createCustomOid("{\"oid\":\"1.2.3\",\"displayName\":\"Example\",\"category\":\"generic\"}"))
                .contains("Example");
        assertThat(oids.listCustomOids(null, 1, 10)).contains("Example", "1");
        assertThat(oids.listSystemOids()).contains("1.2.3");
        assertThat(oids.deleteCustomOid("1.2.3")).contains("deleted");
        assertThat(oids.getCustomOid("../private-sentinel")).contains("OID").doesNotContain("private-sentinel");
        assertThat(oids
                .createCustomOid(
                        "{\"oid\":\"1.2.3\",\"displayName\":\"RDN\",\"category\":\"rdnAttributeType\",\"additionalProperties\":{\"code\":\"bad code\"}}"))
                .contains("code");
    }

    @Test
    void trustOnlyExposesCertificateMetadataAndPreservesBase64OnWire() {
        stubFor(post(urlEqualTo("/v1/trustedCertificates")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        String result = "{\"uuid\":\"" + ID + "\",\"subject\":\"CN=Root\",\"certificateContent\":\"private-sentinel\"}";
        stubFor(get(urlEqualTo("/v1/trustedCertificates")).willReturn(okJson("[" + result + "]")));
        stubFor(get(urlEqualTo("/v1/trustedCertificates/" + ID)).willReturn(okJson(result)));
        stubFor(delete(urlEqualTo("/v1/trustedCertificates/" + ID)).willReturn(noContent()));
        assertThat(trust.addTrustedCertificate("YWJj")).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/trustedCertificates"))
                .withRequestBody(equalToJson("{\"certificateContent\":\"YWJj\"}")));
        assertThat(trust.listTrustedCertificates()).contains("CN=Root").doesNotContain("private-sentinel");
        assertThat(trust.getTrustedCertificate(ID)).contains("CN=Root").doesNotContain("private-sentinel");
        assertThat(trust.deleteTrustedCertificate(ID)).contains("deleted");
        assertThat(trust.addTrustedCertificate("private-sentinel"))
                .contains("Base64")
                .doesNotContain("private-sentinel");
    }
}
