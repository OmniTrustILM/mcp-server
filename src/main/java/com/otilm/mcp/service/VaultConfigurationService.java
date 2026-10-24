package com.otilm.mcp.service;

public interface VaultConfigurationService {
    String getVaultInstance(String uuid);

    String getVaultAttributes(String connectorUuid, String interfaceUuid, String values);

    String getVaultProfile(String vaultUuid, String profileUuid);

    String getVaultProfileAttributes(String vaultUuid, String values);

    String getSecretAttributes(String vaultUuid, String profileUuid, String secretType, String values);

    String createVaultInstance(String configuration);

    String updateVaultInstance(String uuid, String configuration);

    String deleteVaultInstance(String uuid);

    String createVaultProfile(String vaultUuid, String configuration);

    String updateVaultProfile(String vaultUuid, String profileUuid, String configuration);

    String enableVaultProfile(String vaultUuid, String profileUuid);

    String disableVaultProfile(String vaultUuid, String profileUuid);

    String deleteVaultProfile(String vaultUuid, String profileUuid);
}
