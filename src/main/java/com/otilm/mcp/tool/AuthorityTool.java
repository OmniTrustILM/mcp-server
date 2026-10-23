package com.otilm.mcp.tool;

import com.otilm.mcp.service.AuthorityService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AuthorityTool {
    private final AuthorityService service;

    public AuthorityTool(AuthorityService service) {
        this.service = service;
    }

    @McpTool(name = "get_authority",
            description = "Inspect authority instance metadata and provider identity without attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAuthority(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getAuthority(uuid);
    }

    @McpTool(name = "get_authority_attributes",
            description = "Discover authority provider fields/defaults and resolve callbacks from supplied values. interfaceUuid selection requires ILM 2.19 or later.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAuthorityAttributes(@McpToolParam(description = "Authority connector UUID") String connectorUuid,
            @McpToolParam(description = "Optional authority interface UUID; requires ILM 2.19",
                    required = false) String interfaceUuid,
            @McpToolParam(
                    description = "Authority provider kind; required for connector callbacks when interfaceUuid is omitted",
                    required = false) String kind,
            @McpToolParam(description = "Optional plain attribute-name to value JSON object for callback dependencies",
                    required = false) String values) {
        return service.getAuthorityAttributes(connectorUuid, interfaceUuid, kind, values);
    }

    @McpTool(name = "create_authority",
            description = "Create an authority using provider schema validation. JSON fields: name, connectorUuid, kind, optional interfaceUuid (ILM 2.19+), attributes and customAttributes.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createAuthority(@McpToolParam(
            description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.createAuthority(configuration);
    }

    @McpTool(name = "update_authority",
            description = "Replace authority provider/custom attributes. Omitted readable values are retained; protected populated values require fresh replacement. JSON fields: attributes, customAttributes.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateAuthority(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.updateAuthority(uuid, configuration);
    }

    @McpTool(name = "delete_authority",
            description = "Delete one authority; ILM dependency checks apply without forced fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteAuthority(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteAuthority(uuid);
    }

    @McpTool(name = "get_authority_choices",
            description = "List authority end-entity profiles, or certificate profiles and CAs for one end-entity profile.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAuthorityChoices(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "Optional end-entity profile ID from the first call",
                    required = false) String endEntityProfileId) {
        return service.getAuthorityChoices(uuid, endEntityProfileId);
    }
}
