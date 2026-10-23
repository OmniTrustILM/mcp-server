package com.otilm.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "ilm.mcp.access=full",
                "ilm.mcp.toolsets=workflows,attributes,users,notifications",
                "ilm.api.auth.method=oauth-passthrough"})
@ActiveProfiles({"test", "http"})
class McpServerFullAccessTest {

    @LocalServerPort
    private int port;

    @Test
    void writeToolsCarryTheirKind() {
        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            client.initialize();
            Map<String, McpSchema.ToolAnnotations> hints = client
                    .listTools()
                    .tools()
                    .stream()
                    .collect(Collectors.toMap(McpSchema.Tool::name, McpSchema.Tool::annotations));

            assertThat(hints).hasSize(21);
            assertThat(List.of("delete_trigger", "delete_notification_instance", "delete_notification_profile"))
                    .hasSize(3)
                    .allSatisfy(name -> assertThat(hints.get(name).destructiveHint()).isTrue());
            assertThat(List
                    .of("create_trigger", "attach_trigger", "detach_trigger", "create_custom_attribute",
                            "create_notification_instance", "create_notification_profile"))
                    .hasSize(6)
                    .allSatisfy(name -> assertThat(hints.get(name)).satisfies(hint -> {
                        assertThat(hint.readOnlyHint()).isFalse();
                        assertThat(hint.destructiveHint()).isFalse();
                    }));
            assertThat(hints.get("list_users").readOnlyHint()).isTrue();
        }
    }
}
