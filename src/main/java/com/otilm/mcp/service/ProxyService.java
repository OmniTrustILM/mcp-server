package com.otilm.mcp.service;

public interface ProxyService {
    String listProxies();

    String getProxy(String uuid);

    String createProxy(String configuration);

    String updateProxy(String uuid, String configuration);

    String deleteProxy(String uuid);

    String getProxyInstallationInstructions(String uuid);
}
