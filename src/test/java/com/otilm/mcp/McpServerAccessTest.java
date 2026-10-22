package com.otilm.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "ilm.mcp.access=read-write",
                "ilm.mcp.toolsets=keys",
                "ilm.api.auth.method=oauth-passthrough"})
@ActiveProfiles({"test", "http"})
@ExtendWith(OutputCaptureExtension.class)
class McpServerAccessTest {

    @LocalServerPort
    private int port;

    @Test
    void registersOnlyTheChosenToolsets(CapturedOutput output) {
        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            client.initialize();
            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("get_searchable_fields", "search_keys", "get_key");
        }
        assertThat(output).contains("Registered 3 tools for access level read-write and toolsets keys");
    }

    @Test
    void refusesWriteAccessOverHttpWithoutPassthrough() {
        assertThatThrownBy(() -> start("--ilm.mcp.access=read-write"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Access level read-write over HTTP needs ILM_AUTH_METHOD=oauth-passthrough.");
    }

    @Test
    void refusesAnUnknownAccessLevel() {
        assertThatThrownBy(() -> start("--ilm.mcp.access=readonly"))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unknown access level 'readonly'");
    }

    private static void start(String access) {
        new SpringApplicationBuilder(McpServerApplication.class)
                .profiles("test", "http")
                .run("--spring.main.web-application-type=servlet", "--server.port=0", access)
                .close();
    }
}
