package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.otilm.api.model.core.cryptography.key.KeyDetailDto;
import com.otilm.api.model.core.cryptography.key.KeyItemDetailDto;
import com.otilm.api.model.core.cryptography.key.KeyItemDto;
import com.otilm.mcp.client.IlmApiClient;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.names;
import static com.otilm.mcp.service.TextFormat.orNa;
import static com.otilm.mcp.service.TextFormat.pagedList;

@Service
public class KeyServiceImpl implements KeyService {

    private static final String INDENT = "  ";
    private static final String ITEM_INDENT = "      ";

    private final IlmApiClient ilmApiClient;

    public KeyServiceImpl(IlmApiClient ilmApiClient) {
        this.ilmApiClient = ilmApiClient;
    }

    @Override
    public String searchKeys(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ToolResponses.run("searching keys", () -> {
            CryptographicKeyResponseDto response = ilmApiClient
                    .searchKeys(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(response.getTotalItems(), "cryptographic keys", response.getPageNumber(),
                    response.getTotalPages(), response.getCryptographicKeys(), KeyServiceImpl::formatKeyItem);
        });
    }

    @Override
    public String getKey(String uuid) {
        return ToolResponses.run("retrieving key", () -> formatKeyDetail(ilmApiClient.getKey(uuid)));
    }

    private static void formatKeyItem(StringBuilder sb, KeyItemDto key) {
        item(sb, key.getName(), key.getUuid());
        field(sb, INDENT, "Key UUID", key.getKeyWrapperUuid());
        field(sb, INDENT, "Algorithm", code(key.getKeyAlgorithm()));
        field(sb, INDENT, "Size", key.getLength() + " bits");
        field(sb, INDENT, "Type", code(key.getType()));
        field(sb, INDENT, "State", code(key.getState()));
        field(sb, INDENT, "Enabled", key.isEnabled());
        field(sb, INDENT, "Token Instance", key.getTokenInstanceName());
        field(sb, INDENT, "Token Profile", key.getTokenProfileName());
        field(sb, INDENT, "Owner", key.getOwner());
    }

    private static String formatKeyDetail(KeyDetailDto key) {
        StringBuilder sb = new StringBuilder();
        sb.append("Key: ").append(orNa(key.getName())).append("\n");
        field(sb, INDENT, "UUID", key.getUuid());
        field(sb, INDENT, "Description", key.getDescription());
        field(sb, INDENT, "Created", key.getCreationTime());
        field(sb, INDENT, "Token Instance", key.getTokenInstanceName());
        field(sb, INDENT, "Token Profile", key.getTokenProfileName());
        field(sb, INDENT, "Owner", key.getOwner());
        field(sb, INDENT, "Compliance", code(key.getComplianceStatus()));
        field(sb, INDENT, "Groups", names(key.getGroups()));
        if (key.getItems() != null && !key.getItems().isEmpty()) {
            sb.append("\n  Key Items:\n");
            key.getItems().forEach(keyItem -> formatKeyItemDetail(sb, keyItem));
        }
        return sb.toString();
    }

    private static void formatKeyItemDetail(StringBuilder sb, KeyItemDetailDto keyItem) {
        sb.append("    - ").append(orNa(keyItem.getName())).append("\n");
        field(sb, ITEM_INDENT, "UUID", keyItem.getUuid());
        field(sb, ITEM_INDENT, "Type", code(keyItem.getType()));
        field(sb, ITEM_INDENT, "Algorithm", code(keyItem.getKeyAlgorithm()));
        field(sb, ITEM_INDENT, "Size", keyItem.getLength() + " bits");
        field(sb, ITEM_INDENT, "Format", code(keyItem.getFormat()));
        field(sb, ITEM_INDENT, "State", code(keyItem.getState()));
        field(sb, ITEM_INDENT, "Enabled", keyItem.isEnabled());
        if (keyItem.getUsage() != null && !keyItem.getUsage().isEmpty()) {
            field(sb, ITEM_INDENT, "Usage", keyItem.getUsage());
        }
        sb.append("\n");
    }
}
