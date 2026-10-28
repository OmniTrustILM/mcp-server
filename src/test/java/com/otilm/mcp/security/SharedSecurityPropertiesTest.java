package com.otilm.mcp.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SharedSecurityPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Properties.class);

    @Test
    void keepsHttpBackchannelOffWithoutTheEnvironmentVariable() {
        runner
                .run(context -> assertThat(context.getBean(SharedSecurityProperties.class).isAllowHttpBackchannel())
                        .isFalse());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bindsHttpBackchannelFromTheEnvironmentVariable(boolean value) {
        runner
                .withPropertyValues("ILM_MCP_SECURITY_ALLOW_HTTP_BACKCHANNEL=" + value)
                .run(context -> assertThat(context.getBean(SharedSecurityProperties.class).isAllowHttpBackchannel())
                        .isEqualTo(value));
    }

    @EnableConfigurationProperties(SharedSecurityProperties.class)
    static class Properties {
    }
}
