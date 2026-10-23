package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class TokenInventoryTool {

    private final InfrastructureService infrastructureService;

    public TokenInventoryTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_token_instances",
            description = "List all cryptographic token instances configured in the ILM platform. Returns token name, UUID, status, connector, kind, and number of token profiles. Use this to see which cryptographic tokens (e.g., HSMs, software tokens) are available for key management.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listTokenInstances() {
        return infrastructureService.listTokenInstances();
    }
}
