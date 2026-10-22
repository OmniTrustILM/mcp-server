package com.otilm.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * What a tool does to ILM, read from its MCP hints. Like MCP, it treats a tool that states no hints as destructive.
 */
public enum ToolKind {

    READ,
    WRITE,
    DESTRUCTIVE;

    public static ToolKind of(McpSchema.ToolAnnotations hints) {
        if (hints == null) {
            return DESTRUCTIVE;
        }
        if (Boolean.TRUE.equals(hints.readOnlyHint())) {
            return READ;
        }
        return Boolean.FALSE.equals(hints.destructiveHint()) ? WRITE : DESTRUCTIVE;
    }
}
