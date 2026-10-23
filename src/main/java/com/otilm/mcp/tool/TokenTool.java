package com.otilm.mcp.tool;

import com.otilm.mcp.service.TokenService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TokenTool {
    private final TokenService service;

    public TokenTool(TokenService service) {
        this.service = service;
    }

    @McpTool(name = "get_token_instance",
            description = "Read token instance metadata and provider status; protected attributes omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenInstance(@McpToolParam(description = "Key UUID.") String uuid) {
        return service.getTokenInstance(uuid);
    }

    @McpTool(name = "get_token_instance_attributes",
            description = "Discover token instance provider settings. Kind selects the baseline ILM 2.17 route; omitting kind uses ILM 2.20 provider-neutral settings.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenInstanceAttributes(@McpToolParam(description = "Connector UUID.") String connectorUuid,
            @McpToolParam(description = "Cryptography connector kind; omit only with ILM 2.20 or later.",
                    required = false) String kind,
            @McpToolParam(
                    description = "Optional JSON object of attribute names and values used to resolve schema callbacks.",
                    required = false) String values) {
        return service.getTokenInstanceAttributes(connectorUuid, kind, values);
    }

    @McpTool(name = "create_token_instance",
            description = "Create token instance. Configuration fields: name, connectorUuid (required), kind (required for legacy connector), description, attributes, customAttributes. Provider-neutral mode requires ILM 2.20+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createTokenInstance(@McpToolParam(
            description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.createTokenInstance(configuration);
    }

    @McpTool(name = "update_token_instance",
            description = "Update token instance. Configuration fields: name, description, connectorUuid, kind, attributes, customAttributes. Preserves readable omitted fields; protected replacements need fresh values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTokenInstance(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.updateTokenInstance(uuid, configuration);
    }

    @McpTool(name = "delete_token_instance",
            description = "Delete a token instance; ILM dependency checks remain authoritative. Requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTokenInstance(@McpToolParam(description = "Key UUID.") String uuid) {
        return service.deleteTokenInstance(uuid);
    }

    @McpTool(name = "get_token_activation_attributes",
            description = "Discover token activation schema and callback dependencies without protected values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenActivationAttributes(@McpToolParam(description = "Key UUID.") String uuid, @McpToolParam(
            description = "Optional JSON object of attribute names and values used to resolve schema callbacks.",
            required = false) String values) {
        return service.getTokenActivationAttributes(uuid, values);
    }

    @McpTool(name = "activate_token_instance",
            description = "Activate a token using validated provider activation attributes. Reports request acceptance; use refresh_token_status to inspect actual status.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String activateTokenInstance(@McpToolParam(description = "Key UUID.") String uuid,
            @McpToolParam(description = "JSON object of attribute names and values.",
                    required = false) String attributes) {
        return service.activateTokenInstance(uuid, attributes);
    }

    @McpTool(name = "deactivate_token_instance", description = "Deactivate the token instance.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String deactivateTokenInstance(@McpToolParam(description = "Key UUID.") String uuid) {
        return service.deactivateTokenInstance(uuid);
    }

    @McpTool(name = "refresh_token_status",
            description = "Refresh token provider status through ILM and return metadata.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String refreshTokenStatus(@McpToolParam(description = "Key UUID.") String uuid) {
        return service.refreshTokenStatus(uuid);
    }

    @McpTool(name = "sync_token_keys",
            description = "Ask the provider to synchronize key inventory; reports request acceptance.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String syncTokenKeys(@McpToolParam(description = "Key UUID.") String uuid) {
        return service.syncTokenKeys(uuid);
    }

    @McpTool(name = "list_token_profiles", description = "List token profiles and metadata.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listTokenProfiles() {
        return service.listTokenProfiles();
    }

    @McpTool(name = "get_token_profile",
            description = "Read token profile metadata, enabled state and usages; protected attributes omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenProfile(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.getTokenProfile(tokenUuid, profileUuid);
    }

    @McpTool(name = "get_token_profile_attributes",
            description = "Discover token profile provider schema and callback settings.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenProfileAttributes(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(
                    description = "Optional JSON object of attribute names and values used to resolve schema callbacks.",
                    required = false) String values) {
        return service.getTokenProfileAttributes(tokenUuid, values);
    }

    @McpTool(name = "create_token_profile",
            description = "Create token profile. Configuration fields: name (required), description, enabled, usage, attributes, customAttributes.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createTokenProfile(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(
                    description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.createTokenProfile(tokenUuid, configuration);
    }

    @McpTool(name = "update_token_profile",
            description = "Update token profile. Configuration fields: description, enabled, usage (complete replacement), attributes, customAttributes. Omitted readable values retained.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTokenProfile(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid, @McpToolParam(
                    description = "JSON object using the documented fields. Omitted update fields retain readable existing values; protected attributes require fresh input.") String configuration) {
        return service.updateTokenProfile(tokenUuid, profileUuid, configuration);
    }

    @McpTool(name = "delete_token_profile",
            description = "Delete token profile with authoritative dependency checks; full access required.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTokenProfile(@McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.deleteTokenProfile(profileUuid);
    }

    @McpTool(name = "enable_token_profile", description = "Enable a token profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableTokenProfile(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.enableTokenProfile(tokenUuid, profileUuid);
    }

    @McpTool(name = "disable_token_profile", description = "Disable a token profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableTokenProfile(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.disableTokenProfile(tokenUuid, profileUuid);
    }

    @McpTool(name = "update_token_profile_usages", description = "Replace the token profile allowed key usage list.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTokenProfileUsages(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid, @McpToolParam(
                    description = "JSON array replacing all key usages: sign, verify, encrypt, decrypt, wrap or unwrap.") String usages) {
        return service.updateTokenProfileUsages(tokenUuid, profileUuid, usages);
    }

    @McpTool(name = "get_supported_key_types",
            description = "Discover supported key request types for this token profile; requires ILM 2.20 or later.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSupportedKeyTypes(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.getSupportedKeyTypes(tokenUuid, profileUuid);
    }

    @McpTool(name = "get_token_profile_capabilities",
            description = "Read key import/export capabilities, algorithms and profile usages; requires ILM 2.20 or later.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenProfileCapabilities(@McpToolParam(description = "Token instance UUID.") String tokenUuid,
            @McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.getTokenProfileCapabilities(tokenUuid, profileUuid);
    }

    @McpTool(name = "get_token_profile_compliance",
            description = "List associated compliance profiles for this token profile.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTokenProfileCompliance(@McpToolParam(description = "Token profile UUID.") String profileUuid) {
        return service.getTokenProfileCompliance(profileUuid);
    }

    @McpTool(name = "associate_token_profile_compliance",
            description = "Associate one compliance profile with this token profile without replacing other associations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String associateTokenProfileCompliance(@McpToolParam(description = "Token profile UUID.") String profileUuid,
            @McpToolParam(description = "Compliance profile UUID.") String complianceUuid) {
        return service.associateTokenProfileCompliance(profileUuid, complianceUuid);
    }

    @McpTool(name = "detach_token_profile_compliance",
            description = "Detach one compliance profile from this token profile without replacing other associations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String detachTokenProfileCompliance(@McpToolParam(description = "Token profile UUID.") String profileUuid,
            @McpToolParam(description = "Compliance profile UUID.") String complianceUuid) {
        return service.detachTokenProfileCompliance(profileUuid, complianceUuid);
    }

}
