package com.otilm.mcp.service;

public interface TokenService {
    String getTokenInstance(String uuid);

    String getTokenInstanceAttributes(String connectorUuid, String kind, String values);

    String createTokenInstance(String configuration);

    String updateTokenInstance(String uuid, String configuration);

    String deleteTokenInstance(String uuid);

    String getTokenActivationAttributes(String uuid, String values);

    String activateTokenInstance(String uuid, String attributes);

    String deactivateTokenInstance(String uuid);

    String refreshTokenStatus(String uuid);

    String syncTokenKeys(String uuid);

    String listTokenProfiles();

    String getTokenProfile(String tokenUuid, String profileUuid);

    String getTokenProfileAttributes(String tokenUuid, String values);

    String createTokenProfile(String tokenUuid, String configuration);

    String updateTokenProfile(String tokenUuid, String profileUuid, String configuration);

    String deleteTokenProfile(String profileUuid);

    String enableTokenProfile(String tokenUuid, String profileUuid);

    String disableTokenProfile(String tokenUuid, String profileUuid);

    String updateTokenProfileUsages(String tokenUuid, String profileUuid, String usages);

    String getSupportedKeyTypes(String tokenUuid, String profileUuid);

    String getTokenProfileCapabilities(String tokenUuid, String profileUuid);

    String getTokenProfileCompliance(String profileUuid);

    String associateTokenProfileCompliance(String profileUuid, String complianceUuid);

    String detachTokenProfileCompliance(String profileUuid, String complianceUuid);
}
