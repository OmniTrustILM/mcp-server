package com.otilm.mcp.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.config.IlmApiClientConfiguration;
import com.otilm.mcp.config.IlmApiProperties;
import com.otilm.mcp.service.AutomationMaintenanceServiceImpl;
import com.otilm.mcp.service.ConfigurationSupport;
import com.otilm.mcp.service.InfrastructureServiceImpl;
import com.otilm.mcp.service.PlatformVersionCheck;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    @ValueSource(
            strings = {"malformed-json", "invalid-dto", "body-read", "unknown-content-type", "invalid-content-type"})
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
            if (variant.equals("invalid-content-type")) {
                response.getHeaders().set(HttpHeaders.CONTENT_TYPE, SENTINEL);
            } else {
                response
                        .getHeaders()
                        .setContentType(variant.equals("unknown-content-type")
                                ? MediaType.TEXT_PLAIN
                                : MediaType.APPLICATION_JSON);
            }
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
    void workflowReplacementDropsMalformedHeadersFromItsLegacyMetadataRead(CapturedOutput output) {
        String trigger = "00000000-0000-4000-8000-000000000531";
        AtomicInteger metadataReads = new AtomicInteger();
        ClientHttpRequestFactory factory = (uri, method) -> {
            assertThat(method).isEqualTo(HttpMethod.GET);
            String body;
            String contentType = MediaType.APPLICATION_JSON_VALUE;
            if (uri.getPath().equals("/api/v1/info")) {
                body = "{\"app\":{\"version\":\"2.20.0\"}}";
            } else if (uri.getPath().equals("/api/v1/workflows/triggers/" + trigger)) {
                body = """
                        {"uuid":"%s","name":"Existing","type":"event","resource":"certificates",
                         "event":"certificate_expiring","rules":[],"actions":[]}
                        """.formatted(trigger);
            } else {
                assertThat(uri.getPath()).isEqualTo("/api/v1/resources/certificates/filters/rules");
                metadataReads.incrementAndGet();
                body = "[]";
                contentType = SENTINEL;
            }
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8),
                    HttpStatus.OK);
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
            request.setResponse(response);
            return request;
        };
        RestClient configured = configuredClient(IlmApiProperties.AuthMethod.OAUTH_DELEGATION, factory);
        IlmApiClient legacy = new IlmApiClient(configured);
        AutomationMaintenanceServiceImpl service = new AutomationMaintenanceServiceImpl(
                new ConfigurationApiClient(configured), legacy, new PlatformVersionCheck(legacy));

        String result = RequestCredentials
                .call(RequestCredentialsTest.context(SENTINEL), () -> service.updateTrigger(trigger, """
                        {"replaceComposition":true,"conditions":[{"fieldSource":"property",
                         "fieldIdentifier":"OWNER","condition":"EMPTY"}],"setFields":[],
                         "notificationProfileUuid":null,"ignore":true}
                        """));

        assertThat(result).contains("Error updating trigger").doesNotContain(SENTINEL);
        assertThat(metadataReads.get()).isEqualTo(1);
        assertThat(output).doesNotContain(SENTINEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content-type", "unsupported-filename-charset", "illegal-filename-charset"})
    void configurationClientDropsMalformedResponseHeadersBeforeErrorPresentation(String variant,
            CapturedOutput output) {
        ClientHttpRequestFactory factory = (uri, method) -> {
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            MockClientHttpResponse response = new MockClientHttpResponse("{}".getBytes(StandardCharsets.UTF_8),
                    HttpStatus.OK);
            if (variant.equals("content-type")) {
                response.getHeaders().set(HttpHeaders.CONTENT_TYPE, SENTINEL);
            } else {
                response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                String charset = variant.equals("unsupported-filename-charset") ? SENTINEL : "bad[" + SENTINEL + "]";
                response
                        .getHeaders()
                        .set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=" + charset + "''report.bin");
            }
            request.setResponse(response);
            return request;
        };
        ConfigurationApiClient client = new ConfigurationApiClient(
                configuredClient(IlmApiProperties.AuthMethod.OAUTH_DELEGATION, factory));

        String result = RequestCredentials
                .call(RequestCredentialsTest.context(SENTINEL),
                        () -> ConfigurationSupport.run("reading configuration", () -> {
                            if (variant.equals("content-type")) {
                                return client.get("/v1/settings").toString();
                            }
                            return client.download("/v1/export", Map.of()).filename();
                        }));

        assertThat(result).contains("ILM", "request").doesNotContain(SENTINEL);
        assertThat(output).doesNotContain(SENTINEL);
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
        return new GroupTool(new InfrastructureServiceImpl(new IlmApiClient(configuredClient(method, factory))));
    }

    private static RestClient configuredClient(IlmApiProperties.AuthMethod method, ClientHttpRequestFactory factory) {
        IlmApiProperties properties = new IlmApiProperties();
        properties.setUrl("https://ilm.test/api");
        properties.getAuth().setMethod(method);
        RestClient configured = new IlmApiClientConfiguration()
                .ilmRestClient(properties, Optional.empty(), Optional.of(factory));
        if (method != IlmApiProperties.AuthMethod.OAUTH_DELEGATION) {
            configured = configured.mutate().requestFactory(factory).build();
        }
        return configured;
    }
}
