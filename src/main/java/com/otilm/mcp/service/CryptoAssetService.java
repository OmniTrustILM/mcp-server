package com.otilm.mcp.service;

public interface CryptoAssetService {

    String getStatistics();

    String searchCryptoAssets(String filters, Integer itemsPerPage, Integer pageNumber);

    String getCryptoAsset(String uuid);

    String explainPqcVerdict(String uuid);
}
