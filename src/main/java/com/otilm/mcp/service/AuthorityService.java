package com.otilm.mcp.service;

public interface AuthorityService {
    String getAuthority(String uuid);

    String getAuthorityAttributes(String connectorUuid, String interfaceUuid, String kind, String values);

    String createAuthority(String configuration);

    String updateAuthority(String uuid, String configuration);

    String deleteAuthority(String uuid);

    String getAuthorityChoices(String uuid, String endEntityProfileId);
}
