package com.otilm.mcp.tool;

import com.otilm.mcp.service.ConnectorService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ConnectorTool {
    private final ConnectorService service;

    public ConnectorTool(ConnectorService service) {
        this.service = service;
    }

    @McpTool(name = "get_connector",
            description = "Get connector metadata, routing and supported interfaces. Authentication and attribute values are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getConnector(@McpToolParam(description = "Connector UUID") String uuid) {
        return service.getConnector(uuid);
    }

    @McpTool(name = "get_connector_info", description = "Get framework v2 connector identity and software metadata.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getConnectorInfo(@McpToolParam(description = "Connector UUID") String uuid) {
        return service.getConnectorInfo(uuid);
    }

    @McpTool(name = "get_connector_health",
            description = "Check connector health. Returns status without provider error payloads.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getConnectorHealth(@McpToolParam(description = "Connector UUID") String uuid) {
        return service.getConnectorHealth(uuid);
    }

    @McpTool(name = "get_connector_authentication_attributes",
            description = "Get authentication attribute definitions before supplying plain values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getConnectorAuthenticationAttributes(
            @McpToolParam(description = "none, basic, certificate or apiKey") String authType) {
        return service.getConnectorAuthenticationAttributes(authType);
    }

    @McpTool(name = "create_connector", description = "Register a connector. Returns metadata only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createConnector(@McpToolParam(
            description = "JSON object: name, version (v1 or v2), url, authType, authAttributes plain name-to-value object, customAttributes plain object, optional proxyUuid (ILM 2.18+)") String configuration) {
        return service.createConnector(configuration);
    }

    @McpTool(name = "update_connector",
            description = "Replace connector connection settings. Requires fresh authentication, explicit routing, and preserves readable custom attributes. Connector name and framework version cannot be changed.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateConnector(@McpToolParam(description = "Connector UUID") String uuid, @McpToolParam(
            description = "JSON object: url, authType, fresh authAttributes, optional customAttributes; choose proxyUuid or noProxy=true") String configuration) {
        return service.updateConnector(uuid, configuration);
    }

    @McpTool(name = "test_connector_connection",
            description = "Probe a connector without registering it. Provider connection failures omit raw error messages.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String testConnectorConnection(@McpToolParam(
            description = "JSON object: url, authType, fresh authAttributes, optional proxyUuid and existing connector uuid") String configuration) {
        return service.testConnectorConnection(configuration);
    }

    @McpTool(name = "reconnect_connector",
            description = "Reconnect and refresh persisted connector interfaces/status. A failed attempt can persist an offline status.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String reconnectConnector(@McpToolParam(description = "Connector UUID") String uuid) {
        return service.reconnectConnector(uuid);
    }

    @McpTool(name = "approve_connector", description = "Approve a connector waiting for registration approval.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String approveConnector(@McpToolParam(description = "Connector UUID") String uuid) {
        return service.approveConnector(uuid);
    }

    @McpTool(name = "delete_connector",
            description = "Delete a connector using normal dependency checks. Reports dependencies; never forces deletion.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteConnector(@McpToolParam(description = "Connector UUID") String uuid) {
        return service.deleteConnector(uuid);
    }
}
