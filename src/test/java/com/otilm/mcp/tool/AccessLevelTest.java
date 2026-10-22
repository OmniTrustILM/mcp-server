package com.otilm.mcp.tool;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.otilm.mcp.tool.ToolKind.DESTRUCTIVE;
import static com.otilm.mcp.tool.ToolKind.READ;
import static com.otilm.mcp.tool.ToolKind.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessLevelTest {

    @ParameterizedTest
    @CsvSource({
            "read-only, READ_ONLY",
            "read-write, READ_WRITE",
            "full, FULL",
            "READ_WRITE, READ_WRITE",
            "' Read-Write ', READ_WRITE"})
    void parsesOperatorSpellings(String value, AccessLevel expected) {
        assertThat(AccessLevel.parse(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "readonly", "write"})
    void rejectsUnknownValues(String value) {
        assertThatThrownBy(() -> AccessLevel.parse(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown access level '")
                .hasMessageEndingWith(
                        "' in ilm.mcp.access (ILM_MCP_ACCESS). Valid values: read-only, read-write, full.");
    }

    @Test
    void eachLevelAllowsItsKinds() {
        assertThat(kindsAllowedBy(AccessLevel.READ_ONLY)).containsExactly(READ);
        assertThat(kindsAllowedBy(AccessLevel.READ_WRITE)).containsExactly(READ, WRITE);
        assertThat(kindsAllowedBy(AccessLevel.FULL)).containsExactly(READ, WRITE, DESTRUCTIVE);
    }

    @Test
    void labelsAreTheConfigurationValues() {
        assertThat(AccessLevel.values())
                .extracting(AccessLevel::label)
                .containsExactly("read-only", "read-write", "full");
    }

    private static List<ToolKind> kindsAllowedBy(AccessLevel level) {
        return Arrays.stream(ToolKind.values()).filter(level::allows).toList();
    }
}
