package com.otilm.mcp.tool;

import com.otilm.mcp.service.VaultService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class VaultTool {

    private final VaultService vaultService;

    public VaultTool(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @McpTool(name = "list_vault_instances",
            description = "List vault instances configured in the ILM platform with optional filters and pagination. Use get_searchable_fields('vaults') to discover available filter fields. Returns vault name, UUID, description, and connector for each instance.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listVaultInstances(@McpToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('vaults') to see available fields and operators.",
            required = false) String filters,
            @McpToolParam(description = "Number of items per page (default: 10)",
                    required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number for pagination (default: 1)",
                    required = false) Integer pageNumber) {
        return vaultService.listVaultInstances(filters, itemsPerPage, pageNumber);
    }

    @McpTool(name = "list_vault_profiles",
            description = "List vault profiles configured in the ILM platform with optional filters and pagination. Use get_searchable_fields('vaultProfiles') to discover available filter fields. Returns profile name, UUID, description, vault instance, and enabled status.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listVaultProfiles(@McpToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('vaultProfiles') to see available fields and operators.",
            required = false) String filters,
            @McpToolParam(description = "Number of items per page (default: 10)",
                    required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number for pagination (default: 1)",
                    required = false) Integer pageNumber) {
        return vaultService.listVaultProfiles(filters, itemsPerPage, pageNumber);
    }
}
