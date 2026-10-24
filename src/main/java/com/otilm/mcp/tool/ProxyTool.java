package com.otilm.mcp.tool;

import com.otilm.mcp.service.ProxyService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ProxyTool {
    private final ProxyService service;

    public ProxyTool(ProxyService service) {
        this.service = service;
    }

    @McpTool(name = "list_proxies", description = "List connector proxies (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listProxies() {
        return service.listProxies();
    }

    @McpTool(name = "get_proxy", description = "Get proxy metadata and associated connectors (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getProxy(@McpToolParam(description = "Proxy UUID") String uuid) {
        return service.getProxy(uuid);
    }

    @McpTool(name = "create_proxy", description = "Register a proxy (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createProxy(
            @McpToolParam(description = "JSON object: name, optional description") String configuration) {
        return service.createProxy(configuration);
    }

    @McpTool(name = "update_proxy",
            description = "Update proxy description (ILM 2.18+). Proxy names and generated routing code cannot be changed.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateProxy(@McpToolParam(description = "Proxy UUID") String uuid,
            @McpToolParam(description = "JSON object: description") String configuration) {
        return service.updateProxy(uuid, configuration);
    }

    @McpTool(name = "delete_proxy", description = "Delete a proxy with normal platform dependency checks (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteProxy(@McpToolParam(description = "Proxy UUID") String uuid) {
        return service.deleteProxy(uuid);
    }

    @SensitiveOutput
    @McpTool(name = "get_proxy_installation_instructions",
            description = "Explicitly retrieve proxy provisioning instructions, which may contain installation credentials. Full access only (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getProxyInstallationInstructions(@McpToolParam(description = "Proxy UUID") String uuid) {
        return service.getProxyInstallationInstructions(uuid);
    }
}
