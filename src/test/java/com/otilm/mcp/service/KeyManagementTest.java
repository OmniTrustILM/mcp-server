package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.KeyManagementTool;
import com.otilm.mcp.tool.ToolSpecifications;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
class KeyManagementTest {
    static final String TOKEN = "00000000-0000-4000-8000-000000000801";
    static final String PROFILE = "00000000-0000-4000-8000-000000000802";
    static final String KEY = "00000000-0000-4000-8000-000000000803";
    static final String ITEM = "00000000-0000-4000-8000-000000000804";
    static final String PREFIX = "/v1/tokens/" + TOKEN + "/tokenProfiles/" + PROFILE;
    static final String KEY_PATH = "/v1/keys/" + KEY;
    static final String ITEM_PATH = KEY_PATH + "/items/" + ITEM;
    static final String SCHEMA = "[{\"name\":\"algorithm\",\"type\":\"data\",\"version\":2,\"contentType\":\"string\",\"properties\":{\"list\":true,\"required\":true},\"content\":[{\"reference\":\"rsa\",\"data\":\"RSA\"}]}]";
    private KeyManagementService service;
    private KeyManagementTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        service = new KeyManagementServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm)));
        tool = new KeyManagementTool(service);
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlEqualTo("/v1/attributes/custom/resources/keys")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo(PREFIX))
                .willReturn(okJson("{\"uuid\":\"" + PROFILE
                        + "\",\"keyTransfer\":{\"importAvailable\":true,\"exportAvailable\":true,\"importableKeyTypes\":{\"keyPair\":[\"RSA\"]},\"exportableKeyTypes\":{\"keyPair\":[\"RSA\"]}}}")));
    }

    @Test
    void generationRemainsBaselineAndPreservesOptionReferenceWithoutNewFields() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubFor(get(urlEqualTo(PREFIX + "/keys/keyPair/attributes")).willReturn(okJson(SCHEMA)));
        stubFor(post(urlEqualTo(PREFIX + "/keys/keyPair"))
                .willReturn(okJson("{\"uuid\":\"" + KEY
                        + "\",\"name\":\"generated\",\"items\":[{\"keyData\":\"SENTINEL_KEY\"}]}")));
        assertThat(tool
                .generateKey(TOKEN, PROFILE, "keyPair",
                        "{\"name\":\"generated\",\"attributes\":{\"algorithm\":\"RSA\"},\"enabled\":true}"))
                .contains(KEY, "generated")
                .doesNotContain("SENTINEL_KEY");
        verify(postRequestedFor(urlEqualTo(PREFIX + "/keys/keyPair"))
                .withRequestBody(equalToJson(
                        """
                                {"name":"generated","enabled":true,"attributes":[{"name":"algorithm","version":"v2","contentType":"string","content":[{"reference":"rsa","data":"RSA"}]}],"customAttributes":[]}
                                """)));
    }

    @Test
    void importWritesSensitiveFileAndPassphraseFieldsAndHidesReturnedMaterial() {
        stubFor(get(urlEqualTo(PREFIX + "/keys/keyPair/import/attributes")).willReturn(okJson("[]")));
        stubFor(post(urlEqualTo(PREFIX + "/keys/keyPair/import"))
                .willReturn(okJson("{\"uuid\":\"" + KEY + "\",\"name\":\"imported\",\"keyData\":\"SENTINEL_KEY\"}")));
        assertThat(tool
                .importKey(TOKEN, PROFILE, "keyPair",
                        "{\"name\":\"imported\",\"file\":\"aGVsbG8=\",\"inputPassphrase\":\"synthetic-input\",\"exportable\":true}"))
                .contains(KEY)
                .doesNotContain("SENTINEL_KEY", "synthetic-input", "aGVsbG8=");
        verify(postRequestedFor(urlEqualTo(PREFIX + "/keys/keyPair/import"))
                .withRequestBody(equalToJson(
                        """
                                {"name":"imported","file":"aGVsbG8=","inputPassphrase":"synthetic-input","exportable":true,"importAttributes":[],"customAttributes":[]}
                                """)));
    }

    @Test
    void oversizedInvalidUploadsAndBadTypesStopBeforeMutation() {
        String tooLarge = Base64.getEncoder().encodeToString(new byte[5 * 1024 * 1024 + 1]);
        assertThat(tool.importKey(TOKEN, PROFILE, "keyPair", "{\"name\":\"x\",\"file\":\"" + tooLarge + "\"}"))
                .contains("5 MiB");
        assertThat(tool.importKey(TOKEN, PROFILE, "keyPair", "{\"name\":\"x\",\"file\":\"SENTINEL_INVALID!\"}"))
                .doesNotContain("SENTINEL_INVALID")
                .containsIgnoringCase("base64");
        assertThat(tool.generateKey(TOKEN, PROFILE, "rotate", "{\"name\":\"x\"}")).contains("keyPair");
        verify(0, postRequestedFor(urlPathMatching(".*keys.*")));
    }

    @Test
    void exportRequiresNfcTwelveCodepointsAndSupportedActiveExportableItem() {
        exportable();
        assertThat(tool.exportKey(KEY, ITEM, "short", null)).contains("12");
        assertThat(tool.exportKey(KEY, ITEM, "e\u0301abcdefghijklm", null)).contains("NFC");
        assertThat(tool.exportKey(KEY, ITEM, "😀😀😀😀😀😀", null)).contains("12");
        verify(0, postRequestedFor(urlEqualTo(ITEM_PATH + "/export")));
        stubFor(get(urlEqualTo(ITEM_PATH))
                .willReturn(okJson(
                        "{\"uuid\":\"" + ITEM + "\",\"state\":\"compromised\",\"enabled\":true,\"exportable\":true}")));
        assertThat(tool.exportKey(KEY, ITEM, "synthetic-passphrase", null)).containsIgnoringCase("active");
        verify(0, postRequestedFor(urlEqualTo(ITEM_PATH + "/export")));
    }

    @Test
    void exportReturnsEncryptedPemAndFilenameWithActualPassphraseBody() {
        exportable();
        stubFor(post(urlEqualTo(ITEM_PATH + "/export"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/octet-stream")
                        .withHeader("Content-Disposition", "attachment; filename=sample.pem")
                        .withBody(
                                "-----BEGIN ENCRYPTED PRIVATE KEY-----\nc3ludGhldGlj\n-----END ENCRYPTED PRIVATE KEY-----")));
        assertThat(tool.exportKey(KEY, ITEM, "synthetic-passphrase", "{}"))
                .contains("sample.pem", "application/octet-stream", "BEGIN ENCRYPTED PRIVATE KEY")
                .doesNotContain("synthetic-passphrase");
        verify(postRequestedFor(urlEqualTo(ITEM_PATH + "/export"))
                .withRequestBody(equalToJson("{\"passphrase\":\"synthetic-passphrase\",\"exportAttributes\":[]}")));
    }

    @Test
    void lifecycleSelectsOnlyRequestedItemsAndRefusesForeignItemBeforeMutation() {
        key();
        for (String operation : List.of("enable", "disable", "destroy", "compromise")) {
            stubFor(patch(urlEqualTo(KEY_PATH + "/" + operation)).willReturn(noContent()));
        }
        stubFor(delete(urlEqualTo(KEY_PATH)).willReturn(noContent()));
        String selection = "[\"" + ITEM + "\"]";
        assertThat(tool.enableKey(KEY, selection)).contains("accepted");
        assertThat(tool.disableKey(KEY, selection)).contains("accepted");
        assertThat(tool.compromiseKey(KEY, "disclosure", selection)).contains("accepted");
        assertThat(tool.destroyKey(KEY, selection)).contains("provider");
        assertThat(tool.deleteKey(KEY, selection)).contains("provider");
        verify(patchRequestedFor(urlEqualTo(KEY_PATH + "/enable")).withRequestBody(equalToJson(selection)));
        verify(patchRequestedFor(urlEqualTo(KEY_PATH + "/compromise"))
                .withRequestBody(equalToJson("{\"reason\":\"disclosure\",\"uuids\":" + selection + "}")));
        verify(deleteRequestedFor(urlEqualTo(KEY_PATH)).withRequestBody(equalToJson(selection)));
        assertThat(tool.destroyKey(KEY, "[\"" + PROFILE + "\"]")).containsIgnoringCase("belong");
        verify(1, patchRequestedFor(urlEqualTo(KEY_PATH + "/destroy")));
    }

    @Test
    void metadataUsageHistoryAndRenameUseReleasedRoutesWithoutMaterial() {
        key();
        stubFor(get(urlEqualTo(ITEM_PATH))
                .willReturn(okJson("{\"uuid\":\"" + ITEM
                        + "\",\"keyData\":\"SENTINEL_KEY\",\"keyAlgorithm\":\"RSA\",\"enabled\":true}")));
        stubFor(get(urlEqualTo(ITEM_PATH + "/history"))
                .willReturn(okJson("[{\"event\":\"created\",\"status\":\"success\",\"message\":\"SENTINEL_KEY\"}]")));
        stubFor(get(urlEqualTo("/v1/keys/pairs?tokenProfileUuid=" + PROFILE))
                .willReturn(okJson("[{\"uuid\":\"" + KEY + "\",\"name\":\"pair\",\"keyData\":\"SENTINEL_KEY\"}]")));
        assertThat(tool.getKeyItem(KEY, ITEM)).contains("RSA").doesNotContain("SENTINEL_KEY");
        assertThat(tool.getKeyHistory(KEY, ITEM)).contains("created").doesNotContain("SENTINEL_KEY");
        assertThat(tool.listKeyPairs(PROFILE)).contains("pair").doesNotContain("SENTINEL_KEY");
        stubFor(patch(urlEqualTo(ITEM_PATH)).willReturn(noContent()));
        stubFor(put(urlEqualTo(KEY_PATH + "/usages")).willReturn(noContent()));
        stubFor(put(urlEqualTo(KEY_PATH)).willReturn(okJson("{\"uuid\":\"" + KEY + "\"}")));
        assertThat(tool.renameKeyItem(KEY, ITEM, "renamed")).contains("accepted");
        assertThat(tool.updateKeyUsages(KEY, "[\"sign\"]", "[\"" + ITEM + "\"]")).contains("accepted");
        assertThat(tool.updateKey(KEY, "{\"description\":\"updated\"}")).contains(KEY);
        verify(putRequestedFor(urlEqualTo(KEY_PATH)).withRequestBody(matchingJsonPath("$.name", equalTo("key"))));
        verify(putRequestedFor(urlEqualTo(KEY_PATH + "/usages"))
                .withRequestBody(equalToJson("{\"usage\":[\"sign\"],\"uuids\":[\"" + ITEM + "\"]}")));
    }

    @Test
    void transferVersionGatesAndToolAccessAreIndependentOfReadHint() {
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(tool.importKey(TOKEN, PROFILE, "keyPair", "{}")).contains("2.20");
        assertThat(tool.exportKey(KEY, ITEM, "synthetic-passphrase", null)).contains("2.20");
        assertThat(tool.disableKeyExport(KEY, ITEM)).contains("2.20");
        var tools = List.<Object>of(new KeyManagementTool(service));
        for (AccessLevel level : List.of(AccessLevel.READ_ONLY, AccessLevel.READ_WRITE)) {
            assertThat(ToolSpecifications.allowed(level, tools))
                    .extracting(spec -> spec.tool().name())
                    .doesNotContain("export_key", "destroy_key", "delete_key", "compromise_key", "disable_key_export");
        }
        assertThat(ToolSpecifications.allowed(AccessLevel.FULL, tools)).anySatisfy(spec -> {
            assertThat(spec.tool().name()).isEqualTo("export_key");
            assertThat(spec.tool().annotations().readOnlyHint()).isTrue();
        });
    }

    @Test
    void disablingExportIsIrreversibleAndErrorsNeverEchoUpstreamBody() {
        stubFor(patch(urlEqualTo(ITEM_PATH + "/export/disable"))
                .willReturn(okJson("{\"uuid\":\"" + ITEM + "\",\"exportable\":false,\"keyData\":\"SENTINEL_KEY\"}")));
        assertThat(tool.disableKeyExport(KEY, ITEM))
                .containsIgnoringCase("irreversible")
                .doesNotContain("SENTINEL_KEY");
        stubFor(patch(urlEqualTo(ITEM_PATH + "/export/disable"))
                .willReturn(aResponse().withStatus(422).withBody("SENTINEL_KEY")));
        assertThat(tool.disableKeyExport(KEY, ITEM)).doesNotContain("SENTINEL_KEY").contains("422");
    }

    @Test
    void publicOrUnexportableItemsNeverReachMaterialExport() {
        exportable();
        stubFor(get(urlEqualTo(ITEM_PATH))
                .willReturn(okJson("{\"uuid\":\"" + ITEM
                        + "\",\"state\":\"active\",\"enabled\":true,\"exportable\":true,\"type\":\"Public\",\"keyAlgorithm\":\"RSA\"}")));
        assertThat(tool.exportKey(KEY, ITEM, "synthetic-passphrase", null)).containsIgnoringCase("private or secret");
        verify(0, postRequestedFor(urlEqualTo(ITEM_PATH + "/export")));
    }

    @Test
    void generationAndTransferSchemaCallbacksUseKeyScopeWithProfileParent() {
        String group = "[{\"name\":\"choices\",\"type\":\"group\",\"attributeCallback\":{\"dependsOn\":[]}}]";
        stubFor(get(urlEqualTo(PREFIX + "/keys/keyPair/attributes")).willReturn(okJson(group)));
        stubFor(get(urlEqualTo(PREFIX + "/keys/keyPair/import/attributes")).willReturn(okJson(group)));
        stubFor(get(urlEqualTo(ITEM_PATH + "/export/attributes")).willReturn(okJson(group)));
        stubFor(post(urlEqualTo("/v1/keys/" + PROFILE + "/callback")).willReturn(okJson(SCHEMA)));
        key();
        assertThat(tool.getKeyGenerationAttributes(TOKEN, PROFILE, "keyPair", null)).contains("algorithm", "RSA");
        assertThat(tool.getKeyImportAttributes(TOKEN, PROFILE, "keyPair", null)).contains("algorithm", "RSA");
        assertThat(tool.getKeyExportAttributes(KEY, ITEM, null)).contains("algorithm", "RSA");
        verify(3, postRequestedFor(urlEqualTo("/v1/keys/" + PROFILE + "/callback")));
    }

    private void exportable() {
        key();
        stubFor(get(urlEqualTo(ITEM_PATH))
                .willReturn(okJson("{\"uuid\":\"" + ITEM
                        + "\",\"state\":\"active\",\"enabled\":true,\"exportable\":true,\"type\":\"Private\",\"keyAlgorithm\":\"RSA\"}")));
        stubFor(get(urlEqualTo(ITEM_PATH + "/export/attributes")).willReturn(okJson("[]")));
    }

    private void key() {
        stubFor(get(urlEqualTo(KEY_PATH))
                .willReturn(okJson("{\"uuid\":\"" + KEY
                        + "\",\"name\":\"key\",\"description\":\"old\",\"groups\":[],\"customAttributes\":[],\"tokenInstanceUuid\":\""
                        + TOKEN + "\",\"tokenProfileUuid\":\"" + PROFILE + "\",\"items\":[{\"uuid\":\"" + ITEM
                        + "\"}]}")));
    }
}
