package com.otilm.mcp.config;

import com.otilm.mcp.config.IlmApiProperties.AuthMethod;
import com.otilm.mcp.tool.AccessLevel;
import java.util.Locale;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;

/**
 * Settings the server refuses to start with: a server variant that would get no tools, and write access over HTTP while
 * every caller would act with the server's own ILM identity.
 */
final class AccessChecks {

    private AccessChecks() {
    }

    static void verify(AccessLevel level, AuthMethod authMethod, McpServerProperties server) {
        if (server.getType() != McpServerProperties.ApiType.SYNC
                || server.getProtocol() == McpServerProperties.ServerProtocol.STATELESS) {
            throw new IllegalStateException(("The ILM MCP server needs spring.ai.mcp.server.type=SYNC and a protocol"
                    + " other than STATELESS; with type %s and protocol %s it would start without tools.")
                    .formatted(server.getType(), server.getProtocol()));
        }
        if (level != AccessLevel.READ_ONLY && !server.isStdio() && authMethod != AuthMethod.OAUTH_PASSTHROUGH) {
            throw new IllegalStateException(
                    ("Access level %s over HTTP needs ILM_AUTH_METHOD=oauth-passthrough. With %s,"
                            + " every caller would act with the server's own ILM identity. Use oauth-passthrough, run over"
                            + " STDIO, or set ILM_MCP_ACCESS=read-only.")
                            .formatted(level.label(), authMethod.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        }
    }
}
