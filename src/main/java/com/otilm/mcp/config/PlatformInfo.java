package com.otilm.mcp.config;

import com.otilm.api.model.core.info.CoreInfoResponseDto;
import com.otilm.mcp.client.IlmApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PlatformInfo {

    private static final Logger logger = LoggerFactory.getLogger(PlatformInfo.class);

    private static final int SECRETS_MIN_MAJOR = 2;
    private static final int SECRETS_MIN_MINOR = 17;

    private final String platformVersion;
    private final boolean secretsSupported;

    public PlatformInfo(IlmApiClient ilmApiClient) {
        String version = null;
        boolean supported = false;
        try {
            CoreInfoResponseDto info = ilmApiClient.getInfo();
            version = info.getApp().getVersion();
            supported = isVersionAtLeast(version, SECRETS_MIN_MAJOR, SECRETS_MIN_MINOR);
            logger.info("Connected to ILM platform version {}. Secrets support: {}", version, supported);
        } catch (Exception e) {
            logger.warn("Failed to detect ILM platform version. Secrets tools will be disabled: {}", e.getMessage());
        }
        this.platformVersion = version;
        this.secretsSupported = supported;
    }

    public boolean isSecretsSupported() {
        return secretsSupported;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    static boolean isVersionAtLeast(String version, int minMajor, int minMinor) {
        if (version == null) {
            return false;
        }
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > minMajor || (major == minMajor && minor >= minMinor);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
    }
}
