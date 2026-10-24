package com.otilm.mcp.tool;

import com.otilm.mcp.service.TrustService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TrustTool {
    private final TrustService service;

    public TrustTool(TrustService service) {
        this.service = service;
    }

    @McpTool(name = "list_trusted_certificates", description = "List trusted certificate metadata (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listTrustedCertificates() {
        return service.listTrustedCertificates();
    }

    @McpTool(name = "get_trusted_certificate", description = "Get trusted certificate metadata (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTrustedCertificate(
            @McpToolParam(description = "Trusted certificate UUID", required = true) String uuid) {
        return service.getTrustedCertificate(uuid);
    }

    @McpTool(name = "add_trusted_certificate",
            description = "Add a trusted certificate (ILM 2.18+). Returns metadata only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String addTrustedCertificate(
            @McpToolParam(description = "Base64 encoded certificate file, at most 5 MiB decoded",
                    required = true) String certificateContent) {
        return service.addTrustedCertificate(certificateContent);
    }

    @McpTool(name = "delete_trusted_certificate",
            description = "Remove a certificate from the trusted store (ILM 2.18+).",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTrustedCertificate(
            @McpToolParam(description = "Trusted certificate UUID", required = true) String uuid) {
        return service.deleteTrustedCertificate(uuid);
    }
}
