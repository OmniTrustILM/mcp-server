package com.otilm.mcp.config;

import com.otilm.mcp.config.IlmApiProperties.AuthMethod;
import com.otilm.mcp.tool.AccessLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessChecksTest {

    @ParameterizedTest
    @EnumSource(value = AuthMethod.class, names = "OAUTH_DELEGATION", mode = EnumSource.Mode.EXCLUDE)
    void readOnlyRunsOverHttpWithAnyAuthMethod(AuthMethod method) {
        assertThatCode(() -> AccessChecks.verify(AccessLevel.READ_ONLY, method, new McpServerProperties()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"CERTIFICATE, certificate", "OAUTH_CLIENT_CREDENTIALS, oauth-client-credentials", "NONE, none"})
    void writeAccessOverHttpNeedsCallerAuthentication(AuthMethod method, String label) {
        McpServerProperties http = new McpServerProperties();

        assertThatThrownBy(() -> AccessChecks.verify(AccessLevel.READ_WRITE, method, http))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Access level read-write over HTTP needs oauth-delegation or oauth-passthrough. With "
                        + label + ", every caller would act with the server's own ILM identity. Use shared OAuth"
                        + " authentication, run over STDIO, or set ILM_MCP_ACCESS=read-only.");
    }

    @Test
    void passthroughAllowsFullAccessOverHttp() {
        assertThatCode(
                () -> AccessChecks.verify(AccessLevel.FULL, AuthMethod.OAUTH_PASSTHROUGH, new McpServerProperties()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = AuthMethod.class, names = "OAUTH_DELEGATION", mode = EnumSource.Mode.EXCLUDE)
    void stdioAllowsFullAccessWithAnyAuthMethod(AuthMethod method) {
        McpServerProperties stdio = new McpServerProperties();
        stdio.setStdio(true);

        assertThatCode(() -> AccessChecks.verify(AccessLevel.FULL, method, stdio)).doesNotThrowAnyException();
    }

    @Test
    void refusesTheAsyncServer() {
        McpServerProperties server = new McpServerProperties();
        server.setType(McpServerProperties.ApiType.ASYNC);

        assertThatThrownBy(() -> AccessChecks.verify(AccessLevel.READ_ONLY, AuthMethod.CERTIFICATE, server))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The ILM MCP server needs spring.ai.mcp.server.type=SYNC; STATELESS requires"
                        + " oauth-delegation. Type ASYNC and protocol STREAMABLE are incompatible with this"
                        + " authentication mode.");
    }

    @Test
    void refusesTheStatelessProtocol() {
        McpServerProperties server = new McpServerProperties();
        server.setProtocol(McpServerProperties.ServerProtocol.STATELESS);

        assertThatThrownBy(() -> AccessChecks.verify(AccessLevel.READ_ONLY, AuthMethod.CERTIFICATE, server))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageEndingWith(
                        "Type SYNC and protocol STATELESS are incompatible with this authentication mode.");
    }
}
