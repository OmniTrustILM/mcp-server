package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class ToolResponsesTest {

    private static final String CERTIFICATE_PATH = "/v1/certificates/0f1e2d3c-4b5a-4968-8776-5a4b3c2d1e0f";

    private IlmApiClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        client = IlmTestSupport.client(wmInfo);
    }

    private String retrieveCertificate() {
        return ToolResponses
                .run("retrieving certificate",
                        () -> client.getCertificate("0f1e2d3c-4b5a-4968-8776-5a4b3c2d1e0f").getCommonName());
    }

    @Test
    void reportsIlmErrorMessage() {
        stubFor(get(urlEqualTo(CERTIFICATE_PATH))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"Certificate not found\"}")));

        assertThat(retrieveCertificate())
                .isEqualTo("Error retrieving certificate: ILM answered 404 Not Found: Certificate not found");
    }

    @Test
    void reportsIlmValidationMessages() {
        stubFor(get(urlEqualTo(CERTIFICATE_PATH))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[\"Unknown filter field\", \"Unsupported condition\"]")));

        assertThat(retrieveCertificate())
                .isEqualTo(
                        "Error retrieving certificate: ILM answered 422 Unprocessable Content: Unknown filter field; Unsupported condition");
    }

    @Test
    void reportsStatusWhenBodyIsEmpty() {
        stubFor(get(urlEqualTo(CERTIFICATE_PATH)).willReturn(aResponse().withStatus(500)));

        assertThat(retrieveCertificate())
                .isEqualTo("Error retrieving certificate: ILM answered 500 Internal Server Error");
    }

    @Test
    void returnsInvalidArgumentMessageAsIs() {
        assertThat(ToolResponses.run("searching certificates", () -> {
            throw new IllegalArgumentException("Invalid filters JSON");
        })).isEqualTo("Invalid filters JSON");
    }

    @Test
    void reportsOtherFailures() {
        assertThat(ToolResponses.run("listing groups", () -> {
            throw new IllegalStateException("connection refused");
        })).isEqualTo("Error listing groups: connection refused");
    }

    @Test
    void returnsBodyResult() {
        assertThat(ToolResponses.run("listing groups", () -> "Groups (0)")).isEqualTo("Groups (0)");
    }
}
