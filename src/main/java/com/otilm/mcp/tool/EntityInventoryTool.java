package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EntityInventoryTool {

    private final InfrastructureService infrastructureService;

    public EntityInventoryTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_entities",
            description = "List entity instances configured in the ILM platform with optional filters. Use get_searchable_fields('entities') to discover available filter fields. Returns entity name, UUID, status, connector, and kind.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listEntities(@McpToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('entities') to see available fields and operators.",
            required = false) String filters) {
        return infrastructureService.listEntities(filters);
    }
}
