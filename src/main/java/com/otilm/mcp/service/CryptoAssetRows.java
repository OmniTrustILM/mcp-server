package com.otilm.mcp.service;

import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;

final class CryptoAssetRows {

    private static final String INDENT = "  ";

    private CryptoAssetRows() {
    }

    static void append(StringBuilder sb, CryptographicAssetDto asset) {
        item(sb, asset.getName(), asset.getUuid());
        field(sb, INDENT, "Type", code(asset.getType()));
        field(sb, INDENT, "PQC Verdict", code(asset.getPqcVerdict()));
        field(sb, INDENT, "Source CBOMs", asset.getSourceCbomCount());
        field(sb, INDENT, "Occurrences", asset.getOccurrenceCount());
        if (asset.isQuarantined()) {
            field(sb, INDENT, "Quarantined", true);
        }
    }
}
