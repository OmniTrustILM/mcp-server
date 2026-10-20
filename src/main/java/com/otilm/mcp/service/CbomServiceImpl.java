package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.cbom.CbomContributedAssetDto;
import com.otilm.api.model.core.cbom.CbomDetailDto;
import com.otilm.api.model.core.cbom.CbomDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.customAttributes;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.pagedList;

@Service
public class CbomServiceImpl implements CbomService {

    private static final String INDENT = "  ";

    private final IlmApiClient ilmApiClient;
    private final PlatformVersionCheck versionCheck;

    public CbomServiceImpl(IlmApiClient ilmApiClient, PlatformVersionCheck versionCheck) {
        this.ilmApiClient = ilmApiClient;
        this.versionCheck = versionCheck;
    }

    @Override
    public String searchCboms(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ToolResponses.run("searching CBOMs", () -> {
            PaginationResponseDto<CbomDto> page = ilmApiClient
                    .searchCboms(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(page.getTotalItems(), "CBOMs", page.getPageNumber(), page.getTotalPages(), page.getItems(),
                    CbomServiceImpl::formatCbom);
        });
    }

    @Override
    public String getCbom(String uuid) {
        return ToolResponses.run("retrieving CBOM", () -> formatCbomDetail(ilmApiClient.getCbom(uuid)));
    }

    @Override
    public String getCbomVersions(String uuid) {
        return ToolResponses.run("retrieving CBOM versions", () -> {
            List<CbomDto> versions = ilmApiClient.getCbomVersions(uuid);
            return TextFormat
                    .titledList("CBOM Versions for " + uuid, versions, "No CBOM versions found.", (sb, version) -> {
                        sb
                                .append("- v")
                                .append(version.getVersion())
                                .append(" [")
                                .append(version.getUuid())
                                .append("]\n");
                        appendCbomFields(sb, version);
                    });
        });
    }

    @Override
    public String searchCbomAssets(String uuid, String filters, Integer itemsPerPage, Integer pageNumber) {
        return versionCheck.run(IlmFeature.CBOM_ASSETS, "searching CBOM assets", () -> {
            PaginationResponseDto<CbomContributedAssetDto> page = ilmApiClient
                    .searchCbomAssets(uuid,
                            SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(page.getTotalItems(), "cryptographic assets", page.getPageNumber(), page.getTotalPages(),
                    page.getItems(), CryptoAssetRows::append);
        });
    }

    private static void formatCbom(StringBuilder sb, CbomDto cbom) {
        sb
                .append("- ")
                .append(cbom.getSerialNumber())
                .append(" v")
                .append(cbom.getVersion())
                .append(" [")
                .append(cbom.getUuid())
                .append("]\n");
        appendCbomFields(sb, cbom);
    }

    private static String formatCbomDetail(CbomDetailDto cbom) {
        StringBuilder sb = new StringBuilder();
        sb.append("CBOM: ").append(cbom.getSerialNumber()).append(" v").append(cbom.getVersion()).append("\n");
        field(sb, INDENT, "UUID", cbom.getUuid());
        field(sb, INDENT, "Created", cbom.getCreatedAt());
        appendCbomFields(sb, cbom);
        customAttributes(sb, INDENT, cbom.getCustomAttributes());
        return sb.toString();
    }

    private static void appendCbomFields(StringBuilder sb, CbomDto cbom) {
        field(sb, INDENT, "Spec Version", cbom.getSpecVersion());
        field(sb, INDENT, "Timestamp", cbom.getTimestamp());
        field(sb, INDENT, "Source", cbom.getSource());
        field(sb, INDENT, "Assets",
                cbom.getTotalAssets() + " (algorithms " + cbom.getAlgorithms() + ", certificates "
                        + cbom.getCertificates() + ", protocols " + cbom.getProtocols() + ", crypto material "
                        + cbom.getCryptoMaterial() + ")");
        if (cbom.getAssetSyncState() != null) {
            field(sb, INDENT, "Asset Sync", code(cbom.getAssetSyncState()));
        }
        field(sb, INDENT, "Last Asset Sync", cbom.getAssetSyncedAt());
        field(sb, INDENT, "Asset Sync Error", cbom.getAssetSyncError());
    }
}
