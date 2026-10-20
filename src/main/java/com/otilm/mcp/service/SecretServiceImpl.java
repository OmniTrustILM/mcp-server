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
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.nameOf;
import static com.otilm.mcp.service.TextFormat.names;
import static com.otilm.mcp.service.TextFormat.orNa;
import static com.otilm.mcp.service.TextFormat.pagedList;
import static com.otilm.mcp.service.TextFormat.title;

@Service
public class SecretServiceImpl implements SecretService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String INDENT = "  ";

    private final IlmApiClient ilmApiClient;

    public SecretServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String searchSecrets(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ToolResponses.run("searching secrets", () -> {
            PaginationResponseDto<SecretDto> response = ilmApiClient
                    .searchSecrets(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(response.getTotalItems(), "secrets", response.getPageNumber(), response.getTotalPages(),
                    response.getItems(), SecretServiceImpl::formatSecret);
        });
    }

    @Override
    public String getSecret(String uuid) {
        return ToolResponses.run("retrieving secret", () -> formatSecretDetail(ilmApiClient.getSecret(uuid)));
    }

    @Override
    public String getSecretVersions(String uuid) {
        return ToolResponses.run("retrieving secret versions", () -> {
            List<SecretVersionDto> versions = ilmApiClient.getSecretVersions(uuid);
            StringBuilder sb = new StringBuilder();
            title(sb, "Secret Versions for " + uuid);
            sb.append(versions.size()).append(" versions\n\n");
            for (SecretVersionDto version : versions) {
                sb.append("- Version: ").append(version.getVersion()).append("\n");
                field(sb, INDENT, "Created", formatDateTime(version.getCreatedAt()));
                field(sb, INDENT, "Fingerprint", version.getFingerprint());
                sb.append("\n");
            }
            return sb.toString();
        });
    }

    private static void formatSecret(StringBuilder sb, SecretDto secret) {
        item(sb, secret.getName(), secret.getUuid());
        field(sb, INDENT, "Type", code(secret.getType()));
        field(sb, INDENT, "State", code(secret.getState()));
        field(sb, INDENT, "Version", secret.getVersion());
        field(sb, INDENT, "Enabled", secret.isEnabled());
        field(sb, INDENT, "Owner", nameOf(secret.getOwner()));
        field(sb, INDENT, "Vault Profile", nameOf(secret.getSourceVaultProfile()));
        field(sb, INDENT, "Groups", names(secret.getGroups()));
        if (secret.getComplianceStatus() != null) {
            field(sb, INDENT, "Compliance", code(secret.getComplianceStatus()));
        }
    }

    private static String formatSecretDetail(SecretDetailDto secret) {
        StringBuilder sb = new StringBuilder();
        sb.append("Secret: ").append(orNa(secret.getName())).append("\n");
        field(sb, INDENT, "UUID", secret.getUuid());
        field(sb, INDENT, "Description", secret.getDescription());
        field(sb, INDENT, "Type", code(secret.getType()));
        field(sb, INDENT, "State", code(secret.getState()));
        field(sb, INDENT, "Version", secret.getVersion());
        field(sb, INDENT, "Enabled", secret.isEnabled());
        field(sb, INDENT, "Owner", nameOf(secret.getOwner()));
        field(sb, INDENT, "Source Vault Profile", nameOf(secret.getSourceVaultProfile()));
        field(sb, INDENT, "Groups", names(secret.getGroups()));
        field(sb, INDENT, "Compliance", code(secret.getComplianceStatus()));
        field(sb, INDENT, "Created", formatDateTime(secret.getCreatedAt()));
        field(sb, INDENT, "Updated", formatDateTime(secret.getUpdatedAt()));
        if (secret.getSyncVaultProfiles() != null && !secret.getSyncVaultProfiles().isEmpty()) {
            sb.append("\n  Sync Vault Profiles:\n");
            secret
                    .getSyncVaultProfiles()
                    .forEach(profile -> sb
                            .append("    - ")
                            .append(profile.getName())
                            .append(" [")
                            .append(profile.getUuid())
                            .append("]\n"));
        }
        return sb.toString();
    }

    private static String formatDateTime(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_FORMATTER);
    }
}
