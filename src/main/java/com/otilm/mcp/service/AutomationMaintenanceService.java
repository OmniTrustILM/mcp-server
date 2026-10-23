package com.otilm.mcp.service;

public interface AutomationMaintenanceService {
    String getScopedTriggers(String resource, String objectUuid);

    String attachScopedTrigger(String resource, String objectUuid, String event, String triggerUuid, Integer position);

    String detachScopedTrigger(String resource, String objectUuid, String event, String triggerUuid);

    String reorderScopedTriggers(String resource, String objectUuid, String event, String triggerUuids);

    String getScopedTriggerHistory(String objectUuid, String triggerUuid);

    String getNotificationInstance(String uuid);

    String updateNotificationInstance(String uuid, String configuration);

    String updateNotificationProfile(String uuid, String configuration);

    String updateTrigger(String uuid, String configuration);
}
