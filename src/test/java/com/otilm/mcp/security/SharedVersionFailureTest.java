package com.otilm.mcp.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.config.IlmApiClientConfiguration;
import com.otilm.mcp.config.IlmApiProperties;
import com.otilm.mcp.service.IlmFeature;
import com.otilm.mcp.service.PlatformVersionCheck;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SharedVersionFailureTest {
    private static final String SENTINEL = "secretTokenEchoMustNeverLeak";

    @ParameterizedTest
    @ValueSource(strings = {"invalid-content-type", "malformed-json", "body-read"})
    void versionReadFailureKeepsFallbackAndLogsNoResponseDataAtDebug(String variant, CapturedOutput output) {
        ClientHttpRequestFactory factory = (uri, method) -> {
            assertThat(uri.getPath()).isEqualTo("/api/v1/info");
            MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
            MockClientHttpResponse response = variant.equals("body-read")
                    ? new MockClientHttpResponse(new InputStream() {
                        @Override
                        public int read() throws IOException {
                            throw new IOException(SENTINEL);
                        }
                    }, HttpStatus.OK)
                    : new MockClientHttpResponse(SENTINEL.getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response
                    .getHeaders()
                    .set(HttpHeaders.CONTENT_TYPE,
                            variant.equals("invalid-content-type") ? SENTINEL : MediaType.APPLICATION_JSON_VALUE);
            request.setResponse(response);
            return request;
        };
        IlmApiProperties properties = new IlmApiProperties();
        properties.setUrl("https://ilm.test/api");
        properties.getAuth().setMethod(IlmApiProperties.AuthMethod.OAUTH_DELEGATION);
        PlatformVersionCheck versionCheck = new PlatformVersionCheck(new IlmApiClient(
                new IlmApiClientConfiguration().ilmRestClient(properties, Optional.empty(), Optional.of(factory))));
        Logger logger = (Logger) LoggerFactory.getLogger(PlatformVersionCheck.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        logger.setLevel(Level.DEBUG);
        try {
            String result = RequestCredentials
                    .call(RequestCredentialsTest.context(SENTINEL), () -> versionCheck
                            .run(IlmFeature.SIGNING_RECORDS, "searching signing records", () -> "records"));

            assertThat(result).isEqualTo("records").doesNotContain(SENTINEL);
            assertThat(events.list).isNotEmpty().allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).contains("ILM version").doesNotContain(SENTINEL);
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(output).doesNotContain(SENTINEL);
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(events);
            events.stop();
        }
    }
}
