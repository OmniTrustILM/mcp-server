package com.otilm.mcp.config;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class PlatformInfoTest {

    private PlatformInfo createPlatformInfo(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        return new PlatformInfo(apiClient);
    }

    @Test
    void shouldDetectSecretsSupported_when217(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlEqualTo("/v1/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "app": {"name": "CZERTAINLY", "version": "2.17.0"},
                            "db": {"system": "PostgreSQL", "version": "16.2"}
                        }
                        """)));

        PlatformInfo info = createPlatformInfo(wmInfo);

        assertThat(info.isSecretsSupported()).isTrue();
        assertThat(info.getPlatformVersion()).isEqualTo("2.17.0");
    }

    @Test
    void shouldDetectSecretsSupported_whenNewerThan217(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlEqualTo("/v1/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "app": {"name": "CZERTAINLY", "version": "2.18.1"},
                            "db": {"system": "PostgreSQL", "version": "16.2"}
                        }
                        """)));

        PlatformInfo info = createPlatformInfo(wmInfo);

        assertThat(info.isSecretsSupported()).isTrue();
    }

    @Test
    void shouldDetectSecretsNotSupported_whenOlderThan217(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlEqualTo("/v1/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "app": {"name": "CZERTAINLY", "version": "2.16.2"},
                            "db": {"system": "PostgreSQL", "version": "16.2"}
                        }
                        """)));

        PlatformInfo info = createPlatformInfo(wmInfo);

        assertThat(info.isSecretsSupported()).isFalse();
        assertThat(info.getPlatformVersion()).isEqualTo("2.16.2");
    }

    @Test
    void shouldDefaultToNotSupported_whenApiUnreachable(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlEqualTo("/v1/info")).willReturn(aResponse().withStatus(500)));

        PlatformInfo info = createPlatformInfo(wmInfo);

        assertThat(info.isSecretsSupported()).isFalse();
        assertThat(info.getPlatformVersion()).isNull();
    }

    @Test
    void shouldDefaultToNotSupported_whenMalformedVersion(WireMockRuntimeInfo wmInfo) {
        stubFor(get(urlEqualTo("/v1/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "app": {"name": "CZERTAINLY", "version": "unknown"},
                            "db": {"system": "PostgreSQL", "version": "16.2"}
                        }
                        """)));

        PlatformInfo info = createPlatformInfo(wmInfo);

        assertThat(info.isSecretsSupported()).isFalse();
        assertThat(info.getPlatformVersion()).isEqualTo("unknown");
    }
}
