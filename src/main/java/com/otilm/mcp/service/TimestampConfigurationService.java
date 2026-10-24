package com.otilm.mcp.service;

public interface TimestampConfigurationService {
    String getTspProfile(String uuid);

    String createTspProfile(String configuration);

    String updateTspProfile(String uuid, String configuration);

    String deleteTspProfile(String uuid);

    String enableTspProfile(String uuid);

    String disableTspProfile(String uuid);

    String listTspBasicCredentials(String tspUuid);

    String getTspBasicCredential(String tspUuid, String uuid);

    String createTspBasicCredential(String tspUuid, String configuration);

    String updateTspBasicCredential(String tspUuid, String uuid, String configuration);

    String deleteTspBasicCredential(String tspUuid, String uuid);

    String searchTimeQualityConfigurations(String filters, Integer itemsPerPage, Integer pageNumber);

    String getTimeQualityConfiguration(String uuid);

    String getTimeQualitySigningProfiles(String uuid);

    String createTimeQualityConfiguration(String configuration);

    String updateTimeQualityConfiguration(String uuid, String configuration);

    String deleteTimeQualityConfiguration(String uuid);
}
