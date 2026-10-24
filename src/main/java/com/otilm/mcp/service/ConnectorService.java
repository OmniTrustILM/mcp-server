package com.otilm.mcp.service;

public interface ConnectorService {
    String getConnector(String uuid);

    String getConnectorInfo(String uuid);

    String getConnectorHealth(String uuid);

    String getConnectorAuthenticationAttributes(String authType);

    String createConnector(String configuration);

    String updateConnector(String uuid, String configuration);

    String testConnectorConnection(String configuration);

    String reconnectConnector(String uuid);

    String approveConnector(String uuid);

    String deleteConnector(String uuid);
}
