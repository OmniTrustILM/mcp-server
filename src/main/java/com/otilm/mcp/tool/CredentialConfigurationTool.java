package com.otilm.mcp.tool;

import com.otilm.mcp.service.CredentialConfigurationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CredentialConfigurationTool {
    private final CredentialConfigurationService service;

    public CredentialConfigurationTool(CredentialConfigurationService service) {
        this.service = service;
    }

    @McpTool(name = "list_credentials", description = "List credential metadata without decrypted attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listCredentials() {
        return service.listCredentials();
    }

    @McpTool(name = "get_credential",
            description = "Read credential metadata. Secret/file/credential attribute values are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCredential(@McpToolParam(description = "Credential UUID") String uuid) {
        return service.getCredential(uuid);
    }

    @McpTool(name = "get_credential_attributes",
            description = "Get credential provider settings after resolving callbacks. Supply values when provider choices or fields depend on other attributes.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCredentialAttributes(@McpToolParam(description = "Connector UUID") String connectorUuid,
            @McpToolParam(description = "Provider credential kind") String kind,
            @McpToolParam(description = "Optional plain attribute-name to value JSON for callback dependencies",
                    required = false) String values) {
        return service.getCredentialAttributes(connectorUuid, kind, values);
    }

    @McpTool(name = "create_credential",
            description = "Create a provider credential using typed attributes and custom values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createCredential(@McpToolParam(
            description = "JSON object: name, connectorUuid, kind, attributes and optional customAttributes plain value objects") String configuration) {
        return service.createCredential(configuration);
    }

    @McpTool(name = "update_credential",
            description = "Replace credential attributes while retaining readable omitted values. Existing protected attributes require freshly supplied values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateCredential(@McpToolParam(description = "Credential UUID") String uuid, @McpToolParam(
            description = "JSON object: attributes and customAttributes plain value objects; name and provider cannot be changed") String configuration) {
        return service.updateCredential(uuid, configuration);
    }

    @McpTool(name = "enable_credential", description = "Enable a credential.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableCredential(@McpToolParam(description = "Credential UUID") String uuid) {
        return service.enableCredential(uuid);
    }

    @McpTool(name = "disable_credential", description = "Disable a credential.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableCredential(@McpToolParam(description = "Credential UUID") String uuid) {
        return service.disableCredential(uuid);
    }

    @McpTool(name = "delete_credential",
            description = "Delete a credential using platform authorization and dependency checks.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteCredential(@McpToolParam(description = "Credential UUID") String uuid) {
        return service.deleteCredential(uuid);
    }
}
