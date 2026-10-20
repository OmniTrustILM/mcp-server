package com.otilm.mcp.service;

public interface CbomService {

    String searchCboms(String filters, Integer itemsPerPage, Integer pageNumber);

    String getCbom(String uuid);

    String getCbomVersions(String uuid);

    String searchCbomAssets(String uuid, String filters, Integer itemsPerPage, Integer pageNumber);
}
