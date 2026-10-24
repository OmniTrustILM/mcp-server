package com.otilm.mcp.tool;

import com.otilm.mcp.service.MetadataService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class MetadataTool {
    private final MetadataService service;

    public MetadataTool(MetadataService service) {
        this.service = service;
    }

    @McpTool(name = "list_global_metadata", description = "List global metadata definitions without attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listGlobalMetadata() {
        return service.listGlobalMetadata();
    }

    @McpTool(name = "get_global_metadata", description = "Get global metadata definition properties.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getGlobalMetadata(@McpToolParam(description = "Metadata UUID", required = true) String uuid) {
        return service.getGlobalMetadata(uuid);
    }

    @McpTool(name = "create_global_metadata", description = "Create a global metadata definition.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createGlobalMetadata(@McpToolParam(
            description = "JSON object: name, label, contentType, optional description, visible and group",
            required = true) String configuration) {
        return service.createGlobalMetadata(configuration);
    }

    @McpTool(name = "update_global_metadata",
            description = "Patch global metadata display properties while retaining omitted values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateGlobalMetadata(@McpToolParam(description = "Metadata UUID", required = true) String uuid,
            @McpToolParam(
                    description = "JSON object: label, description, visible, group. Name and contentType cannot be changed.",
                    required = true) String configuration) {
        return service.updateGlobalMetadata(uuid, configuration);
    }

    @McpTool(name = "delete_global_metadata",
            description = "Delete a global metadata definition using normal platform checks.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteGlobalMetadata(@McpToolParam(description = "Metadata UUID", required = true) String uuid) {
        return service.deleteGlobalMetadata(uuid);
    }

    @McpTool(name = "list_connector_metadata",
            description = "List connector metadata definitions available for promotion.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listConnectorMetadata(
            @McpToolParam(description = "Optional connector UUID", required = false) String connectorUuid) {
        return service.listConnectorMetadata(connectorUuid);
    }

    @McpTool(name = "promote_connector_metadata",
            description = "Promote one connector metadata definition into global metadata.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String promoteConnectorMetadata(
            @McpToolParam(description = "Connector UUID", required = true) String connectorUuid,
            @McpToolParam(description = "Connector metadata UUID", required = true) String metadataUuid) {
        return service.promoteConnectorMetadata(connectorUuid, metadataUuid);
    }
}
