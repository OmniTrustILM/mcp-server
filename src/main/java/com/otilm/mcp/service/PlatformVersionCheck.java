package com.otilm.mcp.service;

import com.otilm.api.model.core.info.CoreInfoResponseDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tells a tool whether the connected ILM is too old for its feature. The version is read with the caller's credentials
 * on every call; when it cannot be read, the tool proceeds and lets ILM answer.
 */
@Component
public class PlatformVersionCheck {

    private static final Logger logger = LoggerFactory.getLogger(PlatformVersionCheck.class);

    private final IlmApiClient ilmApiClient;

    public PlatformVersionCheck(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    public String run(IlmFeature feature, String action, Supplier<String> body) {
        return unsupported(feature).orElseGet(() -> ToolResponses.run(action, body));
    }

    public Optional<String> unsupported(IlmFeature feature) {
        String version;
        try {
            CoreInfoResponseDto info = ilmApiClient.getInfo();
            version = info == null || info.getApp() == null ? null : info.getApp().getVersion();
        } catch (RuntimeException e) {
            logger.debug("Could not read the ILM version: {}", e.getMessage());
            return Optional.empty();
        }
        return PlatformVersion
                .parse(version)
                .filter(connected -> !connected.isAtLeast(feature.minimum()))
                .map(connected -> "%s requires ILM %s or later; the connected ILM is %s."
                        .formatted(feature.label(), feature.minimum(), version));
    }
}
