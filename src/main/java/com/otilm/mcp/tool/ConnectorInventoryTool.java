package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class ConnectorInventoryTool {

    private final InfrastructureService infrastructureService;

    public ConnectorInventoryTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_connectors",
            description = "List all connectors configured in the ILM platform. Returns connector name, UUID, status, URL, auth type, and function groups. Use this to see which connectors are available and their connection status.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listConnectors() {
        return infrastructureService.listConnectors();
    }
}
