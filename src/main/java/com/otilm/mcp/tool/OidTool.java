package com.otilm.mcp.tool;

import com.otilm.mcp.service.OidService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class OidTool {
    private final OidService service;

    public OidTool(OidService service) {
        this.service = service;
    }

    @McpTool(name = "list_custom_oids", description = "Search custom OIDs with standard ILM filters and pagination.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listCustomOids(
            @McpToolParam(description = "Optional standard ILM search filters JSON array",
                    required = false) String filters,
            @McpToolParam(description = "Page number starting at 1", required = false) Integer pageNumber,
            @McpToolParam(description = "Positive page size", required = false) Integer itemsPerPage) {
        return service.listCustomOids(filters, pageNumber, itemsPerPage);
    }

    @McpTool(name = "get_custom_oid", description = "Get a custom OID and its category-specific properties.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCustomOid(@McpToolParam(description = "Dotted numeric OID", required = true) String oid) {
        return service.getCustomOid(oid);
    }

    @McpTool(name = "create_custom_oid",
            description = "Create a custom OID. Certificate extensions require ILM 2.19; DER valueSchema requires 2.20.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createCustomOid(@McpToolParam(
            description = "JSON object: oid, displayName, category (generic, extendedKeyUsage, rdnAttributeType, certificateExtension), description, additionalProperties. RDN: code and altCodes; extension: defaultCritical, valueEncoding, optional valueSchema.",
            required = true) String configuration) {
        return service.createCustomOid(configuration);
    }

    @McpTool(name = "update_custom_oid",
            description = "Patch OID display and category-specific properties while retaining omitted fields. Category cannot change. Extension properties require ILM 2.19; valueSchema 2.20.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateCustomOid(@McpToolParam(description = "Dotted numeric OID", required = true) String oid,
            @McpToolParam(description = "JSON object: displayName, description, additionalProperties",
                    required = true) String configuration) {
        return service.updateCustomOid(oid, configuration);
    }

    @McpTool(name = "delete_custom_oid", description = "Delete a custom OID using platform checks.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteCustomOid(@McpToolParam(description = "Dotted numeric OID", required = true) String oid) {
        return service.deleteCustomOid(oid);
    }

    @McpTool(name = "list_system_oids", description = "List built-in system OIDs (ILM 2.19+).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listSystemOids() {
        return service.listSystemOids();
    }
}
