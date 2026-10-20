package com.otilm.mcp.service;

import com.czertainly.api.model.client.certificate.CertificateResponseDto;
import com.czertainly.api.model.client.certificate.CertificateSearchRequestDto;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateDto;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateValidationResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CertificateServiceImpl implements CertificateService {

    private static final Logger logger = LoggerFactory.getLogger(CertificateServiceImpl.class);

    private final IlmApiClient ilmApiClient;
    private final ObjectMapper objectMapper;

    public CertificateServiceImpl(IlmApiClient ilmApiClient, ObjectMapper objectMapper) {
        this.ilmApiClient = ilmApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getStatistics() {
        try {
            StatisticsDto stats = ilmApiClient.getStatistics();
            return formatStatistics(stats);
        } catch (Exception e) {
            logger.error("Failed to get statistics", e);
            return "Error retrieving statistics: " + e.getMessage();
        }
    }

    @Override
    public String searchCertificates(String filters, Integer itemsPerPage, Integer pageNumber) {
        try {
            CertificateSearchRequestDto request = new CertificateSearchRequestDto();
            if (itemsPerPage != null) {
                request.setItemsPerPage(itemsPerPage);
            }
            if (pageNumber != null) {
                request.setPageNumber(pageNumber);
            }
            request.setFilters(SearchFilterParser.parseFilters(filters, objectMapper));

            CertificateResponseDto response = ilmApiClient.searchCertificates(request);
            return formatCertificateList(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to search certificates", e);
            return "Error searching certificates: " + e.getMessage();
        }
    }

    @Override
    public String getCertificate(String uuid) {
        try {
            CertificateDetailDto cert = ilmApiClient.getCertificate(uuid);
            return formatCertificateDetail(cert);
        } catch (Exception e) {
            logger.error("Failed to get certificate {}", uuid, e);
            return "Error retrieving certificate: " + e.getMessage();
        }
    }

    @Override
    public String validateCertificate(String uuid) {
        try {
            CertificateValidationResultDto result = ilmApiClient.validateCertificate(uuid);
            return formatValidationResult(uuid, result);
        } catch (Exception e) {
            logger.error("Failed to validate certificate {}", uuid, e);
            return "Error validating certificate: " + e.getMessage();
        }
    }

    @Override
    public String getCertificateChain(String uuid) {
        try {
            CertificateChainResponseDto chain = ilmApiClient.getCertificateChain(uuid);
            return formatCertificateChain(uuid, chain);
        } catch (Exception e) {
            logger.error("Failed to get certificate chain {}", uuid, e);
            return "Error retrieving certificate chain: " + e.getMessage();
        }
    }

    @Override
    public String getCertificateHistory(String uuid) {
        try {
            List<CertificateEventHistoryDto> history = ilmApiClient.getCertificateHistory(uuid);
            return formatCertificateHistory(uuid, history);
        } catch (Exception e) {
            logger.error("Failed to get certificate history {}", uuid, e);
            return "Error retrieving certificate history: " + e.getMessage();
        }
    }

    private String formatStatistics(StatisticsDto stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("ILM Platform Statistics\n");
        sb.append("=======================\n\n");
        sb.append("Total Certificates: ").append(stats.getTotalCertificates()).append("\n");
        sb.append("Total Groups: ").append(stats.getTotalGroups()).append("\n");
        sb.append("Total Discoveries: ").append(stats.getTotalDiscoveries()).append("\n");
        sb.append("Total Connectors: ").append(stats.getTotalConnectors()).append("\n");
        sb.append("Total RA Profiles: ").append(stats.getTotalRaProfiles()).append("\n");
        sb.append("Total Authorities: ").append(stats.getTotalAuthorities()).append("\n");
        sb.append("Total Credentials: ").append(stats.getTotalCredentials()).append("\n");

        if (stats.getTotalSecrets() != null) {
            sb.append("Total Secrets: ").append(stats.getTotalSecrets()).append("\n");
        }
        if (stats.getTotalVaultInstances() != null) {
            sb.append("Total Vault Instances: ").append(stats.getTotalVaultInstances()).append("\n");
        }
        if (stats.getTotalVaultProfiles() != null) {
            sb.append("Total Vault Profiles: ").append(stats.getTotalVaultProfiles()).append("\n");
        }

        appendMapSection(sb, "Certificates by State", stats.getCertificateStatByState());
        appendMapSection(sb, "Certificates by Key Size", stats.getCertificateStatByKeySize());
        appendMapSection(sb, "Certificates by Expiry", stats.getCertificateStatByExpiry());
        appendMapSection(sb, "Certificates by Validation Status", stats.getCertificateStatByValidationStatus());
        appendMapSection(sb, "Certificates by Compliance Status", stats.getCertificateStatByComplianceStatus());

        appendMapSection(sb, "Secrets by Type", stats.getSecretStatByType());
        appendMapSection(sb, "Secrets by State", stats.getSecretStatByState());
        appendMapSection(sb, "Secrets by Compliance Status", stats.getSecretStatByComplianceStatus());
        appendMapSection(sb, "Secrets by Vault Profile", stats.getSecretStatByVaultProfile());
        appendMapSection(sb, "Secrets by Group", stats.getSecretStatByGroup());

        return sb.toString();
    }

    private void appendMapSection(StringBuilder sb, String title, Map<String, Long> map) {
        if (map != null && !map.isEmpty()) {
            sb.append("\n").append(title).append(":\n");
            map.forEach((key, value) -> sb.append("  ").append(key).append(": ").append(value).append("\n"));
        }
    }

    private String formatCertificateList(CertificateResponseDto response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(response.getTotalItems()).append(" certificates");
        sb
                .append(" (page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n\n");

        for (CertificateDto cert : response.getCertificates()) {
            sb.append("- ").append(cert.getCommonName() != null ? cert.getCommonName() : "N/A");
            sb.append(" [").append(cert.getUuid()).append("]\n");
            sb.append("  Serial: ").append(cert.getSerialNumber()).append("\n");
            sb.append("  Status: ").append(cert.getState() != null ? cert.getState().getCode() : "N/A").append("\n");
            sb
                    .append("  Validation: ")
                    .append(cert.getValidationStatus() != null ? cert.getValidationStatus().getCode() : "N/A")
                    .append("\n");
            sb.append("  Algorithm: ").append(cert.getPublicKeyAlgorithm());
            if (cert.getKeySize() != null) {
                sb.append(" ").append(cert.getKeySize());
            }
            sb.append("\n");
            if (cert.getNotAfter() != null) {
                sb.append("  Expires: ").append(cert.getNotAfter()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatCertificateDetail(CertificateDetailDto cert) {
        StringBuilder sb = new StringBuilder();
        sb.append("Certificate: ").append(cert.getCommonName() != null ? cert.getCommonName() : "N/A").append("\n");
        sb.append("  UUID: ").append(cert.getUuid()).append("\n");
        sb.append("  Serial: ").append(cert.getSerialNumber()).append("\n");
        sb.append("  Subject DN: ").append(cert.getSubjectDn()).append("\n");
        sb
                .append("  Issuer: ")
                .append(cert.getIssuerCommonName() != null ? cert.getIssuerCommonName() : "N/A")
                .append("\n");
        sb.append("  Issuer DN: ").append(cert.getIssuerDn()).append("\n");
        if (cert.getNotBefore() != null && cert.getNotAfter() != null) {
            sb.append("  Valid: ").append(cert.getNotBefore()).append(" to ").append(cert.getNotAfter()).append("\n");
        }
        sb.append("  State: ").append(cert.getState() != null ? cert.getState().getCode() : "N/A").append("\n");
        sb
                .append("  Validation: ")
                .append(cert.getValidationStatus() != null ? cert.getValidationStatus().getCode() : "N/A")
                .append("\n");
        sb.append("  Key Algorithm: ").append(cert.getPublicKeyAlgorithm());
        if (cert.getKeySize() != null) {
            sb.append(" ").append(cert.getKeySize());
        }
        sb.append("\n");
        if (cert.getSignatureAlgorithm() != null) {
            sb.append("  Signature Algorithm: ").append(cert.getSignatureAlgorithm()).append("\n");
        }
        sb
                .append("  Compliance: ")
                .append(cert.getComplianceStatus() != null ? cert.getComplianceStatus().getCode() : "N/A")
                .append("\n");
        if (cert.getFingerprint() != null) {
            sb.append("  Fingerprint: ").append(cert.getFingerprint()).append("\n");
        }
        if (cert.getRaProfile() != null) {
            sb.append("  RA Profile: ").append(cert.getRaProfile().getName()).append("\n");
        }
        if (cert.getGroups() != null && !cert.getGroups().isEmpty()) {
            sb.append("  Groups: ");
            sb.append(String.join(", ", cert.getGroups().stream().map(g -> g.getName()).toList()));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatValidationResult(String uuid, CertificateValidationResultDto result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Validation Result for Certificate ").append(uuid).append("\n");
        sb.append("===================================\n\n");
        sb.append("Status: ").append(result.getResultStatus()).append("\n");
        if (result.getMessage() != null) {
            sb.append("Message: ").append(result.getMessage()).append("\n");
        }

        if (result.getValidationChecks() != null) {
            sb.append("\nValidation Checks:\n");
            result
                    .getValidationChecks()
                    .forEach((check, checkDto) -> sb
                            .append("  ")
                            .append(check)
                            .append(": ")
                            .append(checkDto.getStatus())
                            .append(checkDto.getMessage() != null ? " - " + checkDto.getMessage() : "")
                            .append("\n"));
        }
        return sb.toString();
    }

    private String formatCertificateChain(String uuid, CertificateChainResponseDto chain) {
        StringBuilder sb = new StringBuilder();
        sb.append("Certificate Chain for ").append(uuid).append("\n");
        sb.append("================================\n");
        sb.append("Complete chain: ").append(chain.isCompleteChain()).append("\n\n");

        if (chain.getCertificates() != null) {
            int index = 0;
            for (CertificateDetailDto cert : chain.getCertificates()) {
                sb.append("[").append(index++).append("] ");
                sb.append(cert.getCommonName() != null ? cert.getCommonName() : "N/A").append("\n");
                sb.append("    Subject: ").append(cert.getSubjectDn()).append("\n");
                sb.append("    Issuer: ").append(cert.getIssuerDn()).append("\n");
                sb.append("    Serial: ").append(cert.getSerialNumber()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private String formatCertificateHistory(String uuid, List<CertificateEventHistoryDto> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event History for Certificate ").append(uuid).append("\n");
        sb.append("====================================\n\n");

        if (history == null || history.isEmpty()) {
            sb.append("No events found.\n");
            return sb.toString();
        }

        for (CertificateEventHistoryDto event : history) {
            sb.append("- ").append(event.getEvent() != null ? event.getEvent().getCode() : "N/A").append("\n");
            sb.append("  Status: ").append(event.getStatus()).append("\n");
            sb.append("  Time: ").append(event.getCreated()).append("\n");
            sb.append("  By: ").append(event.getCreatedBy()).append("\n");
            if (event.getMessage() != null) {
                sb.append("  Message: ").append(event.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
