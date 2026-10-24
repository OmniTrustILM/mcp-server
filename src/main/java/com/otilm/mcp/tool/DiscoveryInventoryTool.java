package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryInventoryTool {

    private final InfrastructureService infrastructureService;

    public DiscoveryInventoryTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_discoveries",
            description = "List certificate discovery tasks in the ILM platform with optional filters. Use get_searchable_fields('discoveries') to discover available filter fields. Returns discovery name, UUID, status, kind, connector, start/end times, and total certificates discovered.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listDiscoveries(@McpToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('discoveries') to see available fields and operators.",
            required = false) String filters) {
        return infrastructureService.listDiscoveries(filters);
    }
}
