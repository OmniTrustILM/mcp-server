package com.otilm.mcp.service;

/**
 * What a client sends to create a notification profile, as given.
 */
public record NotificationProfileInput(String name, String recipientType, String recipients,
        String notificationInstanceUuid, Boolean internalNotification, String frequency, Integer repetitions) {
}
