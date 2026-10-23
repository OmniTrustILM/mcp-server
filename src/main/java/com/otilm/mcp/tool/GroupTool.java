package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GroupTool {

    private final InfrastructureService infrastructureService;

    public GroupTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @McpTool(name = "list_groups",
            description = "List all groups configured in the ILM platform. Returns group name, UUID, description, and email. Use this to see available groups for organizing certificates and keys.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listGroups() {
        return infrastructureService.listGroups();
    }

    @McpTool(name = "create_group",
            description = "Create a group for organizing certificates and keys, for example as a target that create_trigger puts certificates into.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createGroup(@McpToolParam(description = "Name of the group") String name,
            @McpToolParam(description = "Description of the group", required = false) String description,
            @McpToolParam(description = "Contact email of the group", required = false) String email) {
        return infrastructureService.createGroup(name, description, email);
    }
}
