package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.CertificateResponseDto;
import com.otilm.api.model.client.certificate.CertificateSearchRequestDto;
import com.otilm.api.model.client.dashboard.StatisticsDto;
import com.otilm.api.model.core.certificate.CertificateChainResponseDto;
import com.otilm.api.model.core.certificate.CertificateDetailDto;
import com.otilm.api.model.core.certificate.CertificateDto;
import com.otilm.api.model.core.certificate.CertificateEventHistoryDto;
import com.otilm.api.model.core.certificate.CertificateValidationResultDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.counts;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.nameOf;
import static com.otilm.mcp.service.TextFormat.names;
import static com.otilm.mcp.service.TextFormat.orNa;
import static com.otilm.mcp.service.TextFormat.pagedList;
import static com.otilm.mcp.service.TextFormat.title;

@Service
public class CertificateServiceImpl implements CertificateService {

    private static final String INDENT = "  ";
    private static final String CHAIN_INDENT = "    ";
    private static final String SERIAL = "Serial";
    private static final String STATUS = "Status";

    private final IlmApiClient ilmApiClient;

    public CertificateServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String getStatistics() {
        return ToolResponses.run("retrieving statistics", () -> formatStatistics(ilmApiClient.getStatistics()));
    }

    @Override
    public String searchCertificates(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ToolResponses.run("searching certificates", () -> {
            CertificateResponseDto response = ilmApiClient
                    .searchCertificates(
                            SearchRequests.of(new CertificateSearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(response.getTotalItems(), "certificates", response.getPageNumber(),
                    response.getTotalPages(), response.getCertificates(), CertificateServiceImpl::formatCertificate);
        });
    }

    @Override
    public String getCertificate(String uuid) {
        return ToolResponses
                .run("retrieving certificate", () -> formatCertificateDetail(ilmApiClient.getCertificate(uuid)));
    }

    @Override
    public String validateCertificate(String uuid) {
        return ToolResponses
                .run("validating certificate",
                        () -> formatValidationResult(uuid, ilmApiClient.validateCertificate(uuid)));
    }

    @Override
    public String getCertificateChain(String uuid) {
        return ToolResponses
                .run("retrieving certificate chain",
                        () -> formatCertificateChain(uuid, ilmApiClient.getCertificateChain(uuid)));
    }

    @Override
    public String getCertificateHistory(String uuid) {
        return ToolResponses
                .run("retrieving certificate history",
                        () -> formatCertificateHistory(uuid, ilmApiClient.getCertificateHistory(uuid)));
    }

    private static String formatStatistics(StatisticsDto stats) {
        StringBuilder sb = new StringBuilder();
        title(sb, "ILM Platform Statistics");
        field(sb, "", "Total Certificates", stats.getTotalCertificates());
        field(sb, "", "Total Groups", stats.getTotalGroups());
        field(sb, "", "Total Discoveries", stats.getTotalDiscoveries());
        field(sb, "", "Total Connectors", stats.getTotalConnectors());
        field(sb, "", "Total RA Profiles", stats.getTotalRaProfiles());
        field(sb, "", "Total Authorities", stats.getTotalAuthorities());
        field(sb, "", "Total Credentials", stats.getTotalCredentials());
        field(sb, "", "Total Secrets", stats.getTotalSecrets());
        field(sb, "", "Total Vault Instances", stats.getTotalVaultInstances());
        field(sb, "", "Total Vault Profiles", stats.getTotalVaultProfiles());
        counts(sb, "Certificates by State", stats.getCertificateStatByState());
        counts(sb, "Certificates by Key Size", stats.getCertificateStatByKeySize());
        counts(sb, "Certificates by Expiry", stats.getCertificateStatByExpiry());
        counts(sb, "Certificates by Validation Status", stats.getCertificateStatByValidationStatus());
        counts(sb, "Certificates by Compliance Status", stats.getCertificateStatByComplianceStatus());
        counts(sb, "Secrets by Type", stats.getSecretStatByType());
        counts(sb, "Secrets by State", stats.getSecretStatByState());
        counts(sb, "Secrets by Compliance Status", stats.getSecretStatByComplianceStatus());
        counts(sb, "Secrets by Vault Profile", stats.getSecretStatByVaultProfile());
        counts(sb, "Secrets by Group", stats.getSecretStatByGroup());
        return sb.toString();
    }

    private static void formatCertificate(StringBuilder sb, CertificateDto cert) {
        item(sb, cert.getCommonName(), cert.getUuid());
        field(sb, INDENT, SERIAL, cert.getSerialNumber());
        field(sb, INDENT, STATUS, code(cert.getState()));
        field(sb, INDENT, "Validation", code(cert.getValidationStatus()));
        field(sb, INDENT, "Algorithm", algorithm(cert.getPublicKeyAlgorithm(), cert.getKeySize()));
        field(sb, INDENT, "Expires", cert.getNotAfter());
    }

    private static String formatCertificateDetail(CertificateDetailDto cert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Certificate: ").append(orNa(cert.getCommonName())).append("\n");
        field(sb, INDENT, "UUID", cert.getUuid());
        field(sb, INDENT, SERIAL, cert.getSerialNumber());
        field(sb, INDENT, "Subject DN", cert.getSubjectDn());
        field(sb, INDENT, "Issuer", orNa(cert.getIssuerCommonName()));
        field(sb, INDENT, "Issuer DN", cert.getIssuerDn());
        if (cert.getNotBefore() != null && cert.getNotAfter() != null) {
            field(sb, INDENT, "Valid", cert.getNotBefore() + " to " + cert.getNotAfter());
        }
        field(sb, INDENT, "State", code(cert.getState()));
        field(sb, INDENT, "Validation", code(cert.getValidationStatus()));
        field(sb, INDENT, "Key Algorithm", algorithm(cert.getPublicKeyAlgorithm(), cert.getKeySize()));
        field(sb, INDENT, "Signature Algorithm", cert.getSignatureAlgorithm());
        field(sb, INDENT, "Compliance", code(cert.getComplianceStatus()));
        field(sb, INDENT, "Fingerprint", cert.getFingerprint());
        field(sb, INDENT, "RA Profile", nameOf(cert.getRaProfile()));
        field(sb, INDENT, "Groups", names(cert.getGroups()));
        return sb.toString();
    }

    private static String algorithm(String algorithm, Integer keySize) {
        return keySize == null ? orNa(algorithm) : orNa(algorithm) + " " + keySize;
    }

    private static String formatValidationResult(String uuid, CertificateValidationResultDto result) {
        StringBuilder sb = new StringBuilder();
        title(sb, "Validation Result for Certificate " + uuid);
        field(sb, "", STATUS, result.getResultStatus());
        field(sb, "", "Message", result.getMessage());
        if (result.getValidationChecks() != null) {
            sb.append("\nValidation Checks:\n");
            result
                    .getValidationChecks()
                    .forEach((check, checkDto) -> sb
                            .append(INDENT)
                            .append(check)
                            .append(": ")
                            .append(checkDto.getStatus())
                            .append(checkDto.getMessage() != null ? " - " + checkDto.getMessage() : "")
                            .append("\n"));
        }
        return sb.toString();
    }

    private static String formatCertificateChain(String uuid, CertificateChainResponseDto chain) {
        StringBuilder sb = new StringBuilder();
        title(sb, "Certificate Chain for " + uuid);
        sb.append("Complete chain: ").append(chain.isCompleteChain()).append("\n\n");
        if (chain.getCertificates() != null) {
            int index = 0;
            for (CertificateDetailDto cert : chain.getCertificates()) {
                sb.append("[").append(index++).append("] ").append(orNa(cert.getCommonName())).append("\n");
                field(sb, CHAIN_INDENT, "Subject", cert.getSubjectDn());
                field(sb, CHAIN_INDENT, "Issuer", cert.getIssuerDn());
                field(sb, CHAIN_INDENT, SERIAL, cert.getSerialNumber());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String formatCertificateHistory(String uuid, List<CertificateEventHistoryDto> history) {
        StringBuilder sb = new StringBuilder();
        title(sb, "Event History for Certificate " + uuid);
        if (history == null || history.isEmpty()) {
            return sb.append("No events found.\n").toString();
        }
        for (CertificateEventHistoryDto event : history) {
            sb
                    .append("- ")
                    .append(event.getEvent() == null ? TextFormat.NOT_AVAILABLE : event.getEvent().getCode())
                    .append("\n");
            field(sb, INDENT, STATUS, event.getStatus());
            field(sb, INDENT, "Time", event.getCreated());
            field(sb, INDENT, "By", event.getCreatedBy());
            field(sb, INDENT, "Message", event.getMessage());
            sb.append("\n");
        }
        return sb.toString();
    }
}
