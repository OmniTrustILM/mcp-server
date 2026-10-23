package com.otilm.mcp.tool;

import com.otilm.mcp.service.NotificationInstanceInput;
import com.otilm.mcp.service.NotificationProfileInput;
import com.otilm.mcp.service.NotificationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class NotificationTool {

    private final NotificationService notificationService;

    public NotificationTool(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @McpTool(name = "list_notification_instances",
            description = "List ILM notification instances (the configured email, webhook or other channels) with their connector and kind. A notification profile sends through one of them.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listNotificationInstances() {
        return notificationService.listNotificationInstances();
    }

    @McpTool(name = "list_notification_profiles",
            description = "List ILM notification profiles with their recipients, the instance they send through, and whether they also notify in the ILM app. create_trigger takes a profile's UUID to send notifications.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listNotificationProfiles() {
        return notificationService.listNotificationProfiles();
    }

    @McpTool(name = "get_notification_connector_settings",
            description = "Show what a new notification instance needs for a connector and kind: each field with its label, content type, whether it is required, and its default or choices; the fields a connector callback adds once the values they depend on are given (resolving them only reads); and the recipient attributes that can be mapped to custom attributes. list_connectors shows notification connectors (notificationProvider) and their kinds.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getNotificationConnectorSettings(
            @McpToolParam(description = "The UUID of the notification connector") String connectorUuid,
            @McpToolParam(description = "The notification kind, for example EMAIL or WEBHOOK") String kind,
            @McpToolParam(
                    description = "JSON object of field values that other fields depend on, for example {\"data_contentType\":\"JSON\"}",
                    required = false) String attributes) {
        return notificationService.getNotificationConnectorSettings(connectorUuid, kind, attributes);
    }

    @McpTool(name = "create_notification_instance",
            description = "Create a notification instance (an email, webhook or other channel) on a notification connector. get_notification_connector_settings shows the fields the connector asks for; give their values as a JSON object, with code-block fields such as templates as plain text. A field without a value takes the connector's default when it has one, and fields that depend on another value, like a webhook's content template, are fetched from the connector. Recipient attributes can be mapped to custom attributes by name or UUID.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createNotificationInstance(
            @McpToolParam(description = "Name of the notification instance") String name,
            @McpToolParam(description = "The UUID of the notification connector") String connectorUuid,
            @McpToolParam(description = "The notification kind, for example EMAIL or WEBHOOK") String kind,
            @McpToolParam(description = "Description of the notification instance",
                    required = false) String description,
            @McpToolParam(
                    description = "JSON object of field names and values, e.g. {\"data_senderEmailAddress\":\"ilm@example.com\",\"data_emailContentTemplate\":\"<p>A certificate needs attention.</p>\"}",
                    required = false) String attributes,
            @McpToolParam(
                    description = "JSON object of mappable recipient attribute to custom attribute name or UUID, e.g. {\"data_recipientEmailAddress\":\"Contact email\"}",
                    required = false) String attributeMappings) {
        return notificationService
                .createNotificationInstance(new NotificationInstanceInput(name, connectorUuid, kind, description,
                        attributes, attributeMappings));
    }

    @McpTool(name = "create_notification_profile",
            description = "Create a notification profile: who is notified and how. Recipient types: none (the instance decides, as for a webhook), default (the event's usual recipients; for certificates the owner and groups), owner (the object's owner), object (addresses mapped from the object's custom attributes), and user, group or role (the UUIDs given, from list_users, list_groups or list_roles). It sends through a notification instance, in the ILM app (internalNotification), or both. frequency and repetitions only limit certificate_expiring, which repeats every hour for each expiring certificate: at most one notification per frequency (ISO-8601 days and hours, default P1D) and at most repetitions in total for the same certificate. Pass the profile's UUID to create_trigger.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createNotificationProfile(@McpToolParam(description = "Name of the notification profile") String name,
            @McpToolParam(
                    description = "Recipient type: none, default, owner, object, user, group or role") String recipientType,
            @McpToolParam(
                    description = "Comma-separated user, group or role UUIDs, for recipient types user, group and role",
                    required = false) String recipients,
            @McpToolParam(description = "The UUID of the notification instance to send through",
                    required = false) String notificationInstanceUuid,
            @McpToolParam(description = "true to also notify in the ILM app",
                    required = false) Boolean internalNotification,
            @McpToolParam(
                    description = "ISO-8601 duration of days and hours between certificate_expiring notifications for the same certificate, e.g. P1D or PT12H (default P1D)",
                    required = false) String frequency,
            @McpToolParam(description = "At most this many certificate_expiring notifications for the same certificate",
                    required = false) Integer repetitions) {
        return notificationService
                .createNotificationProfile(new NotificationProfileInput(name, recipientType, recipients,
                        notificationInstanceUuid, internalNotification, frequency, repetitions));
    }

    @McpTool(name = "delete_notification_instance",
            description = "Delete a notification instance. ILM refuses while a notification profile sends through it, and the answer names those profiles.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteNotificationInstance(
            @McpToolParam(description = "The UUID of the notification instance") String uuid) {
        return notificationService.deleteNotificationInstance(uuid);
    }

    @McpTool(name = "delete_notification_profile",
            description = "Delete a notification profile. ILM refuses while a trigger sends notifications through it; delete those triggers first with delete_trigger.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteNotificationProfile(
            @McpToolParam(description = "The UUID of the notification profile") String uuid) {
        return notificationService.deleteNotificationProfile(uuid);
    }

    @McpTool(name = "get_notification_profile",
            description = "Explain one notification profile in plain words: who receives it, the instance it sends through, whether it notifies in the ILM app, and how often it repeats certificate_expiring notifications.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getNotificationProfile(
            @McpToolParam(description = "The UUID of the notification profile") String uuid) {
        return notificationService.getNotificationProfile(uuid);
    }
}
