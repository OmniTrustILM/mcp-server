package com.otilm.mcp.service;

import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

    private static final Map<String, String> RESOURCE_PATHS = Map
            .of("certificates", "certificates", "keys", "keys", "secrets", "secrets", "vaults", "vaults",
                    "vaultProfiles", "vaultProfiles", "entities", "entities", "discoveries", "discoveries");

    private final IlmApiClient ilmApiClient;

    public SearchServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String getSearchableFields(String resourceType) {
        String resource = RESOURCE_PATHS.get(resourceType);
        if (resource == null) {
            return "Unknown resource type: " + resourceType + ". Supported types: "
                    + String.join(", ", RESOURCE_PATHS.keySet());
        }

        try {
            List<SearchFieldDataByGroupDto> fieldGroups = ilmApiClient.getSearchableFields(resource);
            return formatSearchableFields(resourceType, fieldGroups);
        } catch (Exception e) {
            logger.error("Failed to get searchable fields for {}", resourceType, e);
            return "Error retrieving searchable fields for " + resourceType + ": " + e.getMessage();
        }
    }

    private String formatSearchableFields(String resourceType, List<SearchFieldDataByGroupDto> fieldGroups) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available search fields for ").append(resourceType).append("\n");
        sb.append("==========================================\n\n");

        if (fieldGroups == null || fieldGroups.isEmpty()) {
            sb.append("No searchable fields available.\n");
            return sb.toString();
        }

        sb.append("Use these fields in the 'filters' parameter of search tools.\n");
        sb
                .append("Filter format: [{\"fieldSource\":\"<GROUP>\",\"fieldIdentifier\":\"<FIELD>\",\"condition\":\"<OP>\",\"value\":\"<VALUE>\"}]\n\n");

        for (SearchFieldDataByGroupDto group : fieldGroups) {
            sb.append("Group: ").append(group.getFilterFieldSource().name()).append("\n");

            if (group.getSearchFieldData() != null) {
                for (SearchFieldDataDto field : group.getSearchFieldData()) {
                    formatField(sb, field);
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private void formatField(StringBuilder sb, SearchFieldDataDto field) {
        sb.append("  - ").append(field.getFieldIdentifier());
        sb.append(" | ").append(field.getFieldLabel());
        sb.append(" (").append(field.getType().name()).append(")");

        if (field.getConditions() != null && !field.getConditions().isEmpty()) {
            sb.append(" — Operators: ");
            sb.append(String.join(", ", field.getConditions().stream().map(Enum::name).toList()));
        }

        if (field.getValue() != null) {
            sb.append(" — Values: ").append(field.getValue());
        }

        sb.append("\n");
    }
}
