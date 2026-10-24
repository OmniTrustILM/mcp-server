package com.otilm.mcp.tool;

import com.otilm.mcp.service.EntityLocationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EntityTool {
    private final EntityLocationService service;

    public EntityTool(EntityLocationService service) {
        this.service = service;
    }

    @McpTool(name = "get_entity",
            description = "Read entity metadata and connector reference; provider attributes and secrets omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getEntity(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.getEntity(uuid);
    }

    @McpTool(name = "get_entity_attributes",
            description = "Discover entity provider schema and callback dependencies for a connector kind.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getEntityAttributes(@McpToolParam(description = "Provider connector UUID.") String connectorUuid,
            @McpToolParam(description = "Entity provider kind offered by the connector.") String kind,
            @McpToolParam(
                    description = "Optional JSON object of schema attribute names and values for callback resolution.",
                    required = false) String values) {
        return service.getEntityAttributes(connectorUuid, kind, values);
    }

    @McpTool(name = "create_entity",
            description = "Create entity. Configuration: name, connectorUuid and kind required; attributes and customAttributes as name-value objects.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createEntity(@McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.createEntity(configuration);
    }

    @McpTool(name = "update_entity",
            description = "Update entity attributes and customAttributes as name-value objects. Name, connectorUuid and kind are immutable. Preserve omitted readable values; protected values require fresh input.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateEntity(@McpToolParam(description = "Object UUID.") String uuid, @McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.updateEntity(uuid, configuration);
    }

    @McpTool(name = "delete_entity",
            description = "Delete entity through normal dependency checks; requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteEntity(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.deleteEntity(uuid);
    }

}
