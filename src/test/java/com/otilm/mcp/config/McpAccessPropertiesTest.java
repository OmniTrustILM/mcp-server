package com.otilm.mcp.config;

import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.Toolset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpAccessPropertiesTest {

    @Test
    void bindsOperatorValues() {
        McpAccessProperties properties = bind(
                Map.of("ilm.mcp.access", "read-write", "ilm.mcp.toolsets", "keys, Certificates"));

        assertThat(properties.accessLevel()).isEqualTo(AccessLevel.READ_WRITE);
        assertThat(properties.enabledToolsets()).containsExactly(Toolset.CERTIFICATES, Toolset.KEYS);
    }

    @Test
    void emptyToolsetsAreRejected() {
        McpAccessProperties properties = bind(Map.of("ilm.mcp.access", "read-only", "ilm.mcp.toolsets", ""));

        assertThatThrownBy(properties::enabledToolsets)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown toolset ''");
    }

    private static McpAccessProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bind("ilm.mcp", McpAccessProperties.class).get();
    }
}
