package com.otilm.mcp.service;

public interface DiscoveryManagementService {
    String getDiscovery(String uuid);

    String getDiscoveryCertificates(String uuid, Boolean newlyDiscovered, int page, int size);

    String getDiscoveryItems(String uuid, String resource, Boolean newlyDiscovered, int page, int size);

    String getDiscoveryMessages(String uuid, int page, int size);

    String listDiscoveryResources(String connectorUuid);

    String getDiscoveryAttributes(String connectorUuid, String kind, String interfaceUuid, String resource,
            String values);

    String startDiscovery(String configuration);

    String scheduleDiscovery(String jobName, String cronExpression, boolean oneTime, String configuration);

    String controlDiscovery(String uuid, String action);

    String deleteDiscovery(String uuid);
}
