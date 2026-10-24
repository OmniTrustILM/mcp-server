package com.otilm.mcp.tool;

import com.otilm.mcp.service.VaultConfigurationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class VaultConfigurationTool {
    private final VaultConfigurationService service;

    public VaultConfigurationTool(VaultConfigurationService service) {
        this.service = service;
    }

    @McpTool(name = "get_vault_instance",
            description = "Get vault instance metadata by UUID, omitting provider/custom attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getVaultInstance(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.getVaultInstance(uuid);
    }

    @McpTool(name = "get_vault_attributes",
            description = "Get vault creation schema for a connector secret interface. Supply callback dependencies as values JSON; protected defaults are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getVaultAttributes(@McpToolParam(description = "connectorUuid canonical UUID") String connectorUuid,
            @McpToolParam(description = "interfaceUuid canonical UUID") String interfaceUuid,
            @McpToolParam(description = "Optional schema callback dependency values as a JSON object",
                    required = false) String values) {
        return service.getVaultAttributes(connectorUuid, interfaceUuid, values);
    }

    @McpTool(name = "get_vault_profile",
            description = "Get vault profile metadata, omitting provider/custom attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getVaultProfile(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid) {
        return service.getVaultProfile(vaultUuid, profileUuid);
    }

    @McpTool(name = "get_vault_profile_attributes",
            description = "Get vault profile provider/custom schemas. values JSON supplies callback dependencies; protected defaults are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getVaultProfileAttributes(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "Optional schema callback dependency values as a JSON object",
                    required = false) String values) {
        return service.getVaultProfileAttributes(vaultUuid, values);
    }

    @McpTool(name = "get_secret_attributes",
            description = "Get secret creation provider/custom schemas for a vault profile and published secret type; protected defaults are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSecretAttributes(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid,
            @McpToolParam(
                    description = "basicAuth, apiKey, jwtToken, privateKey, secretKey, keyStore, keyValue or generic") String secretType,
            @McpToolParam(description = "Optional schema callback dependency values as a JSON object",
                    required = false) String values) {
        return service.getSecretAttributes(vaultUuid, profileUuid, secretType, values);
    }

    @McpTool(name = "create_vault_instance",
            description = "Create a vault instance. JSON: name, connectorUuid, interfaceUuid (secret interface), optional description, attributes and customAttributes value objects. Schemas/callbacks validate before creation.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createVaultInstance(@McpToolParam(
            description = "Documented JSON configuration object; provider/custom values are keyed by schema attribute name. Typed secret: basicAuth username/password; apiKey,jwtToken,generic content string; privateKey,secretKey base64 content; keyStore keyStoreType(JKS/PKCS12),base64 content,password; keyValue content object.") String configuration) {
        return service.createVaultInstance(configuration);
    }

    @McpTool(name = "update_vault_instance",
            description = "Update description, attributes/customAttributes value objects on a vault instance. Omitted readable values are preserved; existing protected fields require fresh explicit replacements. Name and connector/interface cannot change.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateVaultInstance(@McpToolParam(description = "uuid canonical UUID") String uuid, @McpToolParam(
            description = "Documented JSON configuration object; provider/custom values are keyed by schema attribute name. Typed secret: basicAuth username/password; apiKey,jwtToken,generic content string; privateKey,secretKey base64 content; keyStore keyStoreType(JKS/PKCS12),base64 content,password; keyValue content object.") String configuration) {
        return service.updateVaultInstance(uuid, configuration);
    }

    @McpTool(name = "delete_vault_instance",
            description = "Delete a vault instance. Dependencies must be removed first; no force or cascade fallback.")
    public String deleteVaultInstance(@McpToolParam(description = "uuid canonical UUID") String uuid) {
        return service.deleteVaultInstance(uuid);
    }

    @McpTool(name = "create_vault_profile",
            description = "Create a vault profile. JSON: name, optional description, attributes/customAttributes value objects. Schemas/callbacks validate before creation.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createVaultProfile(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(
                    description = "Documented JSON configuration object; provider/custom values are keyed by schema attribute name. Typed secret: basicAuth username/password; apiKey,jwtToken,generic content string; privateKey,secretKey base64 content; keyStore keyStoreType(JKS/PKCS12),base64 content,password; keyValue content object.") String configuration) {
        return service.createVaultProfile(vaultUuid, configuration);
    }

    @McpTool(name = "update_vault_profile",
            description = "Update vault profile description and attribute value objects. Preserve omitted readable fields and require fresh protected replacements before PUT.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateVaultProfile(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid, @McpToolParam(
                    description = "Documented JSON configuration object; provider/custom values are keyed by schema attribute name. Typed secret: basicAuth username/password; apiKey,jwtToken,generic content string; privateKey,secretKey base64 content; keyStore keyStoreType(JKS/PKCS12),base64 content,password; keyValue content object.") String configuration) {
        return service.updateVaultProfile(vaultUuid, profileUuid, configuration);
    }

    @McpTool(name = "enable_vault_profile", description = "Enable a vault profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableVaultProfile(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid) {
        return service.enableVaultProfile(vaultUuid, profileUuid);
    }

    @McpTool(name = "disable_vault_profile", description = "Disable a vault profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableVaultProfile(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid) {
        return service.disableVaultProfile(vaultUuid, profileUuid);
    }

    @McpTool(name = "delete_vault_profile",
            description = "Delete a vault profile. Dependencies must be resolved first.")
    public String deleteVaultProfile(@McpToolParam(description = "vaultUuid canonical UUID") String vaultUuid,
            @McpToolParam(description = "profileUuid canonical UUID") String profileUuid) {
        return service.deleteVaultProfile(vaultUuid, profileUuid);
    }
}
