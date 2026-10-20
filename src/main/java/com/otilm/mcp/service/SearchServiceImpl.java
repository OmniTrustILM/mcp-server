package com.otilm.mcp.service;

import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.search.SearchFieldDataDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class SearchServiceImpl implements SearchService {

    private static final List<String> RESOURCE_TYPES = List
            .of("certificates", "keys", "secrets", "vaults", "vaultProfiles", "entities", "discoveries", "cryptoAssets",
                    "cboms", "signingProfiles", "signingRecords", "tspProfiles");
    private static final Map<String, IlmFeature> FEATURES = Map
            .of("cryptoAssets", IlmFeature.CRYPTO_ASSETS, "signingProfiles", IlmFeature.SIGNING_PROFILES,
                    "signingRecords", IlmFeature.SIGNING_RECORDS, "tspProfiles", IlmFeature.TSP_PROFILES);

    private final IlmApiClient ilmApiClient;
    private final PlatformVersionCheck versionCheck;

    public SearchServiceImpl(IlmApiClient ilmApiClient, PlatformVersionCheck versionCheck) {
        this.ilmApiClient = ilmApiClient;
        this.versionCheck = versionCheck;
    }

    @Override
    public String getSearchableFields(String resourceType) {
        if (!RESOURCE_TYPES.contains(resourceType)) {
            return "Unknown resource type: " + resourceType + ". Supported types: " + String.join(", ", RESOURCE_TYPES);
        }
        String action = "retrieving searchable fields for " + resourceType;
        Supplier<String> body = () -> formatSearchableFields(resourceType,
                ilmApiClient.getSearchableFields(resourceType));
        IlmFeature feature = FEATURES.get(resourceType);
        return feature == null ? ToolResponses.run(action, body) : versionCheck.run(feature, action, body);
    }

    private static String formatSearchableFields(String resourceType, List<SearchFieldDataByGroupDto> fieldGroups) {
        StringBuilder sb = new StringBuilder();
        TextFormat.title(sb, "Available search fields for " + resourceType);
        if (fieldGroups == null || fieldGroups.isEmpty()) {
            return sb.append("No searchable fields available.\n").toString();
        }
        sb.append("Use these fields in the 'filters' parameter of search tools.\n");
        sb
                .append("Filter format: [{\"fieldSource\":\"<GROUP>\",\"fieldIdentifier\":\"<FIELD>\",")
                .append("\"condition\":\"<OP>\",\"value\":\"<VALUE>\"}]\n\n");
        for (SearchFieldDataByGroupDto group : fieldGroups) {
            sb.append("Group: ").append(group.getFilterFieldSource().getCode()).append("\n");
            if (group.getSearchFieldData() != null) {
                group.getSearchFieldData().forEach(field -> formatField(sb, field));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void formatField(StringBuilder sb, SearchFieldDataDto field) {
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
