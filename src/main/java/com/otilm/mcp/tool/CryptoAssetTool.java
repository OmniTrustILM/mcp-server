package com.otilm.mcp.tool;

import com.otilm.mcp.service.CryptoAssetService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CryptoAssetTool {

    private static final String REQUIRES_ILM = " Requires ILM 2.20 or later.";

    private final CryptoAssetService cryptoAssetService;

    public CryptoAssetTool(CryptoAssetService cryptoAssetService) {
        this.cryptoAssetService = cryptoAssetService;
    }

    @Tool(name = "get_crypto_asset_statistics", description = "Get the post-quantum (PQC) readiness overview of the "
            + "cryptographic asset inventory that ILM builds from CBOMs: total assets and source CBOMs, counts by PQC "
            + "verdict (ready, notReady, notApplicable, unknown) and by asset type, the top algorithm families, and how "
            + "completely the inventory reflects the stored CBOMs. Start here for PQC readiness questions."
            + REQUIRES_ILM, resultConverter = PlainTextResult.class)
    public String getCryptoAssetStatistics() {
        return cryptoAssetService.getStatistics();
    }

    @Tool(name = "search_crypto_assets", description = "Search the cryptographic asset inventory with optional filters "
            + "and pagination, for example by PQC verdict or asset type. Use get_searchable_fields('cryptoAssets') to "
            + "discover filter fields. Returns each asset's name, UUID, type, PQC verdict, and the number of source CBOMs "
            + "and occurrences." + REQUIRES_ILM, resultConverter = PlainTextResult.class)
    public String searchCryptoAssets(
            @ToolParam(
                    description = "JSON array of search filters. Use "
                            + "get_searchable_fields('cryptoAssets') to see available fields and operators.",
                    required = false) String filters,
            @ToolParam(description = "Number of items per page (default: 10)", required = false) Integer itemsPerPage,
            @ToolParam(description = "Page number for pagination (default: 1)", required = false) Integer pageNumber) {
        return cryptoAssetService.searchCryptoAssets(filters, itemsPerPage, pageNumber);
    }

    @Tool(name = "get_crypto_asset", description = "Get one cryptographic asset by its UUID: its PQC verdict with the "
            + "deciding rule and reason, its normalized properties (algorithm family, primitive, parameter set, curve, "
            + "mode, padding, variant), its OIDs, and the CBOMs it was found in." + REQUIRES_ILM,
            resultConverter = PlainTextResult.class)
    public String getCryptoAsset(@ToolParam(description = "The UUID of the cryptographic asset") String uuid) {
        return cryptoAssetService.getCryptoAsset(uuid);
    }

    @Tool(name = "explain_crypto_asset_pqc_verdict",
            description = "Explain how ILM reached a cryptographic asset's PQC "
                    + "verdict: every rule it evaluated in order, with the outcome, message and the properties each rule read, and "
                    + "whether the result matches the stored verdict. Use it to answer why an asset is or is not PQC ready."
                    + REQUIRES_ILM,
            resultConverter = PlainTextResult.class)
    public String explainCryptoAssetPqcVerdict(
            @ToolParam(description = "The UUID of the cryptographic asset") String uuid) {
        return cryptoAssetService.explainPqcVerdict(uuid);
    }
}
