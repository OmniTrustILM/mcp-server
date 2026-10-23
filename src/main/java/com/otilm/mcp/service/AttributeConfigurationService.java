package com.otilm.mcp.service;

public interface AttributeConfigurationService {
    String getCustomAttribute(String uuid);

    String createCustomAttributeDefinition(String configuration);

    String updateCustomAttribute(String uuid, String configuration);

    String enableCustomAttribute(String uuid);

    String disableCustomAttribute(String uuid);

    String deleteCustomAttribute(String uuid);

    String getCustomAttributeResources();

    String getResourceCustomAttributes(String resource);

    String setCustomAttributeResources(String uuid, String resources, boolean replace);

    String setCustomAttributeValue(String resource, String objectUuid, String attributeUuid, String value);

    String deleteCustomAttributeValue(String resource, String objectUuid, String attributeUuid);
}
