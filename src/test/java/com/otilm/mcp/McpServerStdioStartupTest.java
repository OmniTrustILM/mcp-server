package com.otilm.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "logging.level.root=DEBUG")
@ActiveProfiles({"test", "stdio"})
@ExtendWith(OutputCaptureExtension.class)
class McpServerStdioStartupTest {

    private static final String MARKER = "marker-5c1e9a";

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
            "capabilities":{},"clientInfo":{"name":"%s","version":"1.0"}}}
            """.formatted(MARKER);

    private static final PipedOutputStream TO_SERVER = new PipedOutputStream();
    private static final PipedInputStream FROM_SERVER = new PipedInputStream();

    @TestConfiguration
    static class PipedStdio {

        @Bean
        McpServerTransportProviderBase stdioServerTransport() throws IOException {
            return new StdioServerTransportProvider(McpJsonDefaults.getMapper(), new PipedInputStream(TO_SERVER),
                    new PipedOutputStream(FROM_SERVER));
        }
    }

    @Test
    void answersInitializeWithoutLoggingTheRequest(CapturedOutput output) throws Exception {
        TO_SERVER.write(INITIALIZE.getBytes(UTF_8));
        TO_SERVER.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(FROM_SERVER, UTF_8));
        String response = CompletableFuture.supplyAsync(() -> readLine(reader)).get(10, TimeUnit.SECONDS);

        assertThat(response).contains("\"name\":\"ilm-mcp-server\"");
        assertThat(output).contains("DEBUG").doesNotContain(MARKER);
    }

    private static String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
