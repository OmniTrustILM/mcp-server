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
                || (server.getProtocol() == McpServerProperties.ServerProtocol.STATELESS
                        && authMethod != AuthMethod.OAUTH_DELEGATION)) {
            throw new IllegalStateException(("The ILM MCP server needs spring.ai.mcp.server.type=SYNC; STATELESS"
                    + " requires oauth-delegation. Type %s and protocol %s are incompatible with this authentication mode.")
                    .formatted(server.getType(), server.getProtocol()));
        }
        if (level != AccessLevel.READ_ONLY && !server.isStdio() && authMethod != AuthMethod.OAUTH_PASSTHROUGH
                && authMethod != AuthMethod.OAUTH_DELEGATION) {
            throw new IllegalStateException(
                    ("Access level %s over HTTP needs oauth-delegation or oauth-passthrough. With %s,"
                            + " every caller would act with the server's own ILM identity. Use shared OAuth authentication,"
                            + " run over STDIO, or set ILM_MCP_ACCESS=read-only.")
                            .formatted(level.label(), authMethod.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        }
    }
}
