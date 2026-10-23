package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.SecretOperationsTool;
import com.otilm.mcp.tool.ToolSpecifications;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class SecretConfigurationTest {
    private static final String SECRET = "80be0935-81a8-4e55-87dd-c889ddda712c";
    private static final String VAULT = VaultConfigurationTest.VAULT;
    private static final String PROFILE = VaultConfigurationTest.PROFILE;
    private static final String TARGET = "99617dba-e985-4d46-9bc8-da66bc2ae478";
    private SecretOperationsTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new SecretOperationsTool(new SecretOperationsServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build())));
        stubFor(get(urlEqualTo("/v1/vaults/" + VAULT))
                .willReturn(okJson(VaultConfigurationTest.detail("Vault", VAULT))));
        stubFor(get(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE))
                .willReturn(okJson(VaultConfigurationTest.detail("Profile", PROFILE))));
        stubFor(get(urlEqualTo("/v1/secrets/" + SECRET)).willReturn(okJson(secret())));
        stubFor(get(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + TARGET))
                .willReturn(okJson(VaultConfigurationTest.detail("Target", TARGET))));
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/secrets")).willReturn(okJson("[]")));
        for (String type : new String[]{
                "generic",
                "basicAuth",
                "apiKey",
                "jwtToken",
                "privateKey",
                "secretKey",
                "keyStore",
                "keyValue"}) {
            stubFor(get(urlEqualTo(
                    "/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE + "/secrets/" + type + "/attributes"))
                    .willReturn(okJson(VaultConfigurationTest.schema())));
            stubFor(get(
                    urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + TARGET + "/secrets/" + type + "/attributes"))
                    .willReturn(okJson(VaultConfigurationTest.schema())));
        }
    }

    @Test
    void createsSecretAndNewVersionWithActualContentWireDataButOnlyMetadataInResult() {
        String path = "/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE + "/secrets";
        stubFor(post(urlEqualTo(path)).willReturn(okJson(secret())));
        stubFor(put(urlEqualTo("/v1/secrets/" + SECRET)).willReturn(okJson(secret())));
        assertThat(tool.createSecret(VAULT, PROFILE, """
                {"name":"Secret","description":"demo","secret":{"type":"generic","content":"FRESH_SENTINEL"},
                "attributes":{"region":"eu","password":{"secret":"fresh-provider-value"}}}
                """))
                .contains(SECRET, "accepted", "pending")
                .doesNotContain("FRESH_SENTINEL", "PROTECTED_SENTINEL", "fresh-provider-value");
        verify(postRequestedFor(urlEqualTo(path))
                .withRequestBody(matchingJsonPath("$.secret.content", equalTo("FRESH_SENTINEL")))
                .withRequestBody(matchingJsonPath("$.attributes[0].version", equalTo("v2"))));
        assertThat(tool
                .updateSecret(SECRET, VAULT,
                        """
                                {"secret":{"type":"generic","content":"NEW_SENTINEL"},"attributes":{"password":{"secret":"fresh-provider-value"}}}
                                """))
                .contains(SECRET, "accepted", "pending")
                .doesNotContain("NEW_SENTINEL", "PROTECTED_SENTINEL");
        verify(putRequestedFor(urlEqualTo("/v1/secrets/" + SECRET))
                .withRequestBody(matchingJsonPath("$.description", equalTo("existing")))
                .withRequestBody(matchingJsonPath("$.attributes[0].content[0].data", equalTo("eu")))
                .withRequestBody(matchingJsonPath("$.secret.content", equalTo("NEW_SENTINEL"))));
    }

    @Test
    void supportsEveryPublishedSecretContentShapeWithoutWriteOnlyDtoSerializationLoss() {
        String path = "/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE + "/secrets";
        stubFor(post(urlEqualTo(path)).willReturn(okJson(secret())));
        String[] contents = {
                "{\"type\":\"basicAuth\",\"username\":\"user\",\"password\":\"FRESH_SENTINEL\"}",
                "{\"type\":\"apiKey\",\"content\":\"FRESH_SENTINEL\"}",
                "{\"type\":\"jwtToken\",\"content\":\"FRESH_SENTINEL\"}",
                "{\"type\":\"privateKey\",\"content\":\"cHJpdmF0ZQ==\"}",
                "{\"type\":\"secretKey\",\"content\":\"c2VjcmV0\"}",
                "{\"type\":\"keyStore\",\"keyStoreType\":\"PKCS12\",\"content\":\"c3RvcmU=\",\"password\":\"FRESH_SENTINEL\"}",
                "{\"type\":\"keyValue\",\"content\":{\"token\":\"FRESH_SENTINEL\"}}"};
        for (String content : contents) {
            assertThat(tool
                    .createSecret(VAULT, PROFILE,
                            "{\"name\":\"Secret\",\"secret\":" + content
                                    + ",\"attributes\":{\"region\":\"eu\",\"password\":{\"secret\":\"fresh\"}}}"))
                    .contains("accepted")
                    .doesNotContain("FRESH_SENTINEL");
            verify(postRequestedFor(urlEqualTo(path))
                    .withRequestBody(equalToJson("{\"secret\":" + content + "}", true, true)));
        }
    }

    @Test
    void metadataUpdatesDoNotNeedProviderCredentialsAndKeepContentOmitted() {
        stubFor(put(urlEqualTo("/v1/secrets/" + SECRET)).willReturn(okJson(secret())));
        assertThat(tool.updateSecret(SECRET, null, "{\"description\":\"new description\"}"))
                .contains(SECRET)
                .doesNotContain("PROTECTED_SENTINEL");
        verify(putRequestedFor(urlEqualTo("/v1/secrets/" + SECRET))
                .withRequestBody(equalToJson(
                        "{\"description\":\"new description\",\"attributes\":[],\"customAttributes\":[]}")));
    }

    @Test
    void appliesGroupsOwnerAndSourceAsSeparateTargetedRequests() {
        stubFor(patch(urlEqualTo("/v1/secrets/" + SECRET)).willReturn(noContent()));
        assertThat(tool.setSecretGroups(SECRET, "[\"" + PROFILE + "\"]")).contains(SECRET);
        assertThat(tool.setSecretOwner(SECRET, "")).contains(SECRET);
        assertThat(tool
                .changeSecretSourceVaultProfile(SECRET, VAULT, TARGET,
                        "{\"region\":\"eu\",\"password\":{\"secret\":\"fresh\"}}"))
                .contains("accepted", "pending");
        verify(patchRequestedFor(urlEqualTo("/v1/secrets/" + SECRET))
                .withRequestBody(equalToJson("{\"groupUuids\":[\"" + PROFILE + "\"]}")));
        verify(patchRequestedFor(urlEqualTo("/v1/secrets/" + SECRET))
                .withRequestBody(equalToJson("{\"ownerUuid\":\"\"}")));
        verify(patchRequestedFor(urlEqualTo("/v1/secrets/" + SECRET))
                .withRequestBody(matchingJsonPath("$.sourceVaultProfileUuid", equalTo(TARGET)))
                .withRequestBody(matchingJsonPath("$.secretAttributes[0].version", equalTo("v2"))));
    }

    @Test
    void synchronizationAndDestructiveContentRemovalAreExplicitlyDistinct() {
        String path = "/v1/secrets/" + SECRET + "/syncVaultProfiles/" + TARGET;
        stubFor(patch(urlEqualTo(path)).willReturn(noContent()));
        stubFor(delete(urlEqualTo(path + "?deleteInVault=false")).willReturn(noContent()));
        stubFor(delete(urlEqualTo(path + "?deleteInVault=true")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/secrets/" + SECRET + "?deleteInVaults=false")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/secrets/" + SECRET + "?deleteInVaults=true")).willReturn(noContent()));
        assertThat(tool
                .addSecretSyncVaultProfile(SECRET, VAULT, TARGET,
                        "{\"region\":\"eu\",\"password\":{\"secret\":\"fresh\"}}"))
                .contains("accepted", "pending");
        assertThat(tool.removeSecretSyncVaultProfile(SECRET, TARGET)).contains("retained");
        assertThat(tool.removeSecretSyncVaultProfileContent(SECRET, TARGET)).contains("provider");
        assertThat(tool.deleteSecret(SECRET)).contains("retained");
        assertThat(tool.deleteSecretFromVaults(SECRET)).contains("provider");
        verify(deleteRequestedFor(urlEqualTo(path + "?deleteInVault=false")));
        verify(deleteRequestedFor(urlEqualTo(path + "?deleteInVault=true")));
        verify(deleteRequestedFor(urlEqualTo("/v1/secrets/" + SECRET + "?deleteInVaults=false")));
        verify(deleteRequestedFor(urlEqualTo("/v1/secrets/" + SECRET + "?deleteInVaults=true")));
    }

    @Test
    void secretContentIsExplicitSensitiveReadAndStateRoutesUsePatch() {
        stubFor(get(urlEqualTo("/v1/secrets/" + SECRET + "/content"))
                .willReturn(okJson("{\"type\":\"generic\",\"content\":\"SENSITIVE_OUTPUT\"}")));
        assertThat(tool.getSecretContent(SECRET)).contains("SENSITIVE_OUTPUT");
        for (AccessLevel level : new AccessLevel[]{AccessLevel.READ_ONLY, AccessLevel.READ_WRITE}) {
            assertThat(ToolSpecifications.allowed(level, List.of(tool)))
                    .extracting(spec -> spec.tool().name())
                    .doesNotContain("get_secret_content", "delete_secret_from_vaults",
                            "remove_secret_sync_vault_profile_content");
        }
        assertThat(ToolSpecifications.allowed(AccessLevel.FULL, List.of(tool)))
                .extracting(spec -> spec.tool().name())
                .contains("get_secret_content", "delete_secret_from_vaults");
        stubFor(patch(urlEqualTo("/v1/secrets/" + SECRET + "/enable")).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/secrets/" + SECRET + "/disable")).willReturn(noContent()));
        assertThat(tool.enableSecret(SECRET)).contains(SECRET);
        assertThat(tool.disableSecret(SECRET)).contains(SECRET);
        verify(patchRequestedFor(urlEqualTo("/v1/secrets/" + SECRET + "/enable")));
        verify(patchRequestedFor(urlEqualTo("/v1/secrets/" + SECRET + "/disable")));
    }

    @Test
    void rejectsMalformedContentWrongTypesAndUnsafeReplacementBeforeMutation() {
        for (String config : new String[]{
                "{PROTECTED_SENTINEL",
                "{\"name\":\"x\",\"secret\":{\"type\":\"unknown\"}}",
                "{\"name\":\"x\",\"secret\":{\"type\":\"basicAuth\",\"username\":\"x\"}}",
                "{\"name\":\"x\",\"secret\":{\"type\":\"privateKey\",\"content\":\"not-base64!\"}}"}) {
            assertThat(tool.createSecret(VAULT, PROFILE, config)).doesNotContain("accepted", "PROTECTED_SENTINEL");
        }
        assertThat(tool.updateSecret(SECRET, VAULT, "{\"secret\":{\"type\":\"generic\",\"content\":\"new\"}}"))
                .containsIgnoringCase("fresh");
        assertThat(tool.setSecretGroups(SECRET, "[\"PROTECTED_SENTINEL\"]")).doesNotContain("PROTECTED_SENTINEL");
        verify(0, postRequestedFor(urlEqualTo("/v1/vaults/" + VAULT + "/vaultProfiles/" + PROFILE + "/secrets")));
        verify(0, putRequestedFor(anyUrl()));
    }

    @Test
    void refusesSourceAsSyncProfileAndApprovalBlockedContentChangesBeforeWrites() {
        String sync = "/v1/secrets/" + SECRET + "/syncVaultProfiles/" + PROFILE;
        stubFor(patch(urlEqualTo(sync)).willReturn(noContent()));
        assertThat(tool
                .addSecretSyncVaultProfile(SECRET, VAULT, PROFILE,
                        "{\"region\":\"eu\",\"password\":{\"secret\":\"fresh\"}}"))
                .containsIgnoringCase("source");
        verify(0, patchRequestedFor(urlEqualTo(sync)));
        stubFor(get(urlEqualTo("/v1/secrets/" + SECRET))
                .willReturn(okJson(secret().replace("\"state\":\"inactive\"", "\"state\":\"pendingApproval\""))));
        stubFor(put(urlEqualTo("/v1/secrets/" + SECRET)).willReturn(okJson(secret())));
        assertThat(tool.updateSecret(SECRET, VAULT, """
                {"secret":{"type":"generic","content":"new"},"attributes":{"password":{"secret":"fresh"}}}
                """)).doesNotContain("accepted");
        verify(0, putRequestedFor(anyUrl()));
    }

    private static String secret() {
        return """
                {"uuid":"80be0935-81a8-4e55-87dd-c889ddda712c","name":"Secret","description":"existing",
                "type":"generic","state":"inactive","version":1,"enabled":true,
                "sourceVaultProfile":{"uuid":"75124e12-4efa-4ad3-8aa7-ae0337a92978","name":"Profile"},
                "attributes":[{"name":"region","content":[{"data":"eu"}]},{"name":"password","content":[{"data":{"secret":"PROTECTED_SENTINEL"}}]}],
                "customAttributes":[],"secret":{"type":"generic","content":"PROTECTED_SENTINEL"}}
                """;
    }
}
