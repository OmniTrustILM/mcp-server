package com.otilm.mcp.tool;

import com.otilm.mcp.service.CertificateService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CertificateTool {

    private final CertificateService certificateService;

    public CertificateTool(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @McpTool(name = "get_statistics",
            description = "Get overall statistics and dashboard summary of the ILM platform including total counts of certificates, groups, discoveries, connectors, RA profiles, authorities, and credentials, as well as certificate breakdowns by state, key size, expiry, validation status, and compliance status. Use this to get a high-level overview of the platform.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getStatistics() {
        return certificateService.getStatistics();
    }

    @McpTool(name = "search_certificates",
            description = "Search and list certificates in the ILM platform with optional filters and pagination. Use get_searchable_fields('certificates') to discover available filter fields. Returns certificate common name, UUID, serial number, status, validation status, algorithm, key size, and expiry date.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String searchCertificates(@McpToolParam(
            description = "JSON array of search filters. Each filter: {\"fieldSource\":\"property\",\"fieldIdentifier\":\"<field>\",\"condition\":\"<operator>\",\"value\":\"<value>\"}. Use get_searchable_fields('certificates') to see available fields and operators.",
            required = false) String filters,
            @McpToolParam(description = "Number of items per page (default: 10)",
                    required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number for pagination (default: 1)",
                    required = false) Integer pageNumber) {
        return certificateService.searchCertificates(filters, itemsPerPage, pageNumber);
    }

    @McpTool(name = "get_certificate",
            description = "Get detailed information about a specific certificate by its UUID. Returns full certificate details including subject DN, issuer, validity period, state, validation status, key algorithm, signature algorithm, compliance status, fingerprint, RA profile, and groups. Use this when you need complete details about a particular certificate.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificate(@McpToolParam(description = "The UUID of the certificate to retrieve") String uuid) {
        return certificateService.getCertificate(uuid);
    }

    @McpTool(name = "validate_certificate",
            description = "Validate a specific certificate by its UUID. Returns the overall validation status and detailed validation check results including individual check statuses and messages. Use this to verify if a certificate is valid, check for issues, or audit certificate health.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String validateCertificate(
            @McpToolParam(description = "The UUID of the certificate to validate") String uuid) {
        return certificateService.validateCertificate(uuid);
    }

    @McpTool(name = "get_certificate_chain",
            description = "Get the full certificate chain for a specific certificate by its UUID. Returns whether the chain is complete and lists all certificates in the chain from end-entity to root, with subject, issuer, and serial number for each. Use this to inspect the trust chain or diagnose chain-related issues.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificateChain(
            @McpToolParam(description = "The UUID of the certificate whose chain to retrieve") String uuid) {
        return certificateService.getCertificateChain(uuid);
    }

    @McpTool(name = "get_certificate_history",
            description = "Get the event history for a specific certificate by its UUID. Returns a chronological list of events including event type, status, timestamp, who performed it, and any associated messages. Use this to audit certificate lifecycle events or troubleshoot certificate issues.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getCertificateHistory(
            @McpToolParam(description = "The UUID of the certificate whose history to retrieve") String uuid) {
        return certificateService.getCertificateHistory(uuid);
    }
}
