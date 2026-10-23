package com.otilm.mcp.service;

public interface OidService {
    String listCustomOids(String filters, Integer pageNumber, Integer itemsPerPage);

    String getCustomOid(String oid);

    String createCustomOid(String configuration);

    String updateCustomOid(String oid, String configuration);

    String deleteCustomOid(String oid);

    String listSystemOids();
}
