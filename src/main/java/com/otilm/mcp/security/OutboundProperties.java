package com.otilm.mcp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the connections the shared OAuth client makes. They come from the environment variables ILM Core reads,
 * so they carry no {@code ILM_} prefix.
 */
@ConfigurationProperties("ilm.mcp.outbound")
public record OutboundProperties(String trustedCertificates, String httpProxy, String httpsProxy, String noProxy) {
    @Override
    public String toString() {
        return "OutboundProperties[redacted]";
    }
}
