package com.otilm.mcp.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Properties.class);

    // The values are set explicitly so a proxy or trust variable in the environment running the tests cannot leak in.
    @Test
    void bindsEmptyVariablesAsEmptyStrings() {
        runner
                .withPropertyValues("TRUSTED_CERTIFICATES=", "HTTP_PROXY=", "HTTPS_PROXY=", "NO_PROXY=")
                .run(context -> assertThat(context.getBean(OutboundProperties.class))
                        .extracting(OutboundProperties::trustedCertificates, OutboundProperties::httpProxy,
                                OutboundProperties::httpsProxy, OutboundProperties::noProxy)
                        .containsExactly("", "", "", ""));
    }

    @Test
    void bindsTheEnvironmentVariablesOfIlmCore() {
        runner
                .withPropertyValues("TRUSTED_CERTIFICATES=certificates", "HTTP_PROXY=http-proxy",
                        "HTTPS_PROXY=https-proxy", "NO_PROXY=no-proxy")
                .run(context -> assertThat(context.getBean(OutboundProperties.class))
                        .extracting(OutboundProperties::trustedCertificates, OutboundProperties::httpProxy,
                                OutboundProperties::httpsProxy, OutboundProperties::noProxy)
                        .containsExactly("certificates", "http-proxy", "https-proxy", "no-proxy"));
    }

    @Test
    void redactsEveryValueInToString() {
        assertThat(new OutboundProperties("certificates", "http-proxy", "https-proxy", "no-proxy"))
                .hasToString("OutboundProperties[redacted]");
    }

    @EnableConfigurationProperties(OutboundProperties.class)
    static class Properties {
    }
}
