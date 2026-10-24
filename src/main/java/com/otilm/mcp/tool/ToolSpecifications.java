package com.otilm.mcp.tool;

import io.modelcontextprotocol.server.McpServerFeatures;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

/**
 * Builds the MCP tool specifications of tool objects and keeps the ones the access level allows.
 */
public final class ToolSpecifications {

    private ToolSpecifications() {
    }

    public static List<McpServerFeatures.SyncToolSpecification> allowed(AccessLevel level, List<Object> toolObjects) {
        Set<String> sensitive = toolObjects
                .stream()
                .flatMap(tool -> Arrays.stream(tool.getClass().getMethods()))
                .filter(method -> method.isAnnotationPresent(SensitiveOutput.class))
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> {
                    String name = method.getAnnotation(McpTool.class).name();
                    return name.isBlank() ? method.getName() : name;
                })
                .collect(Collectors.toSet());
        return new SyncMcpToolProvider(toolObjects)
                .getToolSpecifications()
                .stream()
                .filter(specification -> level.allows(ToolKind.of(specification.tool().annotations())))
                .filter(specification -> level == AccessLevel.FULL || !sensitive.contains(specification.tool().name()))
                .toList();
    }
}
