package com.otilm.mcp.service;

import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.vault.VaultInstanceDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.mcp.client.IlmApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VaultServiceImpl implements VaultService {

    private static final Logger logger = LoggerFactory.getLogger(VaultServiceImpl.class);

    private final IlmApiClient ilmApiClient;
    private final ObjectMapper objectMapper;

    public VaultServiceImpl(IlmApiClient ilmApiClient, ObjectMapper objectMapper) {
        this.ilmApiClient = ilmApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String listVaultInstances(String filters, Integer itemsPerPage, Integer pageNumber) {
        try {
            SearchRequestDto request = new SearchRequestDto();
            if (itemsPerPage != null) {
                request.setItemsPerPage(itemsPerPage);
            }
            if (pageNumber != null) {
                request.setPageNumber(pageNumber);
            }
            request.setFilters(SearchFilterParser.parseFilters(filters, objectMapper));

            PaginationResponseDto<VaultInstanceDto> response = ilmApiClient.listVaultInstances(request);
            return formatVaultInstanceList(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to list vault instances", e);
            return "Error listing vault instances: " + e.getMessage();
        }
    }

    @Override
    public String listVaultProfiles(String filters, Integer itemsPerPage, Integer pageNumber) {
        try {
            SearchRequestDto request = new SearchRequestDto();
            if (itemsPerPage != null) {
                request.setItemsPerPage(itemsPerPage);
            }
            if (pageNumber != null) {
                request.setPageNumber(pageNumber);
            }
            request.setFilters(SearchFilterParser.parseFilters(filters, objectMapper));

            PaginationResponseDto<VaultProfileDto> response = ilmApiClient.listVaultProfiles(request);
            return formatVaultProfileList(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to list vault profiles", e);
            return "Error listing vault profiles: " + e.getMessage();
        }
    }

    private String formatVaultInstanceList(PaginationResponseDto<VaultInstanceDto> response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(response.getTotalItems()).append(" vault instances");
        sb
                .append(" (page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n\n");

        for (VaultInstanceDto vault : response.getItems()) {
            sb.append("- ").append(vault.getName() != null ? vault.getName() : "N/A");
            sb.append(" [").append(vault.getUuid()).append("]\n");
            if (vault.getDescription() != null) {
                sb.append("  Description: ").append(vault.getDescription()).append("\n");
            }
            if (vault.getConnector() != null) {
                sb.append("  Connector: ").append(vault.getConnector().getName()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatVaultProfileList(PaginationResponseDto<VaultProfileDto> response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(response.getTotalItems()).append(" vault profiles");
        sb
                .append(" (page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n\n");

        for (VaultProfileDto profile : response.getItems()) {
            sb.append("- ").append(profile.getName() != null ? profile.getName() : "N/A");
            sb.append(" [").append(profile.getUuid()).append("]\n");
            if (profile.getDescription() != null) {
                sb.append("  Description: ").append(profile.getDescription()).append("\n");
            }
            if (profile.getVaultInstance() != null) {
                sb.append("  Vault Instance: ").append(profile.getVaultInstance().getName()).append("\n");
            }
            sb.append("  Enabled: ").append(profile.isEnabled()).append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }
}
