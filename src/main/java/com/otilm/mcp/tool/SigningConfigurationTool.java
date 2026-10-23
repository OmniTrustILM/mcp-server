package com.otilm.mcp.tool;

import com.otilm.mcp.service.SigningConfigurationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SigningConfigurationTool {
    private final SigningConfigurationService service;

    public SigningConfigurationTool(SigningConfigurationService service) {
        this.service = service;
    }

    @McpTool(name = "get_signing_schemes",
            description = "Describe supported signing scheme and workflow configuration shapes; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSigningSchemes() {
        return service.getSigningSchemes();
    }

    @McpTool(name = "get_signing_protocols",
            description = "Discover protocols supported for a signing workflow; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSigningProtocols(@McpToolParam(
            description = "Workflow code: timestamping, content_signing or raw_signing.") String workflowType) {
        return service.getSigningProtocols(workflowType);
    }

    @McpTool(name = "get_signing_certificates",
            description = "Discover certificates eligible for a signing workflow; ILM 2.18+. Metadata only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSigningCertificates(@McpToolParam(
            description = "Workflow code: timestamping, content_signing or raw_signing.") String workflowType) {
        return service.getSigningCertificates(workflowType);
    }

    @McpTool(name = "get_signing_operation_attributes",
            description = "Discover signing operation attribute schema for a certificate; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSigningOperationAttributes(
            @McpToolParam(description = "Certificate UUID.") String certificateUuid) {
        return service.getSigningOperationAttributes(certificateUuid);
    }

    @McpTool(name = "get_signing_formatting_attributes",
            description = "Discover formatting schema; chooses the ILM 2.18 or 2.19+ route. Supplying family/maxLevel selects content formatting and requires ILM 2.20+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSigningFormattingAttributes(
            @McpToolParam(description = "Provider connector UUID.") String connectorUuid,
            @McpToolParam(
                    description = "Optional content signature family: pades, xades, cades or jades; requires ILM 2.20+.",
                    required = false) String family,
            @McpToolParam(
                    description = "Optional content signature level: signed, timestamped, long_term or archival; requires ILM 2.20+.",
                    required = false) String maxLevel,
            @McpToolParam(
                    description = "Optional JSON object of schema attribute names and values for callback resolution.",
                    required = false) String values) {
        return service.getSigningFormattingAttributes(connectorUuid, family, maxLevel, values);
    }

    @McpTool(name = "create_signing_profile",
            description = "Create signing profile; ILM 2.18+. Configuration: name, signingScheme and workflow required; description, customAttributes, recordPolicy optional. Scheme uses managed/static_key certificateUuid, managed/one_time_key raProfileUuid/csrTemplateUuid/tokenProfileUuid, or delegated connectorUuid. Provider attributes are name-value objects. recordPolicy requires 2.19+; content family/maxLevel and timestampSource require 2.20+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createSigningProfile(@McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.createSigningProfile(configuration);
    }

    @McpTool(name = "update_signing_profile",
            description = "Update signing profile and return its resulting version; ILM 2.18+. Configuration fields match create_signing_profile; omitted readable fields retained. Nested scheme/workflow objects merge documented fields; protected attributes need fresh input.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateSigningProfile(@McpToolParam(description = "Object UUID.") String uuid, @McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.updateSigningProfile(uuid, configuration);
    }

    @McpTool(name = "delete_signing_profile",
            description = "Delete signing profile with authoritative dependency checks; ILM 2.18+ and full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteSigningProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.deleteSigningProfile(uuid);
    }

    @McpTool(name = "enable_signing_profile", description = "Enable signing profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableSigningProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.enableSigningProfile(uuid);
    }

    @McpTool(name = "disable_signing_profile", description = "Disable signing profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableSigningProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.disableSigningProfile(uuid);
    }

    @McpTool(name = "get_signing_tsp_activation",
            description = "Read TSP activation and association for a signing profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getSigningTspActivation(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.getSigningTspActivation(uuid);
    }

    @McpTool(name = "activate_signing_tsp",
            description = "Associate and activate a TSP profile on this signing profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String activateSigningTsp(@McpToolParam(description = "Object UUID.") String uuid,
            @McpToolParam(description = "TSP profile UUID.") String tspProfileUuid) {
        return service.activateSigningTsp(uuid, tspProfileUuid);
    }

    @McpTool(name = "deactivate_signing_tsp", description = "Deactivate TSP on this signing profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String deactivateSigningTsp(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.deactivateSigningTsp(uuid);
    }

}
