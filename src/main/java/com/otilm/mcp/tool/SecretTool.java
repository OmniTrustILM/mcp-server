package com.otilm.mcp.tool;

import com.otilm.mcp.service.SecretService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SecretTool {

    private final SecretService secretService;

    public SecretTool(SecretService secretService) {
        this.secretService = secretService;
    }

    @McpTool(name = "search_secrets",
            description = "Search and list secrets in the ILM platform with optional filters and pagination. Use get_searchable_fields('secrets') to discover available filter fields. Returns secret name, UUID, type, state, version, enabled status, owner, vault profile, groups, and compliance status.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String searchSecrets(@McpToolParam(
            description = "JSON array of search filters. Use get_searchable_fields('secrets') to see available fields and operators.",
            required = false) String filters,
            @McpToolParam(description = "Number of items per page (default: 10)",
                    required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number for pagination (default: 1)",
                    required = false) Integer pageNumber) {
        return secretService.searchSecrets(filters, itemsPerPage, pageNumber);
    }

    @McpTool(name = "get_secret",
            description = "Get detailed information about a specific secret by its UUID. Returns full secret details including name, description, type, state, version, enabled status, owner, source vault profile, groups, compliance status, creation/update timestamps, and sync vault profiles. Use this when you need complete details about a particular secret. Does not return the actual secret content for security reasons.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSecret(@McpToolParam(description = "The UUID of the secret to retrieve") String uuid) {
        return secretService.getSecret(uuid);
    }

    @McpTool(name = "get_secret_versions",
            description = "Get the version history of a specific secret by its UUID. Returns a list of all versions with version number, creation timestamp, and fingerprint. Use this to inspect how a secret has changed over time or verify secret rotation history.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSecretVersions(
            @McpToolParam(description = "The UUID of the secret whose versions to retrieve") String uuid) {
        return secretService.getSecretVersions(uuid);
    }
}
