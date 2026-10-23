package com.otilm.mcp.service;

public interface CredentialConfigurationService {
    String listCredentials();

    String getCredential(String uuid);

    String getCredentialAttributes(String connectorUuid, String kind);

    String createCredential(String configuration);

    String updateCredential(String uuid, String configuration);

    String enableCredential(String uuid);

    String disableCredential(String uuid);

    String deleteCredential(String uuid);
}
