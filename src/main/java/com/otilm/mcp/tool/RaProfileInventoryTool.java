package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class RaProfileInventoryTool {

    private final InfrastructureService infrastructureService;

    public RaProfileInventoryTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_ra_profiles",
            description = "List all RA (Registration Authority) profiles configured in the ILM platform. Returns profile name, UUID, enabled status, description, authority instance, and enabled protocols. Use this to see which RA profiles are available for certificate enrollment and management.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listRaProfiles() {
        return infrastructureService.listRaProfiles();
    }
}
