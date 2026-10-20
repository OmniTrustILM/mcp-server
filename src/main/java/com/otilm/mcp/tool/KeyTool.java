package com.otilm.mcp.tool;

import com.otilm.mcp.service.KeyService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class KeyTool {

    private final KeyService keyService;

    public KeyTool(KeyService keyService) {
        this.keyService = keyService;
    }

    @Tool(name = "search_keys",
            description = "Search and list cryptographic keys in the ILM platform with optional filters and pagination. Use get_searchable_fields('keys') to discover available filter fields. Returns key name, UUID, algorithm, key size, type, state, enabled status, token instance, token profile, and owner.")
    public String searchKeys(@ToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('keys') to see available fields and operators.",
            required = false) String filters,
            @ToolParam(description = "Number of items per page (default: 10)", required = false) Integer itemsPerPage,
            @ToolParam(description = "Page number for pagination (default: 1)", required = false) Integer pageNumber) {
        return keyService.searchKeys(filters, itemsPerPage, pageNumber);
    }

    @Tool(name = "get_key",
            description = "Get detailed information about a specific cryptographic key by its UUID. Returns key name, description, creation time, token instance, token profile, owner, compliance status, groups, and detailed key items including type, algorithm, size, format, state, enabled status, and usage. Use this when you need complete details about a particular key.")
    public String getKey(@ToolParam(description = "The UUID of the cryptographic key to retrieve") String uuid) {
        return keyService.getKey(uuid);
    }
}
