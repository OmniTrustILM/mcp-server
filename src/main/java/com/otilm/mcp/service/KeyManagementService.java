package com.otilm.mcp.service;

public interface KeyManagementService {
    String listKeyPairs(String tokenProfileUuid);

    String getKeyItem(String uuid, String itemUuid);

    String getKeyHistory(String uuid, String itemUuid);

    String getKeyGenerationAttributes(String tokenUuid, String profileUuid, String type, String values);

    String getKeyImportAttributes(String tokenUuid, String profileUuid, String type, String values);

    String getKeyExportAttributes(String uuid, String itemUuid, String values);

    String generateKey(String tokenUuid, String profileUuid, String type, String configuration);

    String importKey(String tokenUuid, String profileUuid, String type, String configuration);

    String exportKey(String uuid, String itemUuid, String passphrase, String attributes);

    String updateKey(String uuid, String configuration);

    String renameKeyItem(String uuid, String itemUuid, String name);

    String enableKey(String uuid, String itemUuids);

    String disableKey(String uuid, String itemUuids);

    String updateKeyUsages(String uuid, String usages, String itemUuids);

    String compromiseKey(String uuid, String reason, String itemUuids);

    String destroyKey(String uuid, String itemUuids);

    String deleteKey(String uuid, String itemUuids);

    String disableKeyExport(String uuid, String itemUuid);
}
