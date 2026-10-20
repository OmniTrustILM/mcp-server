package com.otilm.mcp.tool;

import com.otilm.mcp.service.SearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SearchTool {

    private final SearchService searchService;

    public SearchTool(SearchService searchService) {
        this.searchService = searchService;
    }

    @Tool(name = "get_searchable_fields",
            description = "Get available search filter fields for a resource type. Returns field identifiers, labels, types, and supported filter operators. Use this before search tools to discover what filters are available.",
            resultConverter = PlainTextResult.class)
    public String getSearchableFields(@ToolParam(
            description = "Resource type to get searchable fields for. One of: certificates, keys, secrets, vaults, vaultProfiles, entities, discoveries, cryptoAssets (ILM 2.20+), cboms, signingProfiles (ILM 2.18+), signingRecords (ILM 2.19+), tspProfiles (ILM 2.18+)") String resourceType) {
        return searchService.getSearchableFields(resourceType);
    }
}
