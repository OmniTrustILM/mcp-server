package com.otilm.mcp;

import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.ToolKind;
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
                    .allSatisfy(
                            tool -> assertThat(AccessLevel.READ_WRITE.allows(ToolKind.of(tool.annotations()))).isTrue())
                    .extracting(McpSchema.Tool::name)
                    .contains("get_searchable_fields", "search_keys", "get_key", "get_key_item", "generate_key",
                            "import_key", "update_key")
                    .doesNotHaveDuplicates()
                    .doesNotContain("export_key", "destroy_key_item", "delete_key", "get_secret_content",
                            "create_connector");
        }
        assertThat(output).contains("tools for access level read-write and toolsets keys");
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
