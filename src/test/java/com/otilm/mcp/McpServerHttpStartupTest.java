package com.otilm.mcp;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
@ActiveProfiles({"test", "http"})
class McpServerHttpStartupTest {

    @RegisterExtension
    static WireMockExtension ilm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void ilmApiUrl(DynamicPropertyRegistry registry) {
        registry.add("ilm.api.url", ilm::baseUrl);
    }

    @Test
    void initializesAndListsToolsOverStreamableHttp() {
        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            McpSchema.InitializeResult initialized = client.initialize();
            assertThat(initialized.serverInfo().name()).isEqualTo("ilm-mcp-server");
            assertThat(initialized.instructions())
                    .contains("This server runs with access level read-only.", "get_crypto_asset_statistics",
                            "create_trigger sets up a whole workflow in one call", "create_notification_profile");
            assertThat(client.listTools().tools())
                    .hasSizeGreaterThan(46)
                    .allSatisfy(tool -> assertThat(tool.annotations().readOnlyHint()).as(tool.name()).isTrue())
                    .extracting(McpSchema.Tool::name)
                    .contains("search_certificates", "get_searchable_fields", "get_crypto_asset_statistics",
                            "search_cboms", "search_signing_records", "get_connector", "get_scoped_triggers",
                            "get_compliance_profile")
                    .doesNotHaveDuplicates()
                    .doesNotContain("export_key", "get_secret_content", "export_certificate_keystore",
                            "generate_acme_eab_key", "get_proxy_installation_instructions",
                            "test_connector_connection");
        }
    }

    @Test
    void toolTextReachesClientsAsPlainText() {
        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
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

    @Test
    void optionalParametersCanBeOmitted() {
        ilm.stubFor(post(urlEqualTo("/v1/keys")).willReturn(okJson(IlmTestSupport.fixture("keys"))));

        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            client.initialize();
            McpSchema.CallToolResult result = client
                    .callTool(McpSchema.CallToolRequest.builder("search_keys").arguments(Map.of()).build());

            assertThat(result.content())
                    .singleElement()
                    .isInstanceOfSatisfying(McpSchema.TextContent.class,
                            text -> assertThat(text.text()).startsWith("Found 126687 cryptographic keys"));
        }
        ilm.verify(postRequestedFor(urlEqualTo("/v1/keys")).withRequestBody(equalToJson("""
                {"filters": [], "itemsPerPage": 10, "pageNumber": 1}
                """, true, true)));
    }
}
