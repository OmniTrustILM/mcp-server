package com.otilm.mcp.tool;

import com.otilm.mcp.service.AutomationMaintenanceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AutomationMaintenanceTool {
    private final AutomationMaintenanceService service;

    public AutomationMaintenanceTool(AutomationMaintenanceService service) {
        this.service = service;
    }

    @McpTool(name = "get_scoped_triggers",
            description = "Inspect ordered event-trigger lists for a discovery, RA profile or group.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getScopedTriggers(
            @McpToolParam(description = "Scope resource: discoveries, raProfiles or groups") String resource,
            @McpToolParam(description = "Scoped resource object UUID") String objectUuid) {
        return service.getScopedTriggers(resource, objectUuid);
    }

    @McpTool(name = "attach_scoped_trigger",
            description = "Attach one compatible trigger while preserving other entries/order. Replacing a scoped list rebinds every included association execution identity to the caller, including retained entries.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String attachScopedTrigger(
            @McpToolParam(description = "Scope resource: discoveries, raProfiles or groups") String resource,
            @McpToolParam(description = "Scoped resource object UUID") String objectUuid,
            @McpToolParam(
                    description = "Supported event code; discovery certificate classification uses certificate_discovered, run completion uses discovery_finished") String event,
            @McpToolParam(description = "Trigger UUID") String triggerUuid,
            @McpToolParam(
                    description = "Optional one-based insertion position; default append. Ignore triggers must precede ordinary triggers.",
                    required = false) Integer position) {
        return service.attachScopedTrigger(resource, objectUuid, event, triggerUuid, position);
    }

    @McpTool(name = "detach_scoped_trigger",
            description = "Detach one scoped trigger, retaining other entries/order. The platform rebinds every retained association execution identity to the caller.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String detachScopedTrigger(
            @McpToolParam(description = "Scope resource: discoveries, raProfiles or groups") String resource,
            @McpToolParam(description = "Scoped resource object UUID") String objectUuid,
            @McpToolParam(
                    description = "Supported event code; discovery certificate classification uses certificate_discovered, run completion uses discovery_finished") String event,
            @McpToolParam(description = "Trigger UUID") String triggerUuid) {
        return service.detachScopedTrigger(resource, objectUuid, event, triggerUuid);
    }

    @McpTool(name = "reorder_scoped_triggers",
            description = "Reorder the exact existing membership. This operation rebinds every included association execution identity to the caller. Use targeted attach/detach to change membership.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String reorderScopedTriggers(
            @McpToolParam(description = "Scope resource: discoveries, raProfiles or groups") String resource,
            @McpToolParam(description = "Scoped resource object UUID") String objectUuid,
            @McpToolParam(
                    description = "Supported event code; discovery certificate classification uses certificate_discovered, run completion uses discovery_finished") String event,
            @McpToolParam(
                    description = "Complete ordered JSON array of the current trigger UUIDs") String triggerUuids) {
        return service.reorderScopedTriggers(resource, objectUuid, event, triggerUuids);
    }

    @McpTool(name = "get_scoped_trigger_history",
            description = "Read trigger history/summary for a resource object and optional individual trigger.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getScopedTriggerHistory(@McpToolParam(description = "Scoped resource object UUID") String objectUuid,
            @McpToolParam(description = "Optional trigger UUID; omit for object summary",
                    required = false) String triggerUuid) {
        return service.getScopedTriggerHistory(objectUuid, triggerUuid);
    }

    @McpTool(name = "update_trigger",
            description = "Maintain a whole workflow without replacing its trigger UUID or attachments. Metadata-only changes retain composition. Complete replacement creates new owned parts, retains shared parts, and restores original references on failure. Renaming requires ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTrigger(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object: optional name/description. To replace composition, give replaceComposition=true and all of conditions (existing create_trigger filter array), setFields (existing create_trigger field array), notificationProfileUuid (UUID or null), ignore (boolean). Event/resource stay fixed to preserve attachment compatibility.") String configuration) {
        return service.updateTrigger(uuid, configuration);
    }
}
