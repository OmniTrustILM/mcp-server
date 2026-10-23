package com.otilm.mcp.service;

public interface SettingsService {
    String getPlatformSettings(String section);

    String updatePlatformSettings(String section, String settings);

    String getOAuthProviderSettings(String providerName);

    String updateOAuthProviderSettings(String providerName, String settings);

    String replaceOAuthProviders(String settings);

    String removeOAuthProvider(String providerName);
}
