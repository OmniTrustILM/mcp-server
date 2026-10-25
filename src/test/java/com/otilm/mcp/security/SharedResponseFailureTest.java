package com.otilm.mcp.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.config.IlmApiClientConfiguration;
import com.otilm.mcp.config.IlmApiProperties;
import com.otilm.mcp.service.InfrastructureServiceImpl;
import com.otilm.mcp.service.ToolResponses;
import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.GroupTool;
import com.otilm.mcp.tool.ToolSpecifications;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SharedResponseFailureTest {
    private static final String SENTINEL = "secretTokenEchoMustNeverLeak";

    @ParameterizedTest
    @ValueSource(strings = {"malformed-json", "invalid-dto", "body-read", "unknown-content-type"})
    void configuredSharedClientDropsResponseFailureMessagesAndCausesBeforeToolLogging(String variant,
            CapturedOutput output) {
        String body = variant.equals("invalid-dto") ? "[{\"customAttributes\":\"" + SENTINEL + "\"}]" : SENTINEL;
        ClientHttpRequestFactory factory = (uri, method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            MockClientHttpResponse response = variant.equals("body-read")
                    ? new MockClientHttpResponse(new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException(SENTINEL);
                        }
                    }, HttpStatus.OK)
                    : new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response
                    .getHeaders()
                    .setContentType(
                            variant.equals("unknown-content-type") ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON);
            request.setResponse(response);
            return request;
        };
        GroupTool tool = tool(IlmApiProperties.AuthMethod.OAUTH_DELEGATION, factory);
        Logger logger = (Logger) LoggerFactory.getLogger(ToolResponses.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            String result = ToolSpecifications
                    .allowedStateless(AccessLevel.READ_ONLY, List.of(tool))
                    .getFirst()
                    .callHandler()
                    .apply(RequestCredentialsTest.context("synthetic-downstream-token"),
                            McpSchema.CallToolRequest.builder("list_groups").arguments(Map.of()).build())
                    .content()
                    .toString();
            assertThat(result).contains("Error listing groups").doesNotContain(SENTINEL);
            assertThat(events.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("listing groups").doesNotContain(SENTINEL);
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(output).doesNotContain(SENTINEL);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void legacyConfiguredClientStillReturnsIlmDiagnosticMessages() {
        ClientHttpRequestFactory factory = (uri, method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            MockClientHttpResponse response = new MockClientHttpResponse(
                    "{\"message\":\"Legacy diagnostic\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.NOT_FOUND);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            request.setResponse(response);
            return request;
        };
        assertThat(tool(IlmApiProperties.AuthMethod.NONE, factory).listGroups())
                .isEqualTo("Error listing groups: ILM answered 404 Not Found: Legacy diagnostic");
    }

    private static GroupTool tool(IlmApiProperties.AuthMethod method, ClientHttpRequestFactory factory) {
        IlmApiProperties properties = new IlmApiProperties();
        properties.setUrl("https://ilm.test/api");
        properties.getAuth().setMethod(method);
        RestClient configured = new IlmApiClientConfiguration()
                .ilmRestClient(properties, Optional.empty(), Optional.of(factory));
        if (method != IlmApiProperties.AuthMethod.OAUTH_DELEGATION) {
            configured = configured.mutate().requestFactory(factory).build();
        }
        return new GroupTool(new InfrastructureServiceImpl(new IlmApiClient(configured)));
    }
}
