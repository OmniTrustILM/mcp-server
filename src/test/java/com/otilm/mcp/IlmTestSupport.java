package com.otilm.mcp;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.otilm.mcp.client.IlmApiClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class IlmTestSupport {

    private IlmTestSupport() {
    }

    public static IlmApiClient client(WireMockRuntimeInfo wmInfo) {
        return new IlmApiClient(IlmApiClient.restClientBuilder(wmInfo.getHttpBaseUrl()).build());
    }

    public static String fixture(String name) {
        String path = "/fixtures/v2_20/" + name + ".json";
        try (InputStream stream = IlmTestSupport.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing test fixture " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
