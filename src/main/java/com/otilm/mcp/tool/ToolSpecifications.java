package com.otilm.mcp.tool;

import com.otilm.mcp.security.RequestCredentials;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;
import org.springframework.ai.mcp.annotation.provider.tool.SyncStatelessMcpToolProvider;

/**
 * Builds the MCP tool specifications of tool objects and keeps the ones the access level allows.
 */
public final class ToolSpecifications {

    private ToolSpecifications() {
    }

    public static List<McpServerFeatures.SyncToolSpecification> allowed(AccessLevel level, List<Object> toolObjects) {
        Set<String> sensitive = sensitiveNames(toolObjects);
        return new SyncMcpToolProvider(toolObjects)
                .getToolSpecifications()
                .stream()
                .filter(specification -> level.allows(ToolKind.of(specification.tool().annotations())))
                .filter(specification -> level == AccessLevel.FULL || !sensitive.contains(specification.tool().name()))
                .toList();
    }

    public static List<McpStatelessServerFeatures.SyncToolSpecification> allowedStateless(AccessLevel level,
            List<Object> toolObjects) {
        Set<String> sensitive = sensitiveNames(toolObjects);
        return new SyncStatelessMcpToolProvider(toolObjects)
                .getToolSpecifications()
                .stream()
                .filter(specification -> level.allows(ToolKind.of(specification.tool().annotations())))
                .filter(specification -> level == AccessLevel.FULL || !sensitive.contains(specification.tool().name()))
                .map(specification -> new McpStatelessServerFeatures.SyncToolSpecification(specification.tool(),
                        (context, request) -> RequestCredentials
                                .call(context, () -> specification.callHandler().apply(context, request))))
                .toList();
    }

    private static Set<String> sensitiveNames(List<Object> toolObjects) {
        return toolObjects
                .stream()
                .flatMap(tool -> Arrays.stream(tool.getClass().getMethods()))
                .filter(method -> method.isAnnotationPresent(SensitiveOutput.class))
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> {
                    String name = method.getAnnotation(McpTool.class).name();
                    return name.isBlank() ? method.getName() : name;
                })
                .collect(Collectors.toSet());
    }
}
