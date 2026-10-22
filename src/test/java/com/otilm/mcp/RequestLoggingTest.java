package com.otilm.mcp;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock's own loggers are test infrastructure, so this test keeps them at INFO itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.web-application-type=servlet",
                "logging.level.root=TRACE",
                "logging.level.wiremock=INFO",
                "logging.level.com.github.tomakehurst.wiremock=INFO",
                "logging.level.org.wiremock=INFO"})
@ActiveProfiles({"test", "http"})
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingTest {

    private static final String MARKER = "marker-5c1e9a";

    @RegisterExtension
    static WireMockExtension ilm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void ilmApiUrl(DynamicPropertyRegistry registry) {
        registry.add("ilm.api.url", ilm::baseUrl);
    }

    @Test
    void broadDebugLoggingLeavesRequestContentsOut(CapturedOutput output) {
        ilm.stubFor(post(urlEqualTo("/v1/keys")).willReturn(okJson(IlmTestSupport.fixture("keys"))));
        String filters = """
                [{"fieldSource":"property","fieldIdentifier":"commonName","condition":"CONTAINS","value":"%s"}]
                """.formatted(MARKER);

        try (McpSyncClient client = IlmTestSupport.mcpClient(port)) {
            client.initialize();
            client
                    .callTool(McpSchema.CallToolRequest
                            .builder("search_keys")
                            .arguments(Map.of("filters", filters))
                            .build());
        }

        ilm.verify(postRequestedFor(urlEqualTo("/v1/keys")).withRequestBody(containing(MARKER)));
        assertThat(output).contains("DEBUG").doesNotContain(MARKER);
    }
}
