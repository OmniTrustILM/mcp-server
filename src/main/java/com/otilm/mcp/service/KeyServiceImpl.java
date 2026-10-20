package com.otilm.mcp.service;

import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyItemDetailDto;
import com.czertainly.api.model.core.cryptography.key.KeyItemDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otilm.mcp.client.IlmApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KeyServiceImpl implements KeyService {

    private static final Logger logger = LoggerFactory.getLogger(KeyServiceImpl.class);

    private final IlmApiClient ilmApiClient;
    private final ObjectMapper objectMapper;

    public KeyServiceImpl(IlmApiClient ilmApiClient, ObjectMapper objectMapper) {
        this.ilmApiClient = ilmApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String searchKeys(String filters, Integer itemsPerPage, Integer pageNumber) {
        try {
            SearchRequestDto request = new SearchRequestDto();
            if (itemsPerPage != null) {
                request.setItemsPerPage(itemsPerPage);
            }
            if (pageNumber != null) {
                request.setPageNumber(pageNumber);
            }
            request.setFilters(SearchFilterParser.parseFilters(filters, objectMapper));

            CryptographicKeyResponseDto response = ilmApiClient.searchKeys(request);
            return formatKeyList(response);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            logger.error("Failed to search keys", e);
            return "Error searching keys: " + e.getMessage();
        }
    }

    @Override
    public String getKey(String uuid) {
        try {
            KeyDetailDto key = ilmApiClient.getKey(uuid);
            return formatKeyDetail(key);
        } catch (Exception e) {
            logger.error("Failed to get key {}", uuid, e);
            return "Error retrieving key: " + e.getMessage();
        }
    }

    private String formatKeyList(CryptographicKeyResponseDto response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(response.getTotalItems()).append(" cryptographic keys");
        sb
                .append(" (page ")
                .append(response.getPageNumber())
                .append(" of ")
                .append(response.getTotalPages())
                .append(")\n\n");

        for (KeyItemDto key : response.getCryptographicKeys()) {
            sb.append("- ").append(key.getName() != null ? key.getName() : "N/A");
            sb.append(" [").append(key.getUuid()).append("]\n");
            sb
                    .append("  Algorithm: ")
                    .append(key.getKeyAlgorithm() != null ? key.getKeyAlgorithm().getCode() : "N/A")
                    .append("\n");
            sb.append("  Size: ").append(key.getLength()).append(" bits\n");
            sb.append("  Type: ").append(key.getType() != null ? key.getType().getCode() : "N/A").append("\n");
            sb.append("  State: ").append(key.getState() != null ? key.getState().getCode() : "N/A").append("\n");
            sb.append("  Enabled: ").append(key.isEnabled()).append("\n");
            if (key.getTokenInstanceName() != null) {
                sb.append("  Token Instance: ").append(key.getTokenInstanceName()).append("\n");
            }
            if (key.getTokenProfileName() != null) {
                sb.append("  Token Profile: ").append(key.getTokenProfileName()).append("\n");
            }
            if (key.getOwner() != null) {
                sb.append("  Owner: ").append(key.getOwner()).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String formatKeyDetail(KeyDetailDto key) {
        StringBuilder sb = new StringBuilder();
        sb.append("Key: ").append(key.getName() != null ? key.getName() : "N/A").append("\n");
        sb.append("  UUID: ").append(key.getUuid()).append("\n");
        if (key.getDescription() != null) {
            sb.append("  Description: ").append(key.getDescription()).append("\n");
        }
        if (key.getCreationTime() != null) {
            sb.append("  Created: ").append(key.getCreationTime()).append("\n");
        }
        if (key.getTokenInstanceName() != null) {
            sb.append("  Token Instance: ").append(key.getTokenInstanceName()).append("\n");
        }
        if (key.getTokenProfileName() != null) {
            sb.append("  Token Profile: ").append(key.getTokenProfileName()).append("\n");
        }
        if (key.getOwner() != null) {
            sb.append("  Owner: ").append(key.getOwner()).append("\n");
        }
        sb
                .append("  Compliance: ")
                .append(key.getComplianceStatus() != null ? key.getComplianceStatus().getCode() : "N/A")
                .append("\n");

        if (key.getGroups() != null && !key.getGroups().isEmpty()) {
            sb.append("  Groups: ");
            sb.append(String.join(", ", key.getGroups().stream().map(g -> g.getName()).toList()));
            sb.append("\n");
        }

        if (key.getItems() != null && !key.getItems().isEmpty()) {
            sb.append("\n  Key Items:\n");
            for (KeyItemDetailDto item : key.getItems()) {
                sb.append("    - ").append(item.getName() != null ? item.getName() : "N/A").append("\n");
                sb.append("      UUID: ").append(item.getUuid()).append("\n");
                sb
                        .append("      Type: ")
                        .append(item.getType() != null ? item.getType().getCode() : "N/A")
                        .append("\n");
                sb
                        .append("      Algorithm: ")
                        .append(item.getKeyAlgorithm() != null ? item.getKeyAlgorithm().getCode() : "N/A")
                        .append("\n");
                sb.append("      Size: ").append(item.getLength()).append(" bits\n");
                sb
                        .append("      Format: ")
                        .append(item.getFormat() != null ? item.getFormat().getCode() : "N/A")
                        .append("\n");
                sb
                        .append("      State: ")
                        .append(item.getState() != null ? item.getState().getCode() : "N/A")
                        .append("\n");
                sb.append("      Enabled: ").append(item.isEnabled()).append("\n");
                if (item.getUsage() != null && !item.getUsage().isEmpty()) {
                    sb.append("      Usage: ").append(item.getUsage()).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
