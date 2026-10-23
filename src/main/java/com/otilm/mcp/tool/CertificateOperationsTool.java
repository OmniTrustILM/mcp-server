package com.otilm.mcp.tool;

import com.otilm.mcp.service.CertificateOperationsService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CertificateOperationsTool {
    private final CertificateOperationsService service;

    public CertificateOperationsTool(CertificateOperationsService service) {
        this.service = service;
    }

    @McpTool(name = "get_certificate_operations",
            description = "ILM 2.19+: discover issue/renew/revoke/register support and asynchronous/cancellation capabilities for an authority and RA profile.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificateOperations(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid) {
        return service.getCertificateOperations(authorityUuid, raProfileUuid);
    }

    @McpTool(name = "get_certificate_operation_attributes",
            description = "Discover provider fields for issue/revoke (ILM 2.17+), register (2.19+), renew/identify (2.20+), resolving callbacks from optional plain values.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificateOperationAttributes(
            @McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "issue, revoke, register, renew or identify") String operation,
            @McpToolParam(description = "Optional plain attribute values JSON", required = false) String values) {
        return service.getCertificateOperationAttributes(authorityUuid, raProfileUuid, operation, values);
    }

    @McpTool(name = "get_csr_attributes",
            description = "Discover CSR request fields. Optional RA profile resolved schemas require ILM 2.19 or later.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCsrAttributes(
            @McpToolParam(description = "Optional RA profile UUID", required = false) String raProfileUuid) {
        return service.getCsrAttributes(raProfileUuid);
    }

    @McpTool(name = "create_certificate_request",
            description = "Create a certificate request without issuing it. Optional raProfileUuid/sourceCertificateUuid, issueAttributes and customAttributes. JSON fields request (base64 CSR), format (pkcs10/crmf), or tokenProfileUuid/keyUuid with csrAttributes/signatureAttributes; optional altTokenProfileUuid/altKeyUuid/altSignatureAttributes. Provider/custom attributes are plain name-to-value objects.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createCertificateRequest(@McpToolParam(
            description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.createCertificateRequest(configuration);
    }

    @McpTool(name = "issue_certificate",
            description = "Request issuance of a new certificate; output reports accepted UUID/current state and may await approval or processing. JSON fields request (base64 CSR), format (pkcs10/crmf), or tokenProfileUuid/keyUuid with csrAttributes/signatureAttributes; optional altTokenProfileUuid/altKeyUuid/altSignatureAttributes. Provider/custom attributes are plain name-to-value objects.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String issueCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.issueCertificate(authorityUuid, raProfileUuid, configuration);
    }

    @McpTool(name = "issue_existing_certificate",
            description = "Request issuance for an existing REQUESTED certificate with no body. ILM 2.19+ registered completion accepts a CSR body and optional authorizationSecret; inspect status before treating issuance as complete.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String issueExistingCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(description = "Optional issue configuration JSON; omit for REQUESTED state",
                    required = false) String configuration) {
        return service.issueExistingCertificate(authorityUuid, raProfileUuid, certificateUuid, configuration);
    }

    @McpTool(name = "register_certificate",
            description = "ILM 2.19+: register subject identity before a CSR exists. JSON fields subjectDn/subjectAltName/extensions or csrAttributes; attributes/raProfileAttributes/customAttributes, ownerUuid/groupUuids/sourceCertificateUuid, optional authorizationSecret and future expiresAt. Ordinary output never returns the secret.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String registerCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.registerCertificate(authorityUuid, raProfileUuid, configuration);
    }

    @McpTool(name = "renew_certificate",
            description = "Request certificate renewal with optional replaceInLocations, request and format. Provider attributes/authorizationSecret require ILM 2.20+. Reports acceptance/current state, not issuance completion.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String renewCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.renewCertificate(authorityUuid, raProfileUuid, certificateUuid, configuration);
    }

    @McpTool(name = "rekey_certificate",
            description = "Request certificate rekeying using a CSR or configured key. Optional replaceInLocations; provider attributes/authorizationSecret require ILM 2.20+. JSON fields request (base64 CSR), format (pkcs10/crmf), or tokenProfileUuid/keyUuid with csrAttributes/signatureAttributes; optional altTokenProfileUuid/altKeyUuid/altSignatureAttributes. Provider/custom attributes are plain name-to-value objects.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String rekeyCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.rekeyCertificate(authorityUuid, raProfileUuid, certificateUuid, configuration);
    }

    @McpTool(name = "finalize_certificate",
            description = "ILM 2.18+: manually finalize pending issuance with certificate and customAttributes. identifyAttributes requires ILM 2.20+. Reports actual certificate state.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String finalizeCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.finalizeCertificate(authorityUuid, raProfileUuid, certificateUuid, configuration);
    }

    @McpTool(name = "revoke_certificate",
            description = "Request revocation with reason and optional attributes/destroyKey. Revocation and optional provider-key destruction may be irreversible; output reports acceptance/current state.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String revokeCertificate(@McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid, @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.revokeCertificate(authorityUuid, raProfileUuid, certificateUuid, configuration);
    }

    @McpTool(name = "confirm_certificate_revocation",
            description = "ILM 2.18+: manually confirm a pending certificate revocation.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String confirmCertificateRevocation(
            @McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid) {
        return service.confirmCertificateRevocation(authorityUuid, raProfileUuid, certificateUuid);
    }

    @McpTool(name = "cancel_certificate_operation",
            description = "ILM 2.18+: request cancellation of a pending certificate operation, subject to provider support.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String cancelCertificateOperation(
            @McpToolParam(description = "Authority instance UUID") String authorityUuid,
            @McpToolParam(description = "RA profile UUID") String raProfileUuid,
            @McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(description = "Optional cancellation reason", required = false) String reason) {
        return service.cancelCertificateOperation(authorityUuid, raProfileUuid, certificateUuid, reason);
    }

    @McpTool(name = "upload_certificate",
            description = "Upload a public certificate with schema-validated custom values. Async upload requires ILM 2.18+ and returns fingerprint/pending state.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String uploadCertificate(
            @McpToolParam(description = "Certificate data supported by ILM upload") String certificate,
            @McpToolParam(description = "Optional plain custom attribute values JSON",
                    required = false) String customAttributes,
            @McpToolParam(description = "True for asynchronous processing") boolean asynchronous) {
        return service.uploadCertificate(certificate, customAttributes, asynchronous);
    }

    @McpTool(name = "import_certificates",
            description = "ILM 2.20+: import selected certificate/container entries. JSON file is base64 (at most 5 MiB), optional passphrase, nonempty entries {entryReference,keyDestination?}; destination fields tokenProfileUuid,keyName,exportable,keyType (keyPair/secret; default keyPair),importAttributes/customAttributes plain values. Exportable=false is final. Ordinary result contains identifiers/outcomes only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String importCertificates(@McpToolParam(
            description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.importCertificates(configuration);
    }

    @McpTool(name = "export_certificate",
            description = "Export a public certificate or public chain as ILM content with format raw/pkcs7 and encoding pem/der. Contains no private key.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String exportCertificate(@McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(description = "raw or pkcs7") String format,
            @McpToolParam(description = "pem or der") String encoding,
            @McpToolParam(description = "True to export certificate chain") boolean chain, @McpToolParam(
                    description = "Include end certificate when exporting a chain") boolean includeEndCertificate) {
        return service.exportCertificate(certificateUuid, format, encoding, chain, includeEndCertificate);
    }

    @SensitiveOutput
    @McpTool(name = "export_certificate_keystore",
            description = "At full access, ILM 2.20+: export certificate and private key in the platform PKCS12 keystore. JSON passphrase must be NFC and at least 12 code points; optional exportAttributes are plain provider values. Returns filename/media type/base64 bytes.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String exportCertificateKeystore(@McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.exportCertificateKeystore(certificateUuid, configuration);
    }

    @McpTool(name = "update_certificate_metadata",
            description = "Change exactly one of groupUuids, ownerUuid, raProfileUuid or trustedCa in a targeted PATCH. Empty UUID string removes owner/RA; empty groups list removes all groups. Optional identify attributes with RA reassignment require ILM 2.20+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateCertificateMetadata(@McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(
                    description = "JSON object with the documented fields. attributes/customAttributes/operation attributes are plain schema attribute-name to value objects, never request envelopes.") String configuration) {
        return service.updateCertificateMetadata(certificateUuid, configuration);
    }

    @McpTool(name = "archive_certificate", description = "Archive a certificate in inventory.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String archiveCertificate(@McpToolParam(description = "Certificate UUID") String certificateUuid) {
        return service.setCertificateArchived(certificateUuid, true);
    }

    @McpTool(name = "unarchive_certificate", description = "Restore a certificate from archive.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String unarchiveCertificate(@McpToolParam(description = "Certificate UUID") String certificateUuid) {
        return service.setCertificateArchived(certificateUuid, false);
    }

    @McpTool(name = "delete_certificate",
            description = "Delete a certificate from ILM inventory; platform dependencies apply. This does not revoke the certificate.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteCertificate(@McpToolParam(description = "Certificate UUID") String certificateUuid) {
        return service.deleteCertificate(certificateUuid);
    }

    @McpTool(name = "get_certificate_relations",
            description = "Inspect predecessor and successor certificate relations.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificateRelations(@McpToolParam(description = "Certificate UUID") String certificateUuid) {
        return service.getCertificateRelations(certificateUuid);
    }

    @McpTool(name = "set_certificate_relation",
            description = "Associate or disassociate two certificates using one targeted relation endpoint.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setCertificateRelation(@McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(description = "Related certificate UUID") String relatedUuid,
            @McpToolParam(description = "True to associate; false to remove") boolean associate) {
        return service.setCertificateRelation(certificateUuid, relatedUuid, associate);
    }

    @McpTool(name = "get_certificate_approvals", description = "List approval requests for one certificate.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificateApprovals(@McpToolParam(description = "Certificate UUID") String certificateUuid,
            @McpToolParam(description = "Page number starting at 1") int page,
            @McpToolParam(description = "Items per page, at most 1000") int size) {
        return service.getCertificateApprovals(certificateUuid, page, size);
    }
}
