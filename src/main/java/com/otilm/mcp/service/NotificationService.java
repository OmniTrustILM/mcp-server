package com.otilm.mcp.service;

public interface NotificationService {

    String listNotificationInstances();

    String listNotificationProfiles();

    String getNotificationProfile(String uuid);

    String getNotificationConnectorSettings(String connectorUuid, String kind, String attributes);

    String createNotificationInstance(NotificationInstanceInput input);

    String createNotificationProfile(NotificationProfileInput input);

    String deleteNotificationInstance(String uuid);

    String deleteNotificationProfile(String uuid);
}
