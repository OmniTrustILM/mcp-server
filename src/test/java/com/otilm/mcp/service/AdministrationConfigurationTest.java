package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
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
class AdministrationConfigurationTest {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER = "22222222-2222-4222-8222-222222222222";
    private AdministrationService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wiremock) {
        service = new AdministrationServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wiremock.getHttpBaseUrl()).build()));
        stubFor(get(urlPathMatching("/v1/attributes/custom/resources/.*")).willReturn(okJson("[]")));
    }

    @Test
    void updatesUserWithoutClearingReadableUnrelatedFieldsOrGroups() {
        stubFor(get(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("""
                {"uuid":"%s","username":"reviewer","firstName":"Alice","lastName":"Example","description":"Original",
                 "email":"alice@example.test","groups":[{"uuid":"%s","name":"Team"}],"customAttributes":[]}
                """.formatted(ID, OTHER))));
        stubFor(put(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        assertThat(service.updateUser(ID, "{\"description\":\"Revised\"}")).contains(ID);
        verify(putRequestedFor(urlEqualTo("/v1/users/" + ID)).withRequestBody(equalToJson("""
                {"firstName":"Alice","lastName":"Example","description":"Revised","email":"alice@example.test",
                 "groupUuids":["%s"],"customAttributes":[]}
                """.formatted(OTHER))));
    }

    @Test
    void groupMembershipPreservesOtherUserGroupsAndUserFields() {
        stubFor(get(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("""
                {"uuid":"%s","firstName":"Alice","groups":[{"uuid":"%s"}],"customAttributes":[]}
                """.formatted(ID, OTHER))));
        stubFor(put(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("{}")));
        assertThat(service.setGroupMembership(OTHER, ID, false)).contains(ID, OTHER);
        verify(putRequestedFor(urlEqualTo("/v1/users/" + ID))
                .withRequestBody(matchingJsonPath("$.groupUuids", equalToJson("[]")))
                .withRequestBody(matchingJsonPath("$.firstName", equalTo("Alice"))));
    }

    @Test
    void roleUsersReplacementRequiresExplicitIntent() {
        assertThat(service.setRoleUsers(ID, "[\"" + OTHER + "\"]", false)).contains("replace");
        verify(0, patchRequestedFor(anyUrl()));
        stubFor(patch(urlEqualTo("/v1/roles/" + ID + "/users")).willReturn(okJson("{}")));
        assertThat(service.setRoleUsers(ID, "[\"" + OTHER + "\"]", true)).contains(ID);
        verify(patchRequestedFor(urlEqualTo("/v1/roles/" + ID + "/users"))
                .withRequestBody(equalToJson("[\"" + OTHER + "\"]")));
    }

    @Test
    void resourceGrantPreservesOtherResourcesAndObjectGrants() {
        stubFor(get(urlEqualTo("/v1/roles/" + ID)).willReturn(okJson("{\"systemRole\":false}")));
        stubFor(get(urlEqualTo("/v1/auth/resources")).willReturn(okJson("""
                [{"uuid":"%s","name":"certificate","actions":[{"name":"detail"},{"name":"revoke"}]}]
                """.formatted(OTHER))));
        stubFor(get(urlEqualTo("/v1/roles/" + ID + "/permissions"))
                .willReturn(
                        okJson("""
                                {"allowAllResources":false,"resources":[{"name":"group","actions":["detail"],"allowAllActions":false,"objects":[]},
                                {"name":"certificate","actions":["detail"],"allowAllActions":false,"objects":[{"uuid":"%s","allow":["detail"],"deny":[]}]}]}
                                """
                                .formatted(OTHER))));
        stubFor(post(urlEqualTo("/v1/roles/" + ID + "/permissions")).willReturn(okJson("{}")));
        assertThat(service.setRoleResourceGrant(ID, OTHER, "{\"allowAllActions\":false,\"actions\":[\"revoke\"]}"))
                .contains(ID, OTHER);
        verify(postRequestedFor(urlEqualTo("/v1/roles/" + ID + "/permissions"))
                .withRequestBody(matchingJsonPath("$.resources[0].name", equalTo("group")))
                .withRequestBody(matchingJsonPath("$.resources[1].objects[0].uuid", equalTo(OTHER)))
                .withRequestBody(matchingJsonPath("$.resources[1].actions[0]", equalTo("revoke"))));
    }

    @Test
    void refusesSystemRoleAndUnsupportedActionsBeforeMutation() {
        stubFor(get(urlEqualTo("/v1/roles/" + ID)).willReturn(okJson("{\"systemRole\":true}")));
        assertThat(service.setRoleResourceGrant(ID, OTHER, "{\"allowAllActions\":false,\"actions\":[\"revoke\"]}"))
                .contains("System role");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void createsUserAndRoleAndReturnsMetadataOnly() {
        stubFor(post(urlEqualTo("/v1/users")).willReturn(okJson("""
                {"uuid":"%s","username":"reviewer","certificateData":"SECRET_SENTINEL"}
                """.formatted(ID))));
        assertThat(service.createUser("{\"username\":\"reviewer\",\"enabled\":true}"))
                .contains(ID, "reviewer")
                .doesNotContain("SECRET_SENTINEL");
        verify(postRequestedFor(urlEqualTo("/v1/users"))
                .withRequestBody(matchingJsonPath("$.enabled", equalTo("true"))));
        assertThat(service.createRole("{\"unknown\":true}")).contains("Unknown");
        verify(0, postRequestedFor(urlEqualTo("/v1/roles")));
    }

    @Test
    void invalidIdentifiersNeverReachPlatform() {
        assertThat(service.getUser("invalid")).contains("UUID");
        assertThat(service.setUserRole(ID, "invalid", true)).contains("UUID");
        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void objectAllowDenyGrantUsesObjectSpecificRouteAndValidatesActions() {
        stubFor(get(urlEqualTo("/v1/roles/" + ID)).willReturn(okJson("{\"systemRole\":false}")));
        stubFor(get(urlEqualTo("/v1/auth/resources"))
                .willReturn(
                        okJson("""
                                [{"uuid":"%s","name":"certificates","objectAccess":true,"actions":[{"name":"detail"},{"name":"revoke"}]}]
                                """
                                .formatted(OTHER))));
        stubFor(put(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects/" + ID))
                .willReturn(noContent()));
        assertThat(service.setRoleObjectGrant(ID, OTHER, ID, "{\"allow\":[\"detail\"],\"deny\":[\"revoke\"]}", false))
                .contains(ID, OTHER);
        verify(putRequestedFor(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects/" + ID))
                .withRequestBody(
                        equalToJson("{\"uuid\":\"" + ID + "\",\"allow\":[\"detail\"],\"deny\":[\"revoke\"]}")));
        assertThat(service.setRoleObjectGrant(ID, OTHER, ID, "{\"allow\":[\"madeUp\"],\"deny\":[]}", false))
                .contains("action");
        verify(1, putRequestedFor(anyUrl()));
    }

    @Test
    void metadataUpdateRequiresFreshProtectedCustomValues() {
        stubFor(get(urlEqualTo("/v1/groups/" + ID)).willReturn(okJson("""
                {"uuid":"%s","name":"Team","customAttributes":[{"name":"pin","version":"v2","contentType":"secret",
                 "content":[{"data":{"secret":"********","protectionLevel":"encrypted"}}]}]}
                """.formatted(ID))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/groups")).willReturn(okJson("""
                [{"name":"pin","version":2,"type":"custom","contentType":"secret","properties":{}}]
                """)));
        assertThat(service.updateGroup(ID, "{\"description\":\"Changed\"}")).contains("pin");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void groupMembershipAcceptsFreshProtectedUserValuesInSameReplacement() {
        stubFor(get(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("""
                {"uuid":"%s","groups":[],"customAttributes":[{"name":"pin","version":"v2","contentType":"secret",
                "content":[{"data":{"secret":"********","protectionLevel":"encrypted"}}]}]}
                """.formatted(ID))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/users")).willReturn(okJson("""
                [{"name":"pin","version":2,"type":"custom","contentType":"secret","properties":{}}]
                """)));
        stubFor(put(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("{}")));
        assertThat(service
                .setGroupMembership(OTHER, ID, true,
                        "{\"pin\":{\"secret\":\"fresh-value\",\"protectionLevel\":\"none\"}}"))
                .contains(ID, OTHER)
                .doesNotContain("fresh-value");
        verify(putRequestedFor(urlEqualTo("/v1/users/" + ID))
                .withRequestBody(matchingJsonPath("$.groupUuids[0]", equalTo(OTHER)))
                .withRequestBody(
                        matchingJsonPath("$.customAttributes[0].content[0].data.secret", equalTo("fresh-value"))));
    }

    @Test
    void groupNameChangeIsRefusedRatherThanSilentlyIgnoredByPlatform() {
        stubFor(get(urlEqualTo("/v1/groups/" + ID))
                .willReturn(okJson("{\"name\":\"Original\",\"customAttributes\":[]}")));
        assertThat(service.updateGroup(ID, "{\"name\":\"Renamed\"}")).contains("name", "cannot");
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void readsUserGroupRoleCatalogAndEffectivePermissionMetadata() {
        stubFor(get(urlEqualTo("/v1/users/" + ID)).willReturn(okJson("""
                {"uuid":"%s","username":"reviewer","roles":[{"name":"Auditor"}],"groups":[{"name":"Team"}],
                 "certificate":{"uuid":"%s","fingerprint":"public-fingerprint","privateKey":"SECRET_SENTINEL"},
                 "customAttributes":[{"name":"private","content":"SECRET_SENTINEL"}]}
                """.formatted(ID, OTHER))));
        stubFor(get(urlEqualTo("/v1/users/" + ID + "/roles")).willReturn(okJson("[{\"name\":\"Auditor\"}]")));
        stubFor(get(urlEqualTo("/v1/users/" + ID + "/permissions"))
                .willReturn(
                        okJson("""
                                {"allowAllResources":false,"resources":[{"name":"certificates","actions":["detail"],"objects":[{"uuid":"%s","allow":["detail"],"deny":["revoke"]}]}]}
                                """
                                .formatted(OTHER))));
        stubFor(get(urlEqualTo("/v1/groups/" + ID))
                .willReturn(okJson("{\"name\":\"Team\",\"email\":\"team@example.test\"}")));
        stubFor(get(urlEqualTo("/v1/groups/" + ID + "/users")).willReturn(okJson("[{\"name\":\"reviewer\"}]")));
        stubFor(get(urlEqualTo("/v1/roles/" + ID)).willReturn(okJson("{\"name\":\"Auditor\",\"systemRole\":false}")));
        stubFor(get(urlEqualTo("/v1/roles/" + ID + "/users")).willReturn(okJson("[{\"username\":\"reviewer\"}]")));
        stubFor(get(urlEqualTo("/v1/roles/" + ID + "/permissions"))
                .willReturn(okJson("{\"allowAllResources\":true,\"resources\":[]}")));
        stubFor(get(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER))
                .willReturn(okJson("{\"name\":\"certificates\",\"actions\":[\"detail\"]}")));
        stubFor(get(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects"))
                .willReturn(okJson("[{\"uuid\":\"" + ID + "\",\"allow\":[\"detail\"],\"deny\":[\"revoke\"]}]")));
        stubFor(get(urlEqualTo("/v1/auth/resources"))
                .willReturn(
                        okJson("""
                                [{"uuid":"%s","name":"certificates","objectAccess":true,"actions":[{"name":"detail","displayName":"Detail"}]}]
                                """
                                .formatted(OTHER))));
        stubFor(get(urlEqualTo("/v1/auth/resources/certificates/objects"))
                .willReturn(okJson("[{\"name\":\"Public certificate\"}]")));
        assertThat(service.getUser(ID))
                .contains("reviewer", "Auditor", "Team", "public-fingerprint")
                .doesNotContain("SECRET_SENTINEL");
        assertThat(service.getUserRoles(ID)).contains("Auditor");
        assertThat(service.getUserPermissions(ID)).contains("detail", "revoke", OTHER);
        assertThat(service.getGroup(ID)).contains("Team", "team@example.test");
        assertThat(service.getGroupUsers(ID)).contains("reviewer");
        assertThat(service.getRole(ID)).contains("Auditor");
        assertThat(service.getRoleUsers(ID)).contains("reviewer");
        assertThat(service.getRolePermissions(ID)).contains("true");
        assertThat(service.getRoleResourcePermissions(ID, OTHER)).contains("certificates", "detail", "revoke");
        assertThat(service.getRoleObjectPermissions(ID, OTHER)).contains("detail", "revoke");
        assertThat(service.listPermissionResources()).contains(OTHER, "Detail", "certificates");
        assertThat(service.listPermissionResourceObjects(OTHER)).contains("Public certificate");
    }

    @Test
    void userLifecycleAndTargetedRoleMembershipUseSupportedRoutes() {
        stubFor(patch(urlEqualTo("/v1/users/" + ID + "/enable")).willReturn(okJson("{\"enabled\":true}")));
        stubFor(patch(urlEqualTo("/v1/users/" + ID + "/disable")).willReturn(okJson("{\"enabled\":false}")));
        stubFor(put(urlEqualTo("/v1/users/" + ID + "/roles/" + OTHER)).willReturn(okJson("{}")));
        stubFor(delete(urlEqualTo("/v1/users/" + ID + "/roles/" + OTHER)).willReturn(okJson("{}")));
        stubFor(patch(urlEqualTo("/v1/users/" + ID + "/roles")).willReturn(okJson("{}")));
        stubFor(delete(urlEqualTo("/v1/users/" + ID)).willReturn(noContent()));
        assertThat(service.setUserEnabled(ID, true)).contains("true");
        assertThat(service.setUserEnabled(ID, false)).contains("false");
        assertThat(service.setUserRole(ID, OTHER, true)).contains("added", ID, OTHER);
        assertThat(service.setUserRole(ID, OTHER, false)).contains("removed", ID, OTHER);
        assertThat(service.setUserRoles(ID, "[]", false)).contains("replace=true");
        assertThat(service.setUserRoles(ID, "[]", true)).contains(ID);
        verify(patchRequestedFor(urlEqualTo("/v1/users/" + ID + "/roles")).withRequestBody(equalToJson("[]")));
        assertThat(service.deleteUser(ID)).contains("Deleted", ID);
        verify(deleteRequestedFor(urlEqualTo("/v1/users/" + ID)));
    }

    @Test
    void roleAndGroupMetadataLifecycleRetainsEmailAndAttributes() {
        stubFor(post(urlEqualTo("/v1/roles")).willReturn(okJson("{\"uuid\":\"" + ID + "\",\"name\":\"Auditor\"}")));
        stubFor(get(urlEqualTo("/v1/roles/" + ID))
                .willReturn(okJson(
                        "{\"name\":\"Auditor\",\"description\":\"Original\",\"email\":\"team@example.test\",\"customAttributes\":[]}")));
        stubFor(put(urlEqualTo("/v1/roles/" + ID)).willReturn(okJson("{}")));
        stubFor(get(urlEqualTo("/v1/groups/" + ID))
                .willReturn(okJson(
                        "{\"name\":\"Team\",\"description\":\"Original\",\"email\":\"team@example.test\",\"customAttributes\":[]}")));
        stubFor(put(urlEqualTo("/v1/groups/" + ID)).willReturn(okJson("{}")));
        stubFor(delete(urlEqualTo("/v1/groups/" + ID)).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/roles/" + ID)).willReturn(noContent()));
        assertThat(service.createRole("{\"name\":\"Auditor\"}")).contains(ID, "Auditor");
        assertThat(service.updateRole(ID, "{\"description\":\"Revised\"}")).contains(ID);
        assertThat(service.updateGroup(ID, "{\"description\":\"Revised\"}")).contains(ID);
        verify(putRequestedFor(urlEqualTo("/v1/groups/" + ID))
                .withRequestBody(equalToJson(
                        "{\"name\":\"Team\",\"description\":\"Revised\",\"email\":\"team@example.test\",\"customAttributes\":[]}")));
        verify(putRequestedFor(urlEqualTo("/v1/roles/" + ID))
                .withRequestBody(equalToJson(
                        "{\"name\":\"Auditor\",\"description\":\"Revised\",\"email\":\"team@example.test\",\"customAttributes\":[]}")));
        assertThat(service.deleteGroup(ID)).contains("Deleted", ID);
        assertThat(service.deleteRole(ID)).contains("Deleted", ID);
    }

    @Test
    void explicitPermissionReplacementAndObjectAddRemoveUseExactPayloads() {
        stubFor(get(urlEqualTo("/v1/roles/" + ID)).willReturn(okJson("{\"systemRole\":false}")));
        stubFor(get(urlEqualTo("/v1/auth/resources"))
                .willReturn(
                        okJson("""
                                [{"uuid":"%s","name":"certificates","objectAccess":true,"actions":[{"name":"detail"},{"name":"revoke"}]}]
                                """
                                .formatted(OTHER))));
        stubFor(post(urlEqualTo("/v1/roles/" + ID + "/permissions")).willReturn(okJson("{}")));
        stubFor(post(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects/" + ID))
                .willReturn(noContent()));
        String configuration = "{\"allowAllResources\":false,\"resources\":[{\"name\":\"certificates\",\"allowAllActions\":false,\"actions\":[\"detail\"],\"objects\":[{\"uuid\":\""
                + ID + "\",\"allow\":[\"detail\"],\"deny\":[]}]}]}";
        assertThat(service.replaceRolePermissions(ID, configuration, false)).contains("replace=true");
        assertThat(service.replaceRolePermissions(ID, configuration, true)).contains(ID);
        verify(postRequestedFor(urlEqualTo("/v1/roles/" + ID + "/permissions"))
                .withRequestBody(equalToJson(configuration)));
        assertThat(service.setRoleObjectGrant(ID, OTHER, ID, "{\"allow\":[\"detail\"],\"deny\":[\"revoke\"]}", true))
                .contains("Added", ID);
        verify(postRequestedFor(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects"))
                .withRequestBody(
                        equalToJson("[{\"uuid\":\"" + ID + "\",\"allow\":[\"detail\"],\"deny\":[\"revoke\"]}]")));
        assertThat(service.removeRoleObjectGrant(ID, OTHER, ID)).contains("Removed", ID);
        verify(deleteRequestedFor(urlEqualTo("/v1/roles/" + ID + "/permissions/" + OTHER + "/objects/" + ID)));
    }

    @Test
    void certificateImportWritesPublicCertificateAndTypedCustomValues() {
        stubFor(post(urlEqualTo("/v1/users")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/certificates")).willReturn(okJson("""
                [{"name":"purpose","version":2,"type":"custom","contentType":"string","properties":{}}]
                """)));
        assertThat(service
                .createUser(
                        "{\"username\":\"reviewer\",\"certificateData\":\"synthetic-public-certificate\",\"certificateCustomAttributes\":{\"purpose\":\"login\"}}"))
                .contains(ID)
                .doesNotContain("synthetic-public-certificate");
        verify(postRequestedFor(urlEqualTo("/v1/users"))
                .withRequestBody(matchingJsonPath("$.certificateData", equalTo("synthetic-public-certificate")))
                .withRequestBody(
                        matchingJsonPath("$.certificateCustomAttributes[0].content[0].data", equalTo("login"))));
    }

}
