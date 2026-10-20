package com.otilm.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
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
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            assertThat(client.initialize().serverInfo().name()).isEqualTo("ilm-mcp-server");
            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .contains("search_certificates", "get_searchable_fields");
        }
    }
}
