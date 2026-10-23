package com.otilm.mcp.tool;

import com.otilm.mcp.service.AutomationMaintenanceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class NotificationMaintenanceTool {
    private final AutomationMaintenanceService service;

    public NotificationMaintenanceTool(AutomationMaintenanceService service) {
        this.service = service;
    }

    @McpTool(name = "get_notification_instance",
            description = "Inspect notification instance metadata without provider attribute or credential values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getNotificationInstance(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getNotificationInstance(uuid);
    }

    @McpTool(name = "update_notification_instance",
            description = "Update notification instance description, provider values or recipient mappings. Omitted readable values/mappings are retained; populated protected values require fresh input.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateNotificationInstance(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(
                    description = "JSON object: optional description, attributes (plain name-to-value object), attributeMappings (plain mapping-name to custom attribute name/UUID object).") String configuration) {
        return service.updateNotificationInstance(uuid, configuration);
    }

    @McpTool(name = "update_notification_profile",
            description = "Update profile composition while retaining omitted recipients/transport/settings. Reports the actual version; ILM may create a new one. eventDataCategories requires ILM 2.20+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateNotificationProfile(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(
                    description = "JSON object: description, recipientType, recipientUuids (UUID array), notificationInstanceUuid (or null), internalNotification, frequency (positive ISO-8601 days/hours), repetitions, eventDataCategories [customAttributes/metadata/associations/objectContent]. Changing recipientType requires an explicit recipientUuids list.") String configuration) {
        return service.updateNotificationProfile(uuid, configuration);
    }

}
