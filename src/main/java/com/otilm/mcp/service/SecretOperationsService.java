package com.otilm.mcp.service;

public interface SecretOperationsService {
    String createSecret(String vaultUuid, String profileUuid, String configuration);

    String updateSecret(String uuid, String vaultUuid, String configuration);

    String enableSecret(String uuid);

    String disableSecret(String uuid);

    String setSecretGroups(String uuid, String groupUuids);

    String setSecretOwner(String uuid, String ownerUuid);

    String changeSecretSourceVaultProfile(String uuid, String vaultUuid, String profileUuid, String attributes);

    String addSecretSyncVaultProfile(String uuid, String vaultUuid, String profileUuid, String attributes);

    String removeSecretSyncVaultProfile(String uuid, String profileUuid);

    String removeSecretSyncVaultProfileContent(String uuid, String profileUuid);

    String deleteSecret(String uuid);

    String deleteSecretFromVaults(String uuid);

    String getSecretContent(String uuid);
}
