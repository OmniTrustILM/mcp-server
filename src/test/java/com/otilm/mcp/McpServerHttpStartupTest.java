package com.otilm.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
@ActiveProfiles({"test", "http"})
class McpServerHttpStartupTest {

    @LocalServerPort
    private int port;

    @Test
    void initializesAndListsToolsOverStreamableHttp() {
        try (McpSyncClient client = client()) {
            McpSchema.InitializeResult initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("ilm-mcp-server");
            assertThat(initialized.instructions()).contains("get_crypto_asset_statistics");
            assertThat(client.listTools().tools())
                    .hasSize(35)
                    .extracting(McpSchema.Tool::name)
                    .contains("search_certificates", "get_searchable_fields", "get_crypto_asset_statistics",
                            "search_cboms", "search_signing_records");
        }
    }

    @Test
    void toolTextReachesClientsAsPlainText() {
        try (McpSyncClient client = client()) {
            client.initialize();
            McpSchema.CallToolResult result = client
                    .callTool(McpSchema.CallToolRequest
                            .builder("get_searchable_fields")
                            .arguments(Map.of("resourceType", "unknown"))
                            .build());

            assertThat(result.content())
                    .singleElement()
                    .isInstanceOfSatisfying(McpSchema.TextContent.class,
                            text -> assertThat(text.text()).startsWith("Unknown resource type: unknown."));
        }
    }

    private McpSyncClient client() {
        return McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build()).build();
    }
}
