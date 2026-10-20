package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.otilm.mcp.IlmTestSupport.stubIlmVersion;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class PlatformVersionCheckTest {

    private PlatformVersionCheck versionCheck;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        versionCheck = new PlatformVersionCheck(IlmTestSupport.client(wmInfo));
    }

    @Test
    void refusesFeatureNewerThanConnectedIlm() {
        stubIlmVersion("2.18.3");

        assertThat(versionCheck.unsupported(IlmFeature.CRYPTO_ASSETS))
                .contains("Cryptographic asset inventory requires ILM 2.20 or later; the connected ILM is 2.18.3.");
    }

    @Test
    void allowsFeatureOnMinimumVersion() {
        stubIlmVersion("2.20.0");

        assertThat(versionCheck.unsupported(IlmFeature.CRYPTO_ASSETS)).isEmpty();
    }

    @Test
    void allowsFeatureWhenVersionCannotBeRead() {
        stubFor(get(urlEqualTo("/v1/info")).willReturn(unauthorized()));

        assertThat(versionCheck.unsupported(IlmFeature.SIGNING_RECORDS)).isEmpty();
    }

    @Test
    void allowsFeatureWhenVersionIsNotNumeric() {
        stubIlmVersion("unknown");

        assertThat(versionCheck.unsupported(IlmFeature.SIGNING_PROFILES)).isEmpty();
    }

    @Test
    void runRefusesOlderIlmWithoutRunningTheBody() {
        stubIlmVersion("2.18.3");
        AtomicBoolean ran = new AtomicBoolean();

        String result = versionCheck.run(IlmFeature.SIGNING_RECORDS, "searching signing records", () -> {
            ran.set(true);
            return "records";
        });

        assertThat(result).isEqualTo("Signing records requires ILM 2.19 or later; the connected ILM is 2.18.3.");
        assertThat(ran).isFalse();
    }

    @Test
    void runAnswersWithTheBodyOnSupportedIlm() {
        stubIlmVersion("2.19.0");

        assertThat(versionCheck.run(IlmFeature.SIGNING_RECORDS, "searching signing records", () -> "records"))
                .isEqualTo("records");
    }
}
