package com.otilm.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static com.otilm.mcp.tool.ToolKind.DESTRUCTIVE;
import static com.otilm.mcp.tool.ToolKind.READ;
import static com.otilm.mcp.tool.ToolKind.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

class ToolKindTest {

    @Test
    void readOnlyHintMakesARead() {
        assertThat(ToolKind.of(hints(true, true))).isEqualTo(READ);
    }

    @Test
    void changeThatIsNotDestructiveIsAWrite() {
        assertThat(ToolKind.of(hints(false, false))).isEqualTo(WRITE);
        assertThat(ToolKind.of(hints(null, false))).isEqualTo(WRITE);
    }

    @Test
    void destructiveHintMakesADestructiveTool() {
        assertThat(ToolKind.of(hints(false, true))).isEqualTo(DESTRUCTIVE);
    }

    @Test
    void missingHintsCountAsDestructive() {
        assertThat(ToolKind.of(null)).isEqualTo(DESTRUCTIVE);
        assertThat(ToolKind.of(hints(null, null))).isEqualTo(DESTRUCTIVE);
        assertThat(ToolKind.of(hints(false, null))).isEqualTo(DESTRUCTIVE);
    }

    private static McpSchema.ToolAnnotations hints(Boolean readOnly, Boolean destructive) {
        return McpSchema.ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(destructive).build();
    }
}
