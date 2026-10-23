package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class AuthorityInventoryTool {

    private final InfrastructureService infrastructureService;

    public AuthorityInventoryTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_authorities",
            description = "List all authority instances configured in the ILM platform. Returns authority name, UUID, status, connector, and kind for each instance. Use this to see which certificate authorities are available and their current status.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listAuthorities() {
        return infrastructureService.listAuthorities();
    }
}
