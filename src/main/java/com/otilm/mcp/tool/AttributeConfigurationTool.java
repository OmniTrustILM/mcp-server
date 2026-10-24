package com.otilm.mcp.tool;

import com.otilm.mcp.service.AttributeConfigurationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AttributeConfigurationTool {
    private final AttributeConfigurationService service;

    public AttributeConfigurationTool(AttributeConfigurationService service) {
        this.service = service;
    }

    @McpTool(name = "get_custom_attribute", description = "Read custom attribute definition metadata without content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCustomAttribute(@McpToolParam(description = "Attribute UUID") String uuid) {
        return service.getCustomAttribute(uuid);
    }

    @McpTool(name = "create_custom_attribute_definition",
            description = "Create a full custom attribute definition, including options/defaults and protection settings. The existing create_custom_attribute remains available for basic definitions.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createCustomAttributeDefinition(@McpToolParam(
            description = "JSON object: name, label, contentType, description, visible, group, required, readOnly, list, multiSelect, extensibleList, protectionLevel (none/encrypted), content array of {data,reference}, resources array of codes") String configuration) {
        return service.createCustomAttributeDefinition(configuration);
    }

    @McpTool(name = "update_custom_attribute",
            description = "Patch a custom attribute definition while preserving omitted flags, resources and readable defaults. Protected defaults require fresh content. Name/contentType cannot change.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateCustomAttribute(@McpToolParam(description = "Attribute UUID") String uuid, @McpToolParam(
            description = "JSON object: label, description, visible, group, required, readOnly, list, multiSelect, extensibleList, protectionLevel, content array of {data,reference}, resources array") String configuration) {
        return service.updateCustomAttribute(uuid, configuration);
    }

    @McpTool(name = "enable_custom_attribute", description = "Enable a custom attribute.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableCustomAttribute(@McpToolParam(description = "Attribute UUID") String uuid) {
        return service.enableCustomAttribute(uuid);
    }

    @McpTool(name = "disable_custom_attribute", description = "Disable a custom attribute.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableCustomAttribute(@McpToolParam(description = "Attribute UUID") String uuid) {
        return service.disableCustomAttribute(uuid);
    }

    @McpTool(name = "delete_custom_attribute",
            description = "Delete a custom attribute definition using normal platform checks.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteCustomAttribute(@McpToolParam(description = "Attribute UUID") String uuid) {
        return service.deleteCustomAttribute(uuid);
    }

    @McpTool(name = "get_custom_attribute_resources", description = "List the resources that accept custom attributes.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCustomAttributeResources() {
        return service.getCustomAttributeResources();
    }

    @McpTool(name = "get_resource_custom_attributes",
            description = "Read resource custom attribute schemas. Protected and structured values are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getResourceCustomAttributes(
            @McpToolParam(description = "Resource code from get_custom_attribute_resources") String resource) {
        return service.getResourceCustomAttributes(resource);
    }

    @McpTool(name = "set_custom_attribute_resources",
            description = "Replace the full resource association list for a custom attribute. Explicit replace=true required; [] removes all associations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setCustomAttributeResources(@McpToolParam(description = "Attribute UUID") String uuid,
            @McpToolParam(description = "JSON array of resource codes") String resources,
            @McpToolParam(description = "Explicit replacement intent; must be true") boolean replace) {
        return service.setCustomAttributeResources(uuid, resources, replace);
    }

    @McpTool(name = "set_custom_attribute_value",
            description = "Set a nonempty object custom attribute value using its v2/v3 schema without replacing other attributes. Use delete_custom_attribute_value to clear it. Returns identifiers only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setCustomAttributeValue(@McpToolParam(description = "Resource code") String resource,
            @McpToolParam(description = "Object UUID") String objectUuid,
            @McpToolParam(description = "Attribute UUID assigned to this resource") String attributeUuid, @McpToolParam(
                    description = "JSON value using the schema type; selected lists accept arrays; secret uses {secret,protectionLevel}, file uses {content,fileName,mimeType}") String value) {
        return service.setCustomAttributeValue(resource, objectUuid, attributeUuid, value);
    }

    @McpTool(name = "delete_custom_attribute_value", description = "Delete one custom attribute value from an object.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteCustomAttributeValue(@McpToolParam(description = "Resource code") String resource,
            @McpToolParam(description = "Object UUID") String objectUuid,
            @McpToolParam(description = "Attribute UUID") String attributeUuid) {
        return service.deleteCustomAttributeValue(resource, objectUuid, attributeUuid);
    }
}
