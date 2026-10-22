package com.otilm.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures;
import java.util.List;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

/**
 * Builds the MCP tool specifications of tool objects and keeps the ones the access level allows.
 */
public final class ToolSpecifications {

    private ToolSpecifications() {
    }

    public static List<McpServerFeatures.SyncToolSpecification> allowed(AccessLevel level, List<Object> toolObjects) {
        return new SyncMcpToolProvider(toolObjects)
                .getToolSpecifications()
                .stream()
                .filter(specification -> level.allows(ToolKind.of(specification.tool().annotations())))
                .toList();
    }
}
