package com.otilm.mcp.tool;

import com.otilm.mcp.service.RaProfileService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class RaProfileTool {
    private final RaProfileService service;

    public RaProfileTool(RaProfileService service) {
        this.service = service;
    }

    @McpTool(name = "get_ra_profile",
            description = "Inspect RA profile metadata, validation settings and redacted request definition settings.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRaProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getRaProfile(uuid);
    }

    @McpTool(name = "get_ra_profile_attributes",
            description = "Discover provider fields for creating an RA profile and resolve callbacks.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRaProfileAttributes(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Optional plain JSON values for callback dependencies",
                    required = false) String values) {
        return service.getRaProfileAttributes(authorityUuid, values);
    }

    @McpTool(name = "create_ra_profile",
            description = "Create an RA profile on an authority. JSON fields: name, description, enabled, attributes, customAttributes.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createRaProfile(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.createRaProfile(authorityUuid, configuration);
    }

    @McpTool(name = "update_ra_profile",
            description = "Update description, enabled, attributes and customAttributes, retaining omitted readable values and requiring fresh protected values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateRaProfile(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.updateRaProfile(authorityUuid, uuid, configuration);
    }

    @McpTool(name = "delete_ra_profile", description = "Delete one RA profile without forced fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteRaProfile(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteRaProfile(authorityUuid, uuid);
    }

    @McpTool(name = "enable_ra_profile", description = "Enable an RA profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableRaProfile(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setRaProfileEnabled(authorityUuid, uuid, true);
    }

    @McpTool(name = "disable_ra_profile", description = "Disable an RA profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableRaProfile(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setRaProfileEnabled(authorityUuid, uuid, false);
    }

    @McpTool(name = "update_ra_profile_validation",
            description = "Update RA certificate validation enabled state, frequency and expiringThreshold, preserving omitted settings.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateRaProfileValidation(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.updateRaProfileValidation(authorityUuid, uuid, configuration);
    }

    @McpTool(name = "update_ra_profile_request_attributes",
            description = "ILM 2.19+: update requestAttributes definitions, mergeMode (staticOnly/connectorOnly/merge), valueSourceBindings and externalCsrValidationStrict, preserving omitted settings.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateRaProfileRequestAttributes(
            @McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.updateRaProfileRequestAttributes(authorityUuid, uuid, configuration);
    }

    @McpTool(name = "get_ra_profile_protocol",
            description = "Inspect one RA profile enrollment protocol association, omitting protected values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRaProfileProtocol(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "acme, scep or cmp") String protocol) {
        return service.getRaProfileProtocol(authorityUuid, uuid, protocol);
    }

    @McpTool(name = "set_ra_profile_protocol",
            description = "Activate an ACME/SCEP/CMP profile for an RA profile or deactivate when enrollmentUuid is omitted. JSON fields issueCertificateAttributes and revokeCertificateAttributes (ACME/CMP only), encoded from provider schemas.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setRaProfileProtocol(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "acme, scep or cmp") String protocol,
            @McpToolParam(description = "Profile UUID to activate, omitted to deactivate",
                    required = false) String enrollmentUuid,
            @McpToolParam(description = "Optional operation values JSON", required = false) String configuration) {
        return service.setRaProfileProtocol(authorityUuid, uuid, protocol, enrollmentUuid, configuration);
    }

    @McpTool(name = "get_ra_profile_associations",
            description = "List RA profile compliance and approval associations. Use targeted compliance/approval association tools to change them.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getRaProfileAssociations(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getRaProfileAssociations(authorityUuid, uuid);
    }

    @McpTool(name = "get_ra_profile_authority_chain",
            description = "List authority CA certificates for an RA profile without private material.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAuthorityChain(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getAuthorityChain(authorityUuid, uuid);
    }
}
