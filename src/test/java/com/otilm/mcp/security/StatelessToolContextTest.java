package com.otilm.mcp.security;

import com.otilm.mcp.tool.AccessLevel;
import com.otilm.mcp.tool.SensitiveOutput;
import com.otilm.mcp.tool.ToolSpecifications;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatelessToolContextTest {
    @Test
    void registeredCallbackPropagatesAcrossThreadsAndCleansUpAfterFailure() throws Exception {
        Probe tool = new Probe();
        var specs = ToolSpecifications.allowedStateless(AccessLevel.FULL, List.of(tool));
        var read = named(specs, "read");
        var failure = named(specs, "failure");
        try (var worker = Executors.newSingleThreadExecutor()) {
            for (String user : List.of("alice", "bob")) {
                var result = worker
                        .submit(() -> read
                                .callHandler()
                                .apply(RequestCredentialsTest.context(user),
                                        McpSchema.CallToolRequest.builder("read").arguments(Map.of()).build()))
                        .get(5, TimeUnit.SECONDS);
                assertThat(result.content().toString()).contains(user);
                worker.submit(() -> {
                    try {
                        failure
                                .callHandler()
                                .apply(RequestCredentialsTest.context(user),
                                        McpSchema.CallToolRequest.builder("failure").arguments(Map.of()).build());
                    } catch (RuntimeException expected) {
                        /* Provider may surface a tool failure or return an error result. */ }
                    assertThatThrownBy(RequestCredentials::token).isInstanceOf(IllegalStateException.class);
                    var handler = read.callHandler();
                    var spoofed = McpSchema.CallToolRequest.builder("read").arguments(Map.of("token", "spoof")).build();
                    assertThatThrownBy(() -> handler.apply(McpTransportContext.EMPTY, spoofed))
                            .isInstanceOf(IllegalStateException.class);
                }).get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void cancellationOfRegisteredCallbackCleansReusedWorker() throws Exception {
        Probe tool = new Probe();
        var blocked = named(ToolSpecifications.allowedStateless(AccessLevel.FULL, List.of(tool)), "blocked");
        try (var worker = Executors.newSingleThreadExecutor()) {
            var running = worker
                    .submit(() -> blocked
                            .callHandler()
                            .apply(RequestCredentialsTest.context("alice"),
                                    McpSchema.CallToolRequest.builder("blocked").arguments(Map.of()).build()));
            assertThat(tool.entered.await(5, TimeUnit.SECONDS)).isTrue();
            running.cancel(true);
            worker
                    .submit(() -> assertThatThrownBy(RequestCredentials::token)
                            .isInstanceOf(IllegalStateException.class))
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void statelessCatalogPreservesSensitiveAndWriteCeilings() {
        assertThat(ToolSpecifications.allowedStateless(AccessLevel.READ_ONLY, List.of(new Probe())))
                .extracting(spec -> spec.tool().name())
                .containsExactlyInAnyOrder("read");
        assertThat(ToolSpecifications.allowedStateless(AccessLevel.FULL, List.of(new Probe())))
                .extracting(spec -> spec.tool().name())
                .containsExactlyInAnyOrder("read", "failure", "blocked", "sensitive");
    }

    static McpStatelessServerFeatures.SyncToolSpecification named(
            List<McpStatelessServerFeatures.SyncToolSpecification> specs, String name) {
        return specs.stream().filter(spec -> spec.tool().name().equals(name)).findFirst().orElseThrow();
    }

    public static final class Probe {
        final CountDownLatch entered = new CountDownLatch(1);

        @McpTool(name = "read", description = "Read credential in test",
                annotations = @McpTool.McpAnnotations(readOnlyHint = true))
        public String read() {
            return RequestCredentials.token();
        }

        @McpTool(name = "failure", description = "Fail inside callback")
        public String failure() {
            throw new IllegalStateException("expected failure");
        }

        @McpTool(name = "blocked", description = "Interrupt callback")
        public String blocked() {
            RequestCredentials.token();
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
            }
            return "interrupted";
        }

        @SensitiveOutput
        @McpTool(name = "sensitive", description = "Sensitive read",
                annotations = @McpTool.McpAnnotations(readOnlyHint = true))
        public String sensitive() {
            return "sensitive";
        }
    }
}
