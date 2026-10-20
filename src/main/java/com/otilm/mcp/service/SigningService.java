package com.otilm.mcp.service;

public interface SigningService {

    String searchSigningProfiles(String filters, Integer itemsPerPage, Integer pageNumber);

    String getSigningProfile(String uuid);

    String searchSigningRecords(String filters, Integer itemsPerPage, Integer pageNumber);

    String getSigningStatistics(String period);

    String searchTspProfiles(String filters, Integer itemsPerPage, Integer pageNumber);
}
