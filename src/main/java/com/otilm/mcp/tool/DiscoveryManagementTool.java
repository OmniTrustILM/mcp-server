package com.otilm.mcp.tool;

import com.otilm.mcp.service.DiscoveryManagementService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryManagementTool {
    private final DiscoveryManagementService service;

    public DiscoveryManagementTool(DiscoveryManagementService service) {
        this.service = service;
    }

    @McpTool(name = "get_discovery",
            description = "Inspect discovery run state and processing counts without provider/secret payloads. Existing list_discoveries searches runs.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getDiscovery(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getDiscovery(uuid);
    }

    @McpTool(name = "get_discovery_certificates",
            description = "List discovery certificate results with one-based pagination and optional newly-discovered filter.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getDiscoveryCertificates(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "Optional true for only new certificates; false for existing",
                    required = false) Boolean newlyDiscovered,
            @McpToolParam(description = "Discovery page number, starting at 1") int page,
            @McpToolParam(description = "Items per page, between 1 and 1000") int size) {
        return service.getDiscoveryCertificates(uuid, newlyDiscovered, page, size);
    }

    @McpTool(name = "get_discovery_items",
            description = "ILM 2.20+: inspect staged discovery item metadata and processing state without item content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getDiscoveryItems(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "Optional resource code such as certificates or keys",
                    required = false) String resource,
            @McpToolParam(description = "Optional new-item filter", required = false) Boolean newlyDiscovered,
            @McpToolParam(description = "Discovery page number, starting at 1") int page,
            @McpToolParam(description = "Items per page, between 1 and 1000") int size) {
        return service.getDiscoveryItems(uuid, resource, newlyDiscovered, page, size);
    }

    @McpTool(name = "get_discovery_messages",
            description = "ILM 2.20+: list run message metadata/codes/occurrence counts. Messages alone do not imply that the run failed.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getDiscoveryMessages(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "Discovery page number, starting at 1") int page,
            @McpToolParam(description = "Items per page, between 1 and 1000") int size) {
        return service.getDiscoveryMessages(uuid, page, size);
    }

    @McpTool(name = "list_discovery_resources",
            description = "ILM 2.20+: ask a discovery connector which resource types it supports.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listDiscoveryResources(@McpToolParam(description = "Discovery connector UUID") String connectorUuid) {
        return service.listDiscoveryResources(connectorUuid);
    }

    @McpTool(name = "get_discovery_attributes",
            description = "Discover provider settings. Supply kind for legacy discovery schemas. ILM 2.20+ resource discovery uses omitted kind and optional resource; interfaceUuid identifies the v2 callback scope. Values are plain attribute-name to value JSON.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getDiscoveryAttributes(@McpToolParam(description = "Discovery connector UUID") String connectorUuid,
            @McpToolParam(description = "Legacy discovery kind, omitted for resource discovery",
                    required = false) String kind,
            @McpToolParam(description = "Optional discovery interface UUID; required for v2 callbacks",
                    required = false) String interfaceUuid,
            @McpToolParam(description = "Optional resource code for per-resource settings",
                    required = false) String resource,
            @McpToolParam(description = "Optional plain attribute values JSON for callback dependencies",
                    required = false) String values) {
        return service.getDiscoveryAttributes(connectorUuid, kind, interfaceUuid, resource, values);
    }

    @McpTool(name = "start_discovery",
            description = "Start a new discovery run after schema/callback validation. Resource discovery/interfaceUuid require ILM 2.20+. Certificate classification triggers use certificate_discovered, separate from discovery_finished run events. Reports acceptance/current state.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String startDiscovery(@McpToolParam(
            description = "JSON object: name, connectorUuid, legacy kind or ILM 2.20+ nonempty resources array with optional interfaceUuid and resourceAttributes {resource: plain values object}; attributes/customAttributes are plain name-to-value objects. Optional triggers is the ordered certificate_discovered trigger UUID list.") String configuration) {
        return service.startDiscovery(configuration);
    }

    @McpTool(name = "schedule_discovery",
            description = "Schedule a validated discovery request as a platform job. Existing scheduler tools inspect/update job cron/state/history; later cron edits retain this stored provider request.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String scheduleDiscovery(@McpToolParam(description = "Scheduled job name") String jobName,
            @McpToolParam(description = "Quartz cron expression") String cronExpression,
            @McpToolParam(description = "True for a single scheduled execution") boolean oneTime, @McpToolParam(
                    description = "JSON object: name, connectorUuid, legacy kind or ILM 2.20+ nonempty resources array with optional interfaceUuid and resourceAttributes {resource: plain values object}; attributes/customAttributes are plain name-to-value objects. Optional triggers is the ordered certificate_discovered trigger UUID list.") String configuration) {
        return service.scheduleDiscovery(jobName, cronExpression, oneTime, configuration);
    }

    @McpTool(name = "stop_discovery",
            description = "ILM 2.20+: request stop of a discovery run when provider/run state allows it. Reports actual run state; acceptance does not imply completion.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String stopDiscovery(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.controlDiscovery(uuid, "stop");
    }

    @McpTool(name = "resume_discovery",
            description = "ILM 2.20+: request resume of a discovery run when provider/run state allows it. Reports actual run state; acceptance does not imply completion.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String resumeDiscovery(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.controlDiscovery(uuid, "resume");
    }

    @McpTool(name = "cancel_discovery",
            description = "ILM 2.20+: request cancel of a discovery run when provider/run state allows it. Reports actual run state; acceptance does not imply completion.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String cancelDiscovery(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.controlDiscovery(uuid, "cancel");
    }

    @McpTool(name = "delete_discovery",
            description = "Delete a discovery run from inventory without forced or bulk fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteDiscovery(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteDiscovery(uuid);
    }
}
