package com.otilm.mcp.tool;

import com.otilm.mcp.service.SigningService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SigningTool {

    private static final String ITEMS_PER_PAGE = "Number of items per page (default: 10)";
    private static final String PAGE_NUMBER = "Page number for pagination (default: 1)";

    private final SigningService signingService;

    public SigningTool(SigningService signingService) {
        this.signingService = signingService;
    }

    @Tool(name = "search_signing_profiles", description = "Search the signing profiles that define how ILM signs "
            + "and timestamps, with optional filters and pagination. Returns each profile's name, UUID, version, "
            + "signing scheme (managed or delegated), workflow type (content_signing, raw_signing or timestamping), "
            + "enabled state and enabled protocols. Requires ILM 2.18 or later.",
            resultConverter = PlainTextResult.class)
    public String searchSigningProfiles(
            @ToolParam(description = "JSON array of search filters. Use get_searchable_fields('signingProfiles') to "
                    + "see available fields and operators.", required = false) String filters,
            @ToolParam(description = ITEMS_PER_PAGE, required = false) Integer itemsPerPage,
            @ToolParam(description = PAGE_NUMBER, required = false) Integer pageNumber) {
        return signingService.searchSigningProfiles(filters, itemsPerPage, pageNumber);
    }

    @Tool(name = "get_signing_profile", description = "Get one signing profile by its UUID: its signing scheme with "
            + "the signing certificate (managed static key), the RA profile, CSR template and token profile (managed "
            + "one-time key) or the signing connector (delegated), its workflow type, whether signing operations are "
            + "recorded, its enabled protocols and custom attributes. Requires ILM 2.18 or later.",
            resultConverter = PlainTextResult.class)
    public String getSigningProfile(@ToolParam(description = "The UUID of the signing profile") String uuid) {
        return signingService.getSigningProfile(uuid);
    }

    @Tool(name = "search_signing_records", description = "Search the records of signing operations performed by ILM, "
            + "with optional filters and pagination. Returns each record's name, UUID, signing profile, protocol, "
            + "signing time and timestamp token serial numbers; signature values and signed content are never "
            + "returned. Requires ILM 2.19 or later.", resultConverter = PlainTextResult.class)
    public String searchSigningRecords(
            @ToolParam(description = "JSON array of search filters. Use get_searchable_fields('signingRecords') to "
                    + "see available fields and operators.", required = false) String filters,
            @ToolParam(description = ITEMS_PER_PAGE, required = false) Integer itemsPerPage,
            @ToolParam(description = PAGE_NUMBER, required = false) Integer pageNumber) {
        return signingService.searchSigningRecords(filters, itemsPerPage, pageNumber);
    }

    @Tool(name = "get_signing_statistics", description = "Get signing activity statistics: records retained, "
            + "signings in the last 24 hours and 7 days, active profiles, distinct requesters, signing volume over "
            + "the chosen period, and signings by profile, requester, workflow type, protocol and scheme. Requires ILM "
            + "2.19 or later.", resultConverter = PlainTextResult.class)
    public String getSigningStatistics(@ToolParam(description = "Period of the signing volume: 24h, 7d, 30d or 90d. "
            + "ILM's default period is used when omitted.", required = false) String period) {
        return signingService.getSigningStatistics(period);
    }

    @Tool(name = "search_tsp_profiles", description = "Search the TSP profiles that publish signing profiles as RFC "
            + "3161 timestamping endpoints, with optional filters and pagination. Returns each profile's name, UUID, "
            + "description, default signing profile, signing URL and enabled state. Requires ILM 2.18 or later.",
            resultConverter = PlainTextResult.class)
    public String searchTspProfiles(
            @ToolParam(description = "JSON array of search filters. Use get_searchable_fields('tspProfiles') to see "
                    + "available fields and operators.", required = false) String filters,
            @ToolParam(description = ITEMS_PER_PAGE, required = false) Integer itemsPerPage,
            @ToolParam(description = PAGE_NUMBER, required = false) Integer pageNumber) {
        return signingService.searchTspProfiles(filters, itemsPerPage, pageNumber);
    }
}
