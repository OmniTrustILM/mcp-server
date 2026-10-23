package com.otilm.mcp.service;

public interface EnrollmentService {
    String listProfiles(String protocol);

    String getProfile(String protocol, String uuid);

    String createProfile(String protocol, String configuration);

    String updateProfile(String protocol, String uuid, String configuration);

    String deleteProfile(String protocol, String uuid);

    String setProfileEnabled(String protocol, String uuid, boolean enabled);

    String setDefaultRaProfile(String protocol, String uuid, String raProfileUuid);

    String getEnrollmentCertificateChoices(String protocol);

    String generateAcmeEabKey();
}
