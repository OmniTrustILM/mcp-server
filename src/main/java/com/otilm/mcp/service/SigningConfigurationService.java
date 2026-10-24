package com.otilm.mcp.service;

public interface SigningConfigurationService {
    String getSigningSchemes();

    String getSigningProtocols(String workflowType);

    String getSigningCertificates(String workflowType);

    String getSigningOperationAttributes(String certificateUuid);

    String getSigningFormattingAttributes(String connectorUuid, String family, String maxLevel, String values);

    String createSigningProfile(String configuration);

    String updateSigningProfile(String uuid, String configuration);

    String deleteSigningProfile(String uuid);

    String enableSigningProfile(String uuid);

    String disableSigningProfile(String uuid);

    String getSigningTspActivation(String uuid);

    String activateSigningTsp(String uuid, String tspProfileUuid);

    String deactivateSigningTsp(String uuid);
}
