package com.otilm.mcp.service;

public interface MetadataService {
    String listGlobalMetadata();

    String getGlobalMetadata(String uuid);

    String createGlobalMetadata(String configuration);

    String updateGlobalMetadata(String uuid, String configuration);

    String deleteGlobalMetadata(String uuid);

    String listConnectorMetadata(String connectorUuid);

    String promoteConnectorMetadata(String connectorUuid, String metadataUuid);
}
