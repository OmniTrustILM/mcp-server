package com.otilm.mcp.tool;

import com.otilm.mcp.service.AttributeService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class AttributeTool {

    private final AttributeService attributeService;

    public AttributeTool(AttributeService attributeService) {
        this.attributeService = attributeService;
    }

    @McpTool(name = "list_custom_attributes",
            description = "List the custom attributes in ILM with their label, content type, the resources they apply to, and the field identifier that conditions and create_trigger setFields use for them.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listCustomAttributes() {
        return attributeService.listCustomAttributes();
    }

    @McpTool(name = "create_custom_attribute",
            description = "Create an enabled custom attribute that can be set on objects of the given resources, for example as a target that create_trigger fills in.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createCustomAttribute(@McpToolParam(description = "Name of the attribute") String name,
            @McpToolParam(description = "Label shown to users") String label,
            @McpToolParam(
                    description = "Content type. One of: string, text, integer, boolean, float, date, time, datetime, secret, file, credential, codeblock, object") String contentType,
            @McpToolParam(
                    description = "Comma-separated resource codes the attribute applies to, e.g. certificates, keys") String resources,
            @McpToolParam(description = "Description of the attribute", required = false) String description) {
        return attributeService.createCustomAttribute(name, label, contentType, resources, description);
    }
}
