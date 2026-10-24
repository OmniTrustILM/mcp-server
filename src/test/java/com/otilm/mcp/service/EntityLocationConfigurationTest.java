package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.EntityTool;
import com.otilm.mcp.tool.LocationTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
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
class EntityLocationConfigurationTest {
    static final String ENTITY = "00000000-0000-4000-8000-000000000901";
    static final String LOCATION = "00000000-0000-4000-8000-000000000902";
    static final String CONNECTOR = "00000000-0000-4000-8000-000000000903";
    static final String CERTIFICATE = "00000000-0000-4000-8000-000000000904";
    static final String PROFILE = "00000000-0000-4000-8000-000000000905";
    static final String AUTHORITY = "00000000-0000-4000-8000-000000000906";
    static final String BASE = "/v1/entities/" + ENTITY;
    static final String PLACE = BASE + "/locations/" + LOCATION;
    static final String SCHEMA = "[{\"name\":\"path\",\"type\":\"data\",\"contentType\":\"string\",\"properties\":{\"required\":true},\"content\":[{\"reference\":\"default\",\"data\":\"/synthetic\"}]}]";
    private EntityTool entity;
    private LocationTool location;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        EntityLocationService service = new EntityLocationServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()));
        entity = new EntityTool(service);
        location = new LocationTool(service);
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR))
                .willReturn(okJson(
                        "{\"functionGroups\":[{\"functionGroupCode\":\"entityProvider\",\"kinds\":[\"STORE\"]}]}")));
        stubFor(get(urlEqualTo("/v1/connectors/" + CONNECTOR + "/attributes/entityProvider/STORE"))
                .willReturn(okJson(SCHEMA)));
        stubFor(get(urlEqualTo(BASE + "/attributes/location")).willReturn(okJson(SCHEMA)));
        for (String resource : new String[]{"entities", "locations", "certificates"}) {
            stubFor(get(urlEqualTo("/v1/attributes/custom/resources/" + resource)).willReturn(okJson("[]")));
        }
        stubFor(get(urlEqualTo(BASE))
                .willReturn(okJson("{\"uuid\":\"" + ENTITY + "\",\"name\":\"entity\",\"connectorUuid\":\"" + CONNECTOR
                        + "\",\"kind\":\"STORE\",\"attributes\":[{\"name\":\"path\",\"content\":[{\"data\":\"/existing\"}]}],\"customAttributes\":[]}")));
        stubFor(get(urlEqualTo(PLACE))
                .willReturn(okJson("{\"uuid\":\"" + LOCATION
                        + "\",\"name\":\"location\",\"description\":\"old\",\"enabled\":true,\"attributes\":[{\"name\":\"path\",\"content\":[{\"data\":\"/existing\"}]}],\"customAttributes\":[],\"metadata\":[{\"data\":\"SENTINEL_SECRET\"}],\"certificates\":[{\"certificateUuid\":\""
                        + CERTIFICATE + "\",\"state\":\"present\",\"keyData\":\"SENTINEL_SECRET\"}]}")));
    }

    @Test
    void entitySchemasCreateUpdateAndDeleteUseFixedProviderRoute() {
        assertThat(entity.getEntityAttributes(CONNECTOR, "STORE", null)).contains("path");
        assertThat(entity.getEntity(ENTITY)).contains("entity").doesNotContain("/existing");
        stubFor(post(urlEqualTo("/v1/entities"))
                .willReturn(okJson("{\"uuid\":\"" + ENTITY + "\",\"name\":\"created\"}")));
        stubFor(put(urlEqualTo(BASE)).willReturn(okJson("{\"uuid\":\"" + ENTITY + "\"}")));
        assertThat(entity
                .createEntity("{\"name\":\"created\",\"connectorUuid\":\"" + CONNECTOR + "\",\"kind\":\"STORE\"}"))
                .contains(ENTITY);
        verify(postRequestedFor(urlEqualTo("/v1/entities"))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].reference", equalTo("default"))));
        assertThat(entity.updateEntity(ENTITY, "{}")).contains(ENTITY);
        verify(putRequestedFor(urlEqualTo(BASE))
                .withRequestBody(equalToJson(
                        "{\"attributes\":[{\"name\":\"path\",\"version\":\"v2\",\"contentType\":\"string\",\"content\":[{\"data\":\"/existing\"}]}],\"customAttributes\":[]}")));
        stubFor(delete(urlEqualTo(BASE)).willReturn(noContent()));
        assertThat(entity.deleteEntity(ENTITY)).contains("Deleted");
    }

    @Test
    void entityUpdatesRejectImmutableFieldsBeforeAnyRequest() {
        for (String field : new String[]{"name", "connectorUuid", "kind"}) {
            assertThat(entity.updateEntity(ENTITY, "{\"" + field + "\":\"changed\"}")).contains(field);
        }
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void locationCrudPreservesReadableDefaultsAndProtectsCredentials() {
        assertThat(location.getLocation(ENTITY, LOCATION))
                .contains(LOCATION)
                .doesNotContain("SENTINEL_SECRET", "/existing");
        assertThat(location.getLocationAttributes(ENTITY, null)).contains("path");
        stubFor(post(urlEqualTo(BASE + "/locations")).willReturn(okJson("{\"uuid\":\"" + LOCATION + "\"}")));
        stubFor(put(urlEqualTo(PLACE)).willReturn(okJson("{\"uuid\":\"" + LOCATION + "\"}")));
        assertThat(location.createLocation(ENTITY, "{\"name\":\"new\",\"enabled\":true}")).contains(LOCATION);
        assertThat(location.updateLocation(ENTITY, LOCATION, "{\"description\":\"updated\"}")).contains(LOCATION);
        verify(putRequestedFor(urlEqualTo(PLACE))
                .withRequestBody(equalToJson(
                        "{\"description\":\"updated\",\"enabled\":true,\"attributes\":[{\"name\":\"path\",\"version\":\"v2\",\"contentType\":\"string\",\"content\":[{\"data\":\"/existing\"}]}],\"customAttributes\":[]}")));
        stubFor(get(urlEqualTo(BASE + "/attributes/location"))
                .willReturn(okJson(
                        "[{\"name\":\"path\",\"type\":\"data\",\"contentType\":\"secret\",\"properties\":{}}]")));
        assertThat(location.updateLocation(ENTITY, LOCATION, "{\"description\":\"again\"}")).contains("fresh");
        verify(1, putRequestedFor(urlEqualTo(PLACE)));
    }

    @Test
    void locationCallbacksSearchEnableAndValidationUseRealRoutes() {
        stubFor(post(urlEqualTo("/v1/locations"))
                .willReturn(okJson("{\"locations\":[{\"uuid\":\"" + LOCATION + "\",\"name\":\"location\"}]}")));
        stubFor(get(urlEqualTo("/v1/locations/search"))
                .willReturn(okJson("[{\"name\":\"name\",\"fields\":[{\"fieldIdentifier\":\"name\"}]}]")));
        assertThat(location.searchLocations(null, 10, 1)).contains("location");
        assertThat(location.getLocationSearchableFields()).contains("name");
        stubFor(post(urlEqualTo(BASE + "/attributes/location/validate")).willReturn(noContent()));
        assertThat(location.validateLocationAttributes(ENTITY, "{}")).contains("validated");
        verify(postRequestedFor(urlEqualTo(BASE + "/attributes/location/validate"))
                .withRequestBody(matchingJsonPath("$[0].content[0].data", equalTo("/synthetic"))));
        stubFor(patch(urlEqualTo(PLACE + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo(PLACE + "/disable")).willReturn(noContent()));
        stubFor(delete(urlEqualTo(PLACE)).willReturn(noContent()));
        assertThat(location.enableLocation(ENTITY, LOCATION)).contains("accepted");
        assertThat(location.disableLocation(ENTITY, LOCATION)).contains("accepted");
        assertThat(location.deleteLocation(ENTITY, LOCATION)).contains("Deleted");
    }

    @Test
    void deploymentPushRemoveRenewAndSyncReportProviderEffectsWithoutMaterial() {
        stubFor(get(urlEqualTo(PLACE + "/attributes/push")).willReturn(okJson(SCHEMA)));
        stubFor(get(urlEqualTo(PLACE + "/attributes/issue")).willReturn(okJson(SCHEMA)));
        stubFor(put(urlEqualTo(PLACE + "/certificates/" + CERTIFICATE))
                .willReturn(okJson("{\"uuid\":\"" + LOCATION + "\",\"keyData\":\"SENTINEL_SECRET\"}")));
        stubFor(patch(urlEqualTo(PLACE + "/certificates/" + CERTIFICATE))
                .willReturn(okJson("{\"uuid\":\"" + LOCATION + "\"}")));
        stubFor(delete(urlEqualTo(PLACE + "/certificates/" + CERTIFICATE)).willReturn(noContent()));
        stubFor(put(urlEqualTo(PLACE + "/sync")).willReturn(okJson("{\"uuid\":\"" + LOCATION + "\"}")));
        assertThat(location.getLocationPushAttributes(ENTITY, LOCATION, null)).contains("path");
        assertThat(location.getLocationCsrAttributes(ENTITY, LOCATION, null)).contains("path");
        assertThat(location.pushCertificateToLocation(ENTITY, LOCATION, CERTIFICATE, "{}"))
                .contains("accepted", "provider")
                .doesNotContain("SENTINEL_SECRET");
        verify(putRequestedFor(urlEqualTo(PLACE + "/certificates/" + CERTIFICATE))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].reference", equalTo("default"))));
        assertThat(location.renewCertificateInLocation(ENTITY, LOCATION, CERTIFICATE)).contains("accepted");
        assertThat(location.removeCertificateFromLocation(ENTITY, LOCATION, CERTIFICATE)).contains("provider");
        assertThat(location.syncLocation(ENTITY, LOCATION)).contains("accepted");
    }

    @Test
    void issueLocationCertificateBuildsBothSchemasBeforeMutating() {
        stubFor(get(urlEqualTo(PLACE + "/attributes/issue")).willReturn(okJson(SCHEMA)));
        stubFor(get(urlEqualTo("/v1/raProfiles/" + PROFILE))
                .willReturn(okJson("{\"authorityInstanceUuid\":\"" + AUTHORITY + "\"}")));
        stubFor(get(urlEqualTo("/v1/authorities/" + AUTHORITY + "/raProfiles/" + PROFILE + "/attributes/issue"))
                .willReturn(okJson("[]")));
        stubFor(post(urlEqualTo(PLACE + "/certificates"))
                .willReturn(okJson("{\"uuid\":\"" + LOCATION + "\",\"state\":\"pending\"}")));
        assertThat(location.issueCertificateToLocation(ENTITY, LOCATION, "{\"raProfileUuid\":\"" + PROFILE + "\"}"))
                .contains("accepted", "state: pending");
        verify(postRequestedFor(urlEqualTo(PLACE + "/certificates"))
                .withRequestBody(matchingJsonPath("$.csrAttributes[0].content[0].reference", equalTo("default")))
                .withRequestBody(matchingJsonPath("$.issueAttributes", equalToJson("[]"))));
        assertThat(entity.createEntity("{\"name\":\"x\",\"connectorUuid\":\"" + CONNECTOR + "\",\"kind\":\"OTHER\"}"))
                .contains("kind");
        verify(0, postRequestedFor(urlEqualTo("/v1/entities")));
    }

    @Test
    void locationSchemaCallbacksUseEntityAsTheParentScope() {
        stubFor(get(urlEqualTo(BASE + "/attributes/location"))
                .willReturn(
                        okJson("[{\"name\":\"group\",\"type\":\"group\",\"attributeCallback\":{\"dependsOn\":[]}}]")));
        stubFor(post(urlEqualTo("/v1/locations/" + ENTITY + "/callback")).willReturn(okJson(SCHEMA)));
        assertThat(location.getLocationAttributes(ENTITY, null)).contains("path");
        verify(postRequestedFor(urlEqualTo("/v1/locations/" + ENTITY + "/callback")));
    }

}
