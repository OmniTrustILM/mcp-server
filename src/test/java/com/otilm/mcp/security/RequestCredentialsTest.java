package com.otilm.mcp.security;

import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCredentialsTest {
    @Test
    void restoresNestedBindingAndClearsWorkerAfterException() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                assertThatThrownBy(RequestCredentials::token).isInstanceOf(IllegalStateException.class);
                RequestCredentials.call(context("alice"), () -> {
                    assertThat(RequestCredentials.token()).isEqualTo("alice");
                    assertThatThrownBy(() -> RequestCredentials.call(context("bob"), () -> {
                        assertThat(RequestCredentials.token()).isEqualTo("bob");
                        throw new IllegalStateException("tool failure");
                    })).hasMessage("tool failure");
                    assertThat(RequestCredentials.token()).isEqualTo("alice");
                    return null;
                });
                assertThatThrownBy(RequestCredentials::token).isInstanceOf(IllegalStateException.class);
            }).get(5, TimeUnit.SECONDS);
            worker
                    .submit(() -> assertThatThrownBy(RequestCredentials::token)
                            .isInstanceOf(IllegalStateException.class))
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void cancellationDoesNotLeaveCredentialsOnReusedWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var running = worker.submit(() -> RequestCredentials.call(context("alice"), () -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            running.cancel(true);
            worker
                    .submit(() -> assertThatThrownBy(RequestCredentials::token)
                            .isInstanceOf(IllegalStateException.class))
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void missingOrUntrustedContextFailsClosedEvenInsideBinding() {
        RequestCredentials.call(context("alice"), () -> {
            assertThatThrownBy(() -> RequestCredentials.call(McpTransportContext.EMPTY, () -> "unsafe"))
                    .isInstanceOf(IllegalStateException.class);
            McpTransportContext spoofed = McpTransportContext.create(Map.of(RequestCredentials.KEY, "spoof"));
            assertThatThrownBy(() -> RequestCredentials.call(spoofed, () -> "unsafe"))
                    .isInstanceOf(IllegalStateException.class);
            return null;
        });
    }

    static McpTransportContext context(String token) {
        return McpTransportContext.create(Map.of(RequestCredentials.KEY, new RequestCredential(token)));
    }
}
