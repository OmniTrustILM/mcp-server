package com.otilm.mcp.security;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.temporaryRedirect;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedHttpTransportTest {
    @Test
    void productionTransportRefusesRedirects() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            RestClient client = productionClient(f);
            f.server.stubFor(get("/redirect").willReturn(temporaryRedirect(f.baseUrl() + "/leak")));
            assertThat(
                    client.get().uri(f.baseUrl() + "/redirect").retrieve().toBodilessEntity().getStatusCode().value())
                    .isEqualTo(302);
            f.server.verify(0, getRequestedFor(urlEqualTo("/leak")));
        }
    }

    @Test
    void productionTransportBoundsReadTime() throws Exception {
        try (OAuthFixture f = new OAuthFixture()) {
            f.properties.setReadTimeout(Duration.ofMillis(150));
            RestClient client = productionClient(f);
            f.server.stubFor(get("/slow").willReturn(ok().withFixedDelay(1500)));
            RestClient.ResponseSpec slow = client.get().uri(f.baseUrl() + "/slow").retrieve();
            long start = System.nanoTime();
            assertThatThrownBy(slow::toBodilessEntity).isInstanceOf(ResourceAccessException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        }
    }

    private static RestClient productionClient(OAuthFixture f) {
        var factory = new SharedSecurityConfiguration.Enabled()
                .sharedHttpRequestFactory(f.properties, new OutboundProperties("", "", "", ""));
        return RestClient.builder().requestFactory(factory).build();
    }
}
