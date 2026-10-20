package com.otilm.mcp.tool;

import com.otilm.mcp.service.CbomService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CbomTool {

    private static final String FILTERS = "JSON array of search filters. Use get_searchable_fields('cboms') to see "
            + "available fields and operators.";
    private static final String ITEMS_PER_PAGE = "Number of items per page (default: 10)";
    private static final String PAGE_NUMBER = "Page number for pagination (default: 1)";
    private static final String CBOM_UUID = "The UUID of the CBOM";

    private final CbomService cbomService;

    public CbomTool(CbomService cbomService) {
        this.cbomService = cbomService;
    }

    @Tool(name = "search_cboms", description = "Search the CBOMs (cryptography bills of materials) stored in ILM with "
            + "optional filters and pagination. Returns each CBOM's serial number, version, UUID, spec version, timestamp, "
            + "source and asset counts, and whether its assets are synced into the cryptographic asset inventory.",
            resultConverter = PlainTextResult.class)
    public String searchCboms(@ToolParam(description = FILTERS, required = false) String filters,
            @ToolParam(description = ITEMS_PER_PAGE, required = false) Integer itemsPerPage,
            @ToolParam(description = PAGE_NUMBER, required = false) Integer pageNumber) {
        return cbomService.searchCboms(filters, itemsPerPage, pageNumber);
    }

    @Tool(name = "get_cbom", description = "Get one CBOM by its UUID: serial number, version, creation time, spec "
            + "version, source, asset counts, asset sync state and custom attributes. The raw CBOM document is not "
            + "returned; use search_cbom_assets to see the assets it contributed.",
            resultConverter = PlainTextResult.class)
    public String getCbom(@ToolParam(description = CBOM_UUID) String uuid) {
        return cbomService.getCbom(uuid);
    }

    @Tool(name = "get_cbom_versions", description = "List the stored versions of a CBOM by its UUID, with each "
            + "version's UUID, timestamp and asset counts.", resultConverter = PlainTextResult.class)
    public String getCbomVersions(@ToolParam(description = CBOM_UUID) String uuid) {
        return cbomService.getCbomVersions(uuid);
    }

    @Tool(name = "search_cbom_assets", description = "List the cryptographic assets one CBOM contributed to the "
            + "inventory, with optional filters (the fields of get_searchable_fields('cryptoAssets')) and pagination. "
            + "Returns each asset's name, UUID, type and PQC verdict, with source CBOM and occurrence counts totalled "
            + "across all CBOMs. The list follows the CBOM's asset sync state and is empty for a version superseded by "
            + "a later synced one. Requires ILM 2.20 or later.", resultConverter = PlainTextResult.class)
    public String searchCbomAssets(@ToolParam(description = CBOM_UUID) String uuid,
            @ToolParam(description = "JSON array of search filters. Use get_searchable_fields('cryptoAssets') to see "
                    + "available fields and operators.", required = false) String filters,
            @ToolParam(description = ITEMS_PER_PAGE, required = false) Integer itemsPerPage,
            @ToolParam(description = PAGE_NUMBER, required = false) Integer pageNumber) {
        return cbomService.searchCbomAssets(uuid, filters, itemsPerPage, pageNumber);
    }
}
