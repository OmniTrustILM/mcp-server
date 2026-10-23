package com.otilm.mcp.tool;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolsetTest {

    private static final String VALID = "Valid values: all, certificates, keys, infrastructure, secrets, vaults, crypto-assets, cboms, signing, workflows, attributes, users.";

    @Test
    void allSelectsEveryToolset() {
        assertThat(Toolset.parse(List.of("all"))).containsExactly(Toolset.values());
        assertThat(Toolset.parse(List.of("keys", "ALL"))).containsExactly(Toolset.values());
    }

    @Test
    void namesSelectTheirToolsetsOnce() {
        assertThat(Toolset.parse(List.of(" Signing ", "keys", "CRYPTO_ASSETS", "keys")))
                .containsExactly(Toolset.KEYS, Toolset.CRYPTO_ASSETS, Toolset.SIGNING);
    }

    @Test
    void rejectsUnknownNames() {
        List<String> values = List.of("keys", "certificate");

        assertThatThrownBy(() -> Toolset.parse(values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown toolset 'certificate' in ilm.mcp.toolsets (ILM_MCP_TOOLSETS). " + VALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsAMissingList(List<String> values) {
        assertThatThrownBy(() -> Toolset.parse(values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown toolset '' in ilm.mcp.toolsets (ILM_MCP_TOOLSETS). " + VALID);
    }
}
