package com.otilm.mcp.tool;

import com.otilm.mcp.service.KeyManagementService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class KeyManagementTool {
    private final KeyManagementService service;

    public KeyManagementTool(KeyManagementService service) {
        this.service = service;
    }

    @McpTool(name = "list_key_pairs",
            description = "List cryptographic key pairs; optionally filter by token profile. Metadata only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listKeyPairs(@McpToolParam(description = "Optional token profile UUID filter.",
            required = false) String tokenProfileUuid) {
        return service.listKeyPairs(tokenProfileUuid);
    }

    @McpTool(name = "get_key_item",
            description = "Read key item metadata, state, enabled flag, algorithm and usages; omits key material.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getKeyItem(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "Key item UUID belonging to this key.") String itemUuid) {
        return service.getKeyItem(uuid, itemUuid);
    }

    @McpTool(name = "get_key_history",
            description = "Read key item event and status history without provider messages or material.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getKeyHistory(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "Key item UUID belonging to this key.") String itemUuid) {
        return service.getKeyHistory(uuid, itemUuid);
    }

    @McpTool(name = "get_key_generation_attributes",
            description = "Discover schema and callback settings for key generation; supported on ILM 2.17 or later.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getKeyGenerationAttributes(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid,
            @McpToolParam(description = "Key request type: keyPair or secret.") String type,
            @McpToolParam(
                    description = "Optional JSON object of attribute names and values used to resolve schema callbacks.",
                    required = false) String values) {
        return service.getKeyGenerationAttributes(tokenUuid, profileUuid, type, values);
    }

    @McpTool(name = "get_key_import_attributes",
            description = "Discover provider key import settings; requires ILM 2.20 or later.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getKeyImportAttributes(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid,
            @McpToolParam(description = "Key request type: keyPair or secret.") String type,
            @McpToolParam(
                    description = "Optional JSON object of attribute names and values used to resolve schema callbacks.",
                    required = false) String values) {
        return service.getKeyImportAttributes(tokenUuid, profileUuid, type, values);
    }

    @McpTool(name = "get_key_export_attributes",
            description = "Discover provider key export settings; requires ILM 2.20 or later. Returns no key material.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getKeyExportAttributes(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "Key item UUID belonging to this key.") String itemUuid,
            @McpToolParam(
                    description = "Optional JSON object of attribute names and values used to resolve schema callbacks.",
                    required = false) String values) {
        return service.getKeyExportAttributes(uuid, itemUuid, values);
    }

    @McpTool(name = "generate_key",
            description = "Generate a key using provider schema. Configuration fields: name (required), description, groupUuids, attributes, customAttributes, enabled, exportable. Exportable=true requires ILM 2.20+ and is final at creation.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String generateKey(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid,
            @McpToolParam(description = "Key request type: keyPair or secret.") String type, @McpToolParam(
                    description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.generateKey(tokenUuid, profileUuid, type, configuration);
    }

    @McpTool(name = "import_key",
            description = "Import key material; ILM 2.20+. Configuration fields: name and base64 file (required, decoded <=5 MiB), inputPassphrase, description, exportable, importAttributes, customAttributes, groupUuids. Returns metadata only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String importKey(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid,
            @McpToolParam(description = "Key request type: keyPair or secret.") String type, @McpToolParam(
                    description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.importKey(tokenUuid, profileUuid, type, configuration);
    }

    @SensitiveOutput
    @McpTool(name = "export_key",
            description = "Export an active enabled exportable key item as encrypted PKCS#8 PEM. Requires ILM 2.20+, profile export support and full MCP access. Returns filename, media type and encrypted material.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String exportKey(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "Key item UUID belonging to this key.") String itemUuid,
            @McpToolParam(
                    description = "Export protection passphrase in NFC, at least 12 Unicode code points.") String passphrase,
            @McpToolParam(description = "JSON object of attribute names and values.",
                    required = false) String attributes) {
        return service.exportKey(uuid, itemUuid, passphrase, attributes);
    }

    @McpTool(name = "update_key",
            description = "Update key metadata. Configuration fields: name, description, tokenProfileUuid, ownerUuid, groupUuids (complete replacement), customAttributes. Omitted readable fields are preserved.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateKey(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.updateKey(uuid, configuration);
    }

    @McpTool(name = "rename_key_item", description = "Rename a key item without changing key material.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String renameKeyItem(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "Key item UUID belonging to this key.") String itemUuid,
            @McpToolParam(description = "New item name.") String name) {
        return service.renameKeyItem(uuid, itemUuid, name);
    }

    @McpTool(name = "enable_key", description = "Enable all key items or the explicit selection.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableKey(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "Optional JSON array of key item UUIDs; omitted or empty selects every item in this key.",
            required = false) String itemUuids) {
        return service.enableKey(uuid, itemUuids);
    }

    @McpTool(name = "disable_key", description = "Disable all key items or the explicit selection.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableKey(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "Optional JSON array of key item UUIDs; omitted or empty selects every item in this key.",
            required = false) String itemUuids) {
        return service.disableKey(uuid, itemUuids);
    }

    @McpTool(name = "update_key_usages", description = "Replace usages for all key items or the explicit selection.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateKeyUsages(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "JSON array replacing all key usages: sign, verify, encrypt, decrypt, wrap or unwrap.") String usages,
            @McpToolParam(
                    description = "Optional JSON array of key item UUIDs; omitted or empty selects every item in this key.",
                    required = false) String itemUuids) {
        return service.updateKeyUsages(uuid, usages, itemUuids);
    }

    @McpTool(name = "compromise_key",
            description = "Mark selected or all key items compromised. This irreversible state change requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String compromiseKey(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "Compromise reason: disclosure, modification, substitution or use_of_sensitive_data.") String reason,
            @McpToolParam(
                    description = "Optional JSON array of key item UUIDs; omitted or empty selects every item in this key.",
                    required = false) String itemUuids) {
        return service.compromiseKey(uuid, reason, itemUuids);
    }

    @McpTool(name = "destroy_key",
            description = "Destroy selected or all key items; may remove provider key material irreversibly. Requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String destroyKey(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "Optional JSON array of key item UUIDs; omitted or empty selects every item in this key.",
            required = false) String itemUuids) {
        return service.destroyKey(uuid, itemUuids);
    }

    @McpTool(name = "delete_key",
            description = "Delete selected or all key items and inventory; may remove provider material irreversibly. Requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteKey(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "Optional JSON array of key item UUIDs; omitted or empty selects every item in this key.",
            required = false) String itemUuids) {
        return service.deleteKey(uuid, itemUuids);
    }

    @McpTool(name = "disable_key_export",
            description = "Permanently disable export for a key item; ILM 2.20+. This is irreversible and requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String disableKeyExport(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "Key item UUID belonging to this key.") String itemUuid) {
        return service.disableKeyExport(uuid, itemUuid);
    }

}
