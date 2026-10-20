package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.secret.SecretDetailDto;
import com.otilm.api.model.core.secret.SecretDto;
import com.otilm.api.model.core.secret.SecretVersionDto;
import com.otilm.mcp.client.IlmApiClient;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SecretServiceImpl implements SecretService {

    private static final Logger logger = LoggerFactory.getLogger(SecretServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final IlmApiClient ilmApiClient;

    public SecretServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String searchSecrets(String filters, Integer itemsPerPage, Integer pageNumber) {
        try {
            SearchRequestDto request = new SearchRequestDto();
            if (itemsPerPage != null) {
                request.setItemsPerPage(itemsPerPage);
            }
            if (pageNumber != null) {
                request.setPageNumber(pageNumber);
            }
            request.setFilters(SearchFilterParser.parseFilters(filters));

            PaginationResponseDto<SecretDto> response = ilmApiClient.searchSecrets(request);
            return formatSecretList(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to search secrets", e);
            return "Error searching secrets: " + e.getMessage();
        }
    }

    @Override
    public String getSecret(String uuid) {
        try {
            SecretDetailDto secret = ilmApiClient.getSecret(uuid);
            return formatSecretDetail(secret);
        } catch (Exception e) {
            logger.error("Failed to get secret {}", uuid, e);
            return "Error retrieving secret: " + e.getMessage();
        }
    }

    @Override
    public String getSecretVersions(String uuid) {
        try {
            List<SecretVersionDto> versions = ilmApiClient.getSecretVersions(uuid);
            return formatSecretVersions(uuid, versions);
        } catch (Exception e) {
            logger.error("Failed to get secret versions {}", uuid, e);
            return "Error retrieving secret versions: " + e.getMessage();
        }
    }

    private String formatSecretList(PaginationResponseDto<SecretDto> response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(response.getTotalItems()).append(" secrets");
        sb
                .append(" (page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n\n");

        for (SecretDto secret : response.getItems()) {
            sb.append("- ").append(secret.getName() != null ? secret.getName() : "N/A");
            sb.append(" [").append(secret.getUuid()).append("]\n");
            sb.append("  Type: ").append(secret.getType() != null ? secret.getType().getCode() : "N/A").append("\n");
            sb.append("  State: ").append(secret.getState() != null ? secret.getState().getCode() : "N/A").append("\n");
            sb.append("  Version: ").append(secret.getVersion()).append("\n");
            sb.append("  Enabled: ").append(secret.isEnabled()).append("\n");
            if (secret.getOwner() != null) {
                sb.append("  Owner: ").append(secret.getOwner().getName()).append("\n");
            }
            if (secret.getSourceVaultProfile() != null) {
                sb.append("  Vault Profile: ").append(secret.getSourceVaultProfile().getName()).append("\n");
            }
            if (secret.getGroups() != null && !secret.getGroups().isEmpty()) {
                sb.append("  Groups: ");
                sb.append(String.join(", ", secret.getGroups().stream().map(g -> g.getName()).toList()));
                sb.append("\n");
            }
            if (secret.getComplianceStatus() != null) {
                sb.append("  Compliance: ").append(secret.getComplianceStatus().getCode()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatSecretDetail(SecretDetailDto secret) {
        StringBuilder sb = new StringBuilder();
        sb.append("Secret: ").append(secret.getName() != null ? secret.getName() : "N/A").append("\n");
        sb.append("  UUID: ").append(secret.getUuid()).append("\n");
        if (secret.getDescription() != null) {
            sb.append("  Description: ").append(secret.getDescription()).append("\n");
        }
        sb.append("  Type: ").append(secret.getType() != null ? secret.getType().getCode() : "N/A").append("\n");
        sb.append("  State: ").append(secret.getState() != null ? secret.getState().getCode() : "N/A").append("\n");
        sb.append("  Version: ").append(secret.getVersion()).append("\n");
        sb.append("  Enabled: ").append(secret.isEnabled()).append("\n");
        if (secret.getOwner() != null) {
            sb.append("  Owner: ").append(secret.getOwner().getName()).append("\n");
        }
        if (secret.getSourceVaultProfile() != null) {
            sb.append("  Source Vault Profile: ").append(secret.getSourceVaultProfile().getName()).append("\n");
        }
        if (secret.getGroups() != null && !secret.getGroups().isEmpty()) {
            sb.append("  Groups: ");
            sb.append(String.join(", ", secret.getGroups().stream().map(g -> g.getName()).toList()));
            sb.append("\n");
        }
        sb
                .append("  Compliance: ")
                .append(secret.getComplianceStatus() != null ? secret.getComplianceStatus().getCode() : "N/A")
                .append("\n");
        if (secret.getCreatedAt() != null) {
            sb.append("  Created: ").append(formatDateTime(secret.getCreatedAt())).append("\n");
        }
        if (secret.getUpdatedAt() != null) {
            sb.append("  Updated: ").append(formatDateTime(secret.getUpdatedAt())).append("\n");
        }

        if (secret.getSyncVaultProfiles() != null && !secret.getSyncVaultProfiles().isEmpty()) {
            sb.append("\n  Sync Vault Profiles:\n");
            secret
                    .getSyncVaultProfiles()
                    .forEach(svp -> sb
                            .append("    - ")
                            .append(svp.getName())
                            .append(" [")
                            .append(svp.getUuid())
                            .append("]\n"));
        }

        return sb.toString();
    }

    private String formatSecretVersions(String uuid, List<SecretVersionDto> versions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Secret Versions for ").append(uuid).append("\n");
        sb.append("================================\n");
        sb.append(versions.size()).append(" versions\n\n");

        for (SecretVersionDto version : versions) {
            sb.append("- Version: ").append(version.getVersion()).append("\n");
            if (version.getCreatedAt() != null) {
                sb.append("  Created: ").append(formatDateTime(version.getCreatedAt())).append("\n");
            }
            if (version.getFingerprint() != null) {
                sb.append("  Fingerprint: ").append(version.getFingerprint()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatDateTime(OffsetDateTime dateTime) {
        return dateTime.format(DATE_FORMATTER);
    }
}
