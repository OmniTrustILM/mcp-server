package com.otilm.mcp.service;

public interface RaProfileService {
    String getRaProfile(String uuid);

    String getRaProfileAttributes(String authorityUuid, String values);

    String createRaProfile(String authorityUuid, String configuration);

    String updateRaProfile(String authorityUuid, String uuid, String configuration);

    String deleteRaProfile(String authorityUuid, String uuid);

    String setRaProfileEnabled(String authorityUuid, String uuid, boolean enabled);

    String updateRaProfileValidation(String authorityUuid, String uuid, String configuration);

    String updateRaProfileRequestAttributes(String authorityUuid, String uuid, String configuration);

    String getRaProfileProtocol(String authorityUuid, String uuid, String protocol);

    String setRaProfileProtocol(String authorityUuid, String uuid, String protocol, String enrollmentUuid,
            String configuration);

    String getRaProfileAssociations(String authorityUuid, String uuid);

    String getAuthorityChain(String authorityUuid, String uuid);
}
