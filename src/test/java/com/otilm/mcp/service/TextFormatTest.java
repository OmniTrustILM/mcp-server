package com.otilm.mcp.service;

import com.otilm.api.model.client.attribute.ResponseAttributeV2;
import com.otilm.api.model.common.NameAndUuidDto;
import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.api.model.common.attribute.common.content.data.SecretAttributeContentData;
import com.otilm.api.model.common.attribute.v2.content.SecretAttributeContentV2;
import com.otilm.api.model.core.cryptoasset.PqcVerdict;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextFormatTest {

    @Test
    void ordinaryCustomAttributesSuppressProtectedAndStructuredContent() {
        var secret = new ResponseAttributeV2();
        secret.setName("Password");
        secret.setContentType(AttributeContentType.SECRET);
        secret
                .setContent(List
                        .of(new SecretAttributeContentV2("private-sentinel",
                                new SecretAttributeContentData("private-sentinel"))));
        StringBuilder result = new StringBuilder();
        TextFormat.customAttributes(result, "", List.of(secret));
        assertThat(result.toString()).contains("Password", "omitted").doesNotContain("private-sentinel");
    }

    @Test
    void existingInventoryUrlFieldsDoNotRevealConnectionCredentials() {
        StringBuilder output = new StringBuilder();
        TextFormat
                .field(output, "", "Connector URL",
                        "https://operator:private-sentinel@example.test/path?token=private-sentinel#private-sentinel");
        assertThat(output.toString())
                .contains("https://example.test/path")
                .doesNotContain("private-sentinel", "operator");
    }

    @Test
    void orNaReplacesNullOnly() {
        assertThat(TextFormat.orNa(null)).isEqualTo("N/A");
        assertThat(TextFormat.orNa(42)).isEqualTo("42");
    }

    @Test
    void codePrintsEnumCode() {
        assertThat(TextFormat.code(PqcVerdict.NOT_READY)).isEqualTo("notReady");
        assertThat(TextFormat.code(null)).isEqualTo("N/A");
    }

    @Test
    void fieldSkipsMissingValues() {
        StringBuilder sb = new StringBuilder();

        TextFormat.field(sb, "  ", "Type", "algorithm");
        TextFormat.field(sb, "  ", "Description", null);
        TextFormat.field(sb, "  ", "Owner", " ");

        assertThat(sb).hasToString("  Type: algorithm\n");
    }

    @Test
    void titleIsUnderlinedToItsLength() {
        StringBuilder sb = new StringBuilder();

        TextFormat.title(sb, "Groups (2)");

        assertThat(sb).hasToString("Groups (2)\n==========\n\n");
    }

    @Test
    void pageHeaderNamesTheCountAndPage() {
        StringBuilder sb = new StringBuilder();

        TextFormat.pageHeader(sb, 42L, "cryptographic assets", 2, 5);

        assertThat(sb).hasToString("Found 42 cryptographic assets (page 2 of 5)\n\n");
    }

    @Test
    void countsListsEachEntryAndSkipsEmptyMaps() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ready", 10L);
        counts.put("notReady", 70L);
        StringBuilder sb = new StringBuilder();

        TextFormat.counts(sb, "Assets by PQC Verdict", counts);
        TextFormat.counts(sb, "Empty", Map.of());
        TextFormat.counts(sb, "Missing", null);

        assertThat(sb).hasToString("\nAssets by PQC Verdict:\n  ready: 10\n  notReady: 70\n");
    }

    @Test
    void itemNamesTheEntryAndItsUuid() {
        StringBuilder sb = new StringBuilder();

        TextFormat.item(sb, "production", "0b1c2d3e-4f50-4617-8293-a4b5c6d7e8f9");
        TextFormat.item(sb, null, "1c2d3e4f-5061-4728-93a4-b5c6d7e8f901");

        assertThat(sb).hasToString("""
                - production [0b1c2d3e-4f50-4617-8293-a4b5c6d7e8f9]
                - N/A [1c2d3e4f-5061-4728-93a4-b5c6d7e8f901]
                """);
    }

    @Test
    void titledListSeparatesEntriesAndSaysWhenEmpty() {
        assertThat(TextFormat
                .titledList("Groups (2)", List.of("a", "b"), "No groups found.",
                        (sb, name) -> sb.append("- ").append(name).append("\n")))
                .isEqualTo("Groups (2)\n==========\n\n- a\n\n- b\n\n");
        assertThat(TextFormat.titledList("Groups (0)", List.of(), "No groups found.", StringBuilder::append))
                .isEqualTo("Groups (0)\n==========\n\nNo groups found.\n");
    }

    @Test
    void pagedListCountsPagesAndSaysWhenEmpty() {
        assertThat(TextFormat.pagedList(1L, "CBOMs", 1, 1, List.of("x"), (sb, name) -> sb.append(name).append("\n")))
                .isEqualTo("Found 1 CBOMs (page 1 of 1)\n\nx\n\n");
        assertThat(TextFormat.pagedList(0L, "CBOMs", 1, 0, null, StringBuilder::append))
                .isEqualTo("Found 0 CBOMs (page 1 of 0)\n\nNo CBOMs found.\n");
    }

    @Test
    void nameOfToleratesMissingReference() {
        assertThat(TextFormat.nameOf(new NameAndUuidDto("2d3e4f50-6172-4839-a4b5-c6d7e8f90112", "prod-vault")))
                .isEqualTo("prod-vault");
        assertThat(TextFormat.nameOf(null)).isNull();
    }

    @Test
    void pairsKeepsMapOrder() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("algorithmFamily", "RSA");
        fields.put("parameterSet", 2048);

        assertThat(TextFormat.pairs(fields)).isEqualTo("algorithmFamily=RSA, parameterSet=2048");
        assertThat(TextFormat.pairs(null)).isNull();
        assertThat(TextFormat.pairs(Map.of())).isNull();
    }

    @Test
    void namesJoinsNames() {
        NameAndUuidDto first = new NameAndUuidDto("0b1c2d3e-4f50-4617-8293-a4b5c6d7e8f9", "production");
        NameAndUuidDto second = new NameAndUuidDto("1c2d3e4f-5061-4728-93a4-b5c6d7e8f901", "pqc-ready");

        assertThat(TextFormat.names(List.of(first, second))).isEqualTo("production, pqc-ready");
        assertThat(TextFormat.names(null)).isEmpty();
    }
}
