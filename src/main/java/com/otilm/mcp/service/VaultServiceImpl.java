package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.vault.VaultInstanceDto;
import com.otilm.api.model.core.vaultprofile.VaultProfileDto;
import com.otilm.mcp.client.IlmApiClient;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.nameOf;
import static com.otilm.mcp.service.TextFormat.pagedList;

@Service
public class VaultServiceImpl implements VaultService {

    private static final String INDENT = "  ";

    private final IlmApiClient ilmApiClient;

    public VaultServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String listVaultInstances(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ToolResponses.run("listing vault instances", () -> {
            PaginationResponseDto<VaultInstanceDto> response = ilmApiClient
                    .listVaultInstances(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(response.getTotalItems(), "vault instances", response.getPageNumber(),
                    response.getTotalPages(), response.getItems(), (sb, vault) -> {
                        item(sb, vault.getName(), vault.getUuid());
                        field(sb, INDENT, "Description", vault.getDescription());
                        field(sb, INDENT, "Connector", nameOf(vault.getConnector()));
                    });
        });
    }

    @Override
    public String listVaultProfiles(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ToolResponses.run("listing vault profiles", () -> {
            PaginationResponseDto<VaultProfileDto> response = ilmApiClient
                    .listVaultProfiles(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(response.getTotalItems(), "vault profiles", response.getPageNumber(),
                    response.getTotalPages(), response.getItems(), (sb, profile) -> {
                        item(sb, profile.getName(), profile.getUuid());
                        field(sb, INDENT, "Description", profile.getDescription());
                        field(sb, INDENT, "Vault Instance", nameOf(profile.getVaultInstance()));
                        field(sb, INDENT, "Enabled", profile.isEnabled());
                    });
        });
    }
}
