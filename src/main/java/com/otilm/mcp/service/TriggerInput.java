package com.otilm.mcp.service;

/**
 * What a client sends to create a trigger, as given: JSON arrays stay text until {@link TriggerRequests} reads them.
 */
public record TriggerInput(String name, String event, String conditions, String setFields,
        String notificationProfileUuid, Boolean ignore, Boolean attach) {
}
