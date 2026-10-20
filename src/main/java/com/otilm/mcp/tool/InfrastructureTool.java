package com.otilm.mcp.tool;

import com.otilm.mcp.service.InfrastructureService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class InfrastructureTool {

    private final InfrastructureService infrastructureService;

    public InfrastructureTool(InfrastructureService infrastructureService) {
        this.infrastructureService = infrastructureService;
    }

    @Tool(name = "list_authorities",
            description = "List all authority instances configured in the ILM platform. Returns authority name, UUID, status, connector, and kind for each instance. Use this to see which certificate authorities are available and their current status.",
            resultConverter = PlainTextResult.class)
    public String listAuthorities() {
        return infrastructureService.listAuthorities();
    }

    @Tool(name = "list_ra_profiles",
            description = "List all RA (Registration Authority) profiles configured in the ILM platform. Returns profile name, UUID, enabled status, description, authority instance, and enabled protocols. Use this to see which RA profiles are available for certificate enrollment and management.",
            resultConverter = PlainTextResult.class)
    public String listRaProfiles() {
        return infrastructureService.listRaProfiles();
    }

    @Tool(name = "list_connectors",
            description = "List all connectors configured in the ILM platform. Returns connector name, UUID, status, URL, auth type, and function groups. Use this to see which connectors are available and their connection status.",
            resultConverter = PlainTextResult.class)
    public String listConnectors() {
        return infrastructureService.listConnectors();
    }

    @Tool(name = "list_groups",
            description = "List all groups configured in the ILM platform. Returns group name, UUID, description, and email. Use this to see available groups for organizing certificates and keys.",
            resultConverter = PlainTextResult.class)
    public String listGroups() {
        return infrastructureService.listGroups();
    }

    @Tool(name = "list_entities",
            description = "List entity instances configured in the ILM platform with optional filters. Use get_searchable_fields('entities') to discover available filter fields. Returns entity name, UUID, status, connector, and kind.",
            resultConverter = PlainTextResult.class)
    public String listEntities(@ToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('entities') to see available fields and operators.",
            required = false) String filters) {
        return infrastructureService.listEntities(filters);
    }

    @Tool(name = "list_credentials",
            description = "List all credentials configured in the ILM platform. Returns credential name, UUID, enabled status, kind, and connector. Use this to see which credentials are available for authentication with external systems.",
            resultConverter = PlainTextResult.class)
    public String listCredentials() {
        return infrastructureService.listCredentials();
    }

    @Tool(name = "list_token_instances",
            description = "List all cryptographic token instances configured in the ILM platform. Returns token name, UUID, status, connector, kind, and number of token profiles. Use this to see which cryptographic tokens (e.g., HSMs, software tokens) are available for key management.",
            resultConverter = PlainTextResult.class)
    public String listTokenInstances() {
        return infrastructureService.listTokenInstances();
    }

    @Tool(name = "list_discoveries",
            description = "List certificate discovery tasks in the ILM platform with optional filters. Use get_searchable_fields('discoveries') to discover available filter fields. Returns discovery name, UUID, status, kind, connector, start/end times, and total certificates discovered.",
            resultConverter = PlainTextResult.class)
    public String listDiscoveries(@ToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('discoveries') to see available fields and operators.",
            required = false) String filters) {
        return infrastructureService.listDiscoveries(filters);
    }
}
