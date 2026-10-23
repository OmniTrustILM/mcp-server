package com.otilm.mcp;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.ConnectorTool;
import com.otilm.mcp.tool.SensitiveOutput;
import com.otilm.mcp.tool.ToolKind;
import com.otilm.mcp.tool.ToolSpecifications;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "ilm.mcp.access=full",
                "ilm.mcp.toolsets=all,keys,connectors,metadata",
                "ilm.api.auth.method=oauth-passthrough",
                "logging.level.root=DEBUG"})
@ActiveProfiles({"test", "http"})
@ExtendWith(OutputCaptureExtension.class)
class McpServerConfigurationInventoryTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    private static final List<String> MATERIAL = List
            .of("export_key", "export_certificate_keystore", "get_secret_content", "generate_acme_eab_key",
                    "get_proxy_installation_instructions");
    @RegisterExtension
    static WireMockExtension ilm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();
    @LocalServerPort
    private int port;
    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void url(DynamicPropertyRegistry registry) {
        registry.add("ilm.api.url", ilm::baseUrl);
    }

    @Test
    void everyToolBeanAppearsExactlyOnceAndAccessFilteringCoversEveryDeclaredMethod() {
        List<Object> beans = context
                .getBeansWithAnnotation(Component.class)
                .values()
                .stream()
                .filter(bean -> bean.getClass().getPackageName().equals("com.otilm.mcp.tool"))
                .toList();
        List<String> declared = beans
                .stream()
                .flatMap(bean -> Arrays.stream(bean.getClass().getMethods()))
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> method.getAnnotation(McpTool.class).name())
                .toList();
        assertThat(declared).doesNotHaveDuplicates();
        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            client.initialize();
            List<McpSchema.Tool> tools = client.listTools().tools();
            assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrderElementsOf(declared);
            assertThat(tools)
                    .extracting(McpSchema.Tool::name)
                    .containsAll(MATERIAL)
                    .contains("create_connector", "update_certificate_metadata", "generate_key",
                            "create_vault_instance", "create_role", "update_scheduled_job", "update_platform_settings",
                            "create_custom_attribute_definition");
        }
        for (AccessLevel level : List.of(AccessLevel.READ_ONLY, AccessLevel.READ_WRITE)) {
            var allowed = ToolSpecifications.allowed(level, beans);
            assertThat(allowed)
                    .extracting(specification -> specification.tool().name())
                    .doesNotContainAnyElementsOf(MATERIAL);
            allowed
                    .forEach(specification -> assertThat(level.allows(ToolKind.of(specification.tool().annotations())))
                            .isTrue());
            beans
                    .stream()
                    .flatMap(bean -> Arrays.stream(bean.getClass().getMethods()))
                    .filter(method -> method.isAnnotationPresent(McpTool.class))
                    .forEach(method -> {
                        McpTool annotation = method.getAnnotation(McpTool.class);
                        boolean permitted = !method.isAnnotationPresent(SensitiveOutput.class)
                                && (annotation.annotations().readOnlyHint() || level == AccessLevel.READ_WRITE
                                        && !annotation.annotations().destructiveHint());
                        assertThat(allowed
                                .stream()
                                .anyMatch(specification -> specification.tool().name().equals(annotation.name())))
                                .as(level.label() + ": " + annotation.name())
                                .isEqualTo(permitted);
                    });
        }
    }

    @Test
    void connectorConnectionProbeRequiresWriteAccess() {
        List<Object> tools = List.of(context.getBean(ConnectorTool.class));
        assertThat(ToolSpecifications.allowed(AccessLevel.READ_ONLY, tools))
                .extracting(specification -> specification.tool().name())
                .doesNotContain("test_connector_connection");
        for (AccessLevel level : List.of(AccessLevel.READ_WRITE, AccessLevel.FULL)) {
            assertThat(ToolSpecifications.allowed(level, tools))
                    .filteredOn(specification -> specification.tool().name().equals("test_connector_connection"))
                    .singleElement()
                    .satisfies(specification -> {
                        assertThat(specification.tool().annotations().readOnlyHint()).isFalse();
                        assertThat(specification.tool().annotations().destructiveHint()).isFalse();
                    });
        }
    }

    @Test
    void sensitiveContentReachesAnExplicitFullAccessCallWithoutAppearingInLogs(CapturedOutput output) {
        ilm
                .stubFor(get(urlEqualTo("/v1/secrets/" + ID + "/content"))
                        .willReturn(okJson(
                                "{\"type\":\"basicAuth\",\"username\":\"synthetic\",\"password\":\"private-sentinel-logging\"}")));
        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            client.initialize();
            var result = client
                    .callTool(McpSchema.CallToolRequest
                            .builder("get_secret_content")
                            .arguments(Map.of("uuid", ID))
                            .build());
            assertThat(result.content())
                    .anySatisfy(content -> assertThat(((McpSchema.TextContent) content).text())
                            .contains("private-sentinel-logging"));
        }
        assertThat(output).doesNotContain("private-sentinel-logging");
    }
}
