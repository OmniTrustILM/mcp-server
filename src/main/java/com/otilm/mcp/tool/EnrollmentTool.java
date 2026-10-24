package com.otilm.mcp.tool;

import com.otilm.mcp.service.EnrollmentService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentTool {
    private final EnrollmentService service;

    public EnrollmentTool(EnrollmentService service) {
        this.service = service;
    }

    @McpTool(name = "list_acme_profiles", description = "List ACME enrollment profile metadata.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listAcmeProfiles() {
        return service.listProfiles("acme");
    }

    @McpTool(name = "get_acme_profile",
            description = "Get ACME profile configuration metadata, excluding secrets and attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAcmeProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getProfile("acme", uuid);
    }

    @McpTool(name = "create_acme_profile",
            description = "Create ACME profile. Plain issue/revoke/custom attribute values are schema validated. ACME EAB/identifier options and SCEP/CMP challengeSource require ILM 2.20+; SCEP enableChallengePassword requires 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createAcmeProfile(@McpToolParam(
            description = "JSON object: name (creation), description, termsOfServiceUrl, websiteUrl, dnsResolverIp, dnsResolverPort, raProfileUuid, retryInterval, validity, requireContact, requireTermsOfService, termsOfServiceChangeDisable/termsOfServiceChangeUrl (updates), issueCertificateAttributes, revokeCertificateAttributes, customAttributes, certificateAssociations. ILM 2.20+: eabSecretUuids, preauthorizedIdentifiers [{type:dns/ip,value,matchType:exact/subdomain,allowWildcard}], identifierAuthorizationMode:preauthorizedOrChallenge/preauthorizedOnly. Attribute fields are plain name-to-value objects. certificateAssociations contains ownerUuid, groupUuids, customAttributes.") String configuration) {
        return service.createProfile("acme", configuration);
    }

    @McpTool(name = "update_acme_profile",
            description = "Update ACME profile, retaining omitted readable settings and attribute values; populated protected configuration requires fresh replacement. ACME EAB/identifier options and SCEP/CMP challengeSource require ILM 2.20+; SCEP enableChallengePassword requires 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateAcmeProfile(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object: name (creation), description, termsOfServiceUrl, websiteUrl, dnsResolverIp, dnsResolverPort, raProfileUuid, retryInterval, validity, requireContact, requireTermsOfService, termsOfServiceChangeDisable/termsOfServiceChangeUrl (updates), issueCertificateAttributes, revokeCertificateAttributes, customAttributes, certificateAssociations. ILM 2.20+: eabSecretUuids, preauthorizedIdentifiers [{type:dns/ip,value,matchType:exact/subdomain,allowWildcard}], identifierAuthorizationMode:preauthorizedOrChallenge/preauthorizedOnly. Attribute fields are plain name-to-value objects. certificateAssociations contains ownerUuid, groupUuids, customAttributes.") String configuration) {
        return service.updateProfile("acme", uuid, configuration);
    }

    @McpTool(name = "delete_acme_profile", description = "Delete one ACME enrollment profile without force fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteAcmeProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteProfile("acme", uuid);
    }

    @McpTool(name = "enable_acme_profile", description = "Enable one ACME enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableAcmeProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setProfileEnabled("acme", uuid, true);
    }

    @McpTool(name = "disable_acme_profile", description = "Disable one ACME enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableAcmeProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setProfileEnabled("acme", uuid, false);
    }

    @McpTool(name = "set_acme_default_ra_profile",
            description = "Set the default RA profile for one ACME enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setAcmeDefaultRaProfile(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid) {
        return service.setDefaultRaProfile("acme", uuid, raProfileUuid);
    }

    @McpTool(name = "list_scep_profiles", description = "List SCEP enrollment profile metadata.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listScepProfiles() {
        return service.listProfiles("scep");
    }

    @McpTool(name = "get_scep_profile",
            description = "Get SCEP profile configuration metadata, excluding secrets and attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getScepProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getProfile("scep", uuid);
    }

    @McpTool(name = "create_scep_profile",
            description = "Create SCEP profile. Plain issue/revoke/custom attribute values are schema validated. ACME EAB/identifier options and SCEP/CMP challengeSource require ILM 2.20+; SCEP enableChallengePassword requires 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createScepProfile(@McpToolParam(
            description = "JSON object: name (creation), description, raProfileUuid, caCertificateUuid, renewalThreshold, includeCaCertificate, includeCaCertificateChain, challengePassword, enableChallengePassword (ILM 2.19+), challengeSource (ILM 2.20+: protocolDefault/certificateRegistration), enableIntune, intuneTenant, intuneApplicationId, intuneApplicationKey, issueCertificateAttributes, customAttributes, certificateAssociations {ownerUuid,groupUuids,customAttributes}. Attribute fields are plain name-to-value objects; protected values require fresh input on replacement.") String configuration) {
        return service.createProfile("scep", configuration);
    }

    @McpTool(name = "update_scep_profile",
            description = "Update SCEP profile, retaining omitted readable settings and attribute values; populated protected configuration requires fresh replacement. ACME EAB/identifier options and SCEP/CMP challengeSource require ILM 2.20+; SCEP enableChallengePassword requires 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateScepProfile(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object: name (creation), description, raProfileUuid, caCertificateUuid, renewalThreshold, includeCaCertificate, includeCaCertificateChain, challengePassword, enableChallengePassword (ILM 2.19+), challengeSource (ILM 2.20+: protocolDefault/certificateRegistration), enableIntune, intuneTenant, intuneApplicationId, intuneApplicationKey, issueCertificateAttributes, customAttributes, certificateAssociations {ownerUuid,groupUuids,customAttributes}. Attribute fields are plain name-to-value objects; protected values require fresh input on replacement.") String configuration) {
        return service.updateProfile("scep", uuid, configuration);
    }

    @McpTool(name = "delete_scep_profile", description = "Delete one SCEP enrollment profile without force fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteScepProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteProfile("scep", uuid);
    }

    @McpTool(name = "enable_scep_profile", description = "Enable one SCEP enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableScepProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setProfileEnabled("scep", uuid, true);
    }

    @McpTool(name = "disable_scep_profile", description = "Disable one SCEP enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableScepProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setProfileEnabled("scep", uuid, false);
    }

    @McpTool(name = "set_scep_default_ra_profile",
            description = "Set the default RA profile for one SCEP enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setScepDefaultRaProfile(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid) {
        return service.setDefaultRaProfile("scep", uuid, raProfileUuid);
    }

    @McpTool(name = "list_cmp_profiles", description = "List CMP enrollment profile metadata.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listCmpProfiles() {
        return service.listProfiles("cmp");
    }

    @McpTool(name = "get_cmp_profile",
            description = "Get CMP profile configuration metadata, excluding secrets and attribute values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCmpProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getProfile("cmp", uuid);
    }

    @McpTool(name = "create_cmp_profile",
            description = "Create CMP profile. Plain issue/revoke/custom attribute values are schema validated. ACME EAB/identifier options and SCEP/CMP challengeSource require ILM 2.20+; SCEP enableChallengePassword requires 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createCmpProfile(@McpToolParam(
            description = "JSON object: name, description, variant:v2/v2_3gpp/v3, raProfileUuid, requestProtectionMethod/responseProtectionMethod:sharedSecret/signature, sharedSecret, signingCertificateUuid, challengeSource (ILM 2.20+: protocolDefault/certificateRegistration), issueCertificateAttributes, revokeCertificateAttributes, customAttributes, certificateAssociations {ownerUuid,groupUuids,customAttributes}. Attribute fields are plain name-to-value objects; shared-secret protection requires a fresh sharedSecret on replacement.") String configuration) {
        return service.createProfile("cmp", configuration);
    }

    @McpTool(name = "update_cmp_profile",
            description = "Update CMP profile, retaining omitted readable settings and attribute values; populated protected configuration requires fresh replacement. ACME EAB/identifier options and SCEP/CMP challengeSource require ILM 2.20+; SCEP enableChallengePassword requires 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateCmpProfile(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object: name, description, variant:v2/v2_3gpp/v3, raProfileUuid, requestProtectionMethod/responseProtectionMethod:sharedSecret/signature, sharedSecret, signingCertificateUuid, challengeSource (ILM 2.20+: protocolDefault/certificateRegistration), issueCertificateAttributes, revokeCertificateAttributes, customAttributes, certificateAssociations {ownerUuid,groupUuids,customAttributes}. Attribute fields are plain name-to-value objects; shared-secret protection requires a fresh sharedSecret on replacement.") String configuration) {
        return service.updateProfile("cmp", uuid, configuration);
    }

    @McpTool(name = "delete_cmp_profile", description = "Delete one CMP enrollment profile without force fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteCmpProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteProfile("cmp", uuid);
    }

    @McpTool(name = "enable_cmp_profile", description = "Enable one CMP enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableCmpProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setProfileEnabled("cmp", uuid, true);
    }

    @McpTool(name = "disable_cmp_profile", description = "Disable one CMP enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableCmpProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.setProfileEnabled("cmp", uuid, false);
    }

    @McpTool(name = "set_cmp_default_ra_profile",
            description = "Set the default RA profile for one CMP enrollment profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setCmpDefaultRaProfile(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid) {
        return service.setDefaultRaProfile("cmp", uuid, raProfileUuid);
    }

    @McpTool(name = "get_enrollment_certificate_choices",
            description = "List eligible SCEP CA or CMP signing certificates.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getEnrollmentCertificateChoices(@McpToolParam(description = "scep or cmp") String protocol) {
        return service.getEnrollmentCertificateChoices(protocol);
    }

    @SensitiveOutput
    @McpTool(name = "generate_acme_eab_key",
            description = "At full access, ILM 2.20+: generate a one-time ACME external account binding HMAC key. Store it as a secret and associate the secret UUID with an ACME profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String generateAcmeEabKey() {
        return service.generateAcmeEabKey();
    }
}
