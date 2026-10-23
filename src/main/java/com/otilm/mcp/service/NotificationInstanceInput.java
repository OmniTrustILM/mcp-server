package com.otilm.mcp.service;

/**
 * What a client sends to create a notification instance, as given: JSON objects stay text until they are read.
 */
public record NotificationInstanceInput(String name, String connectorUuid, String kind, String description,
        String attributes, String attributeMappings) {
}
