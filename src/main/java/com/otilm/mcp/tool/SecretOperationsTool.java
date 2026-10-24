package com.otilm.mcp.tool;

import com.otilm.mcp.service.SecretOperationsService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SecretOperationsTool {
    private final SecretOperationsService service;

    public SecretOperationsTool(SecretOperationsService service) {
        this.service = service;
    }

    @McpTool(name = "create_secret",
            description = "Request secret creation in a vault profile. JSON: name, secret typed content, optional description, attributes/customAttributes value objects. Reports accepted state; provider synchronization/approval may remain pending. Never returns contents.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createSecret(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid, @McpToolParam(
                    description = "Documented JSON configuration object; provider/custom values are keyed by schema attribute name. Typed secret: basicAuth username/password; apiKey,jwtToken,generic content string; privateKey,secretKey base64 content; keyStore keyStoreType(JKS/PKCS12),base64 content,password; keyValue content object.") String configuration) {
        return service.createSecret(vaultUuid, profileUuid, configuration);
    }

    @McpTool(name = "update_secret",
            description = "Update secret metadata/custom attributes or request a new version with fresh typed secret content. Provider attributes apply to new content only. Omitted readable fields are preserved; protected replacement fields need fresh values. vaultUuid is required for new content.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateSecret(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "vaultUuid canonical UUID", required = false) String vaultUuid, @McpToolParam(
                    description = "Documented JSON configuration object; provider/custom values are keyed by schema attribute name. Typed secret: basicAuth username/password; apiKey,jwtToken,generic content string; privateKey,secretKey base64 content; keyStore keyStoreType(JKS/PKCS12),base64 content,password; keyValue content object.") String configuration) {
        return service.updateSecret(uuid, vaultUuid, configuration);
    }

    @McpTool(name = "enable_secret", description = "Enable a secret.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableSecret(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.enableSecret(uuid);
    }

    @McpTool(name = "disable_secret", description = "Disable a secret.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableSecret(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.disableSecret(uuid);
    }

    @McpTool(name = "set_secret_groups",
            description = "Replace this secret group membership with a JSON array of UUIDs; [] clears groups. Uses a separate targeted request.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setSecretGroups(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "JSON array of group UUIDs") String groupUuids) {
        return service.setSecretGroups(uuid, groupUuids);
    }

    @McpTool(name = "set_secret_owner",
            description = "Set secret owner by UUID, or empty string to clear. Uses a separate targeted request.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setSecretOwner(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "Owner UUID, or empty string to clear") String ownerUuid) {
        return service.setSecretOwner(uuid, ownerUuid);
    }

    @McpTool(name = "change_secret_source_vault_profile",
            description = "Request source vault profile change with target provider attributes value JSON. The target vault/profile are validated and the response reports pending approval/provider work.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String changeSecretSourceVaultProfile(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid,
            @McpToolParam(description = "Provider attribute values as a JSON object") String attributes) {
        return service.changeSecretSourceVaultProfile(uuid, vaultUuid, profileUuid, attributes);
    }

    @McpTool(name = "add_secret_sync_vault_profile",
            description = "Add a synchronization profile using target provider attributes value JSON. Reports acceptance; provider synchronization may be pending.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String addSecretSyncVaultProfile(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid,
            @McpToolParam(description = "Provider attribute values as a JSON object") String attributes) {
        return service.addSecretSyncVaultProfile(uuid, vaultUuid, profileUuid, attributes);
    }

    @McpTool(name = "remove_secret_sync_vault_profile",
            description = "Remove a synchronization profile association while retaining provider secret content.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String removeSecretSyncVaultProfile(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid) {
        return service.removeSecretSyncVaultProfile(uuid, profileUuid);
    }

    @McpTool(name = "remove_secret_sync_vault_profile_content",
            description = "Remove a synchronization profile association and delete secret content from that provider vault.")
    public String removeSecretSyncVaultProfileContent(@McpToolParam(description = "uuid canonical UUID") String uuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid) {
        return service.removeSecretSyncVaultProfileContent(uuid, profileUuid);
    }

    @McpTool(name = "delete_secret",
            description = "Delete secret inventory entry while retaining secret content in provider vaults.")
    public String deleteSecret(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.deleteSecret(uuid);
    }

    @McpTool(name = "delete_secret_from_vaults",
            description = "Delete secret inventory entry AND secret content from source and synchronization provider vaults.")
    public String deleteSecretFromVaults(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.deleteSecretFromVaults(uuid);
    }

    @SensitiveOutput
    @McpTool(name = "get_secret_content",
            description = "Retrieve actual secret content, including credentials or private material. Explicit sensitive output is available only at full access.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSecretContent(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.getSecretContent(uuid);
    }
}
