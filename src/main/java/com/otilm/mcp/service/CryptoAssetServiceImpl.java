package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.dashboard.CryptographicAssetStatisticsDto;
import com.otilm.api.model.client.dashboard.CryptographicAssetSyncCompletenessDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDetailDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetNormalizedFieldsDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetOidDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetPqcExplanationDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetSourceDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetVerdictDto;
import com.otilm.api.model.core.cryptoasset.PqcExplanationStepDto;
import com.otilm.api.model.core.cryptoasset.PqcReferencedAssetDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.counts;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.orNa;
import static com.otilm.mcp.service.TextFormat.pagedList;
import static com.otilm.mcp.service.TextFormat.pairs;
import static com.otilm.mcp.service.TextFormat.title;

@Service
public class CryptoAssetServiceImpl implements CryptoAssetService {

    private static final String INDENT = "  ";
    private static final String STEP_INDENT = "       ";
    private static final String REFERENCED_ASSET = "Referenced Asset";

    private final IlmApiClient ilmApiClient;
    private final PlatformVersionCheck versionCheck;

    public CryptoAssetServiceImpl(IlmApiClient ilmApiClient, PlatformVersionCheck versionCheck) {
        this.ilmApiClient = ilmApiClient;
        this.versionCheck = versionCheck;
    }

    @Override
    public String getStatistics() {
        return versionCheck
                .run(IlmFeature.CRYPTO_ASSETS, "retrieving cryptographic asset statistics",
                        () -> formatStatistics(ilmApiClient.getCryptoAssetStatistics()));
    }

    @Override
    public String searchCryptoAssets(String filters, Integer itemsPerPage, Integer pageNumber) {
        return versionCheck.run(IlmFeature.CRYPTO_ASSETS, "searching cryptographic assets", () -> {
            PaginationResponseDto<CryptographicAssetDto> page = ilmApiClient
                    .searchCryptoAssets(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(page.getTotalItems(), "cryptographic assets", page.getPageNumber(), page.getTotalPages(),
                    page.getItems(), CryptoAssetRows::append);
        });
    }

    @Override
    public String getCryptoAsset(String uuid) {
        return versionCheck
                .run(IlmFeature.CRYPTO_ASSETS, "retrieving cryptographic asset",
                        () -> formatDetail(ilmApiClient.getCryptoAsset(uuid)));
    }

    @Override
    public String explainPqcVerdict(String uuid) {
        return versionCheck
                .run(IlmFeature.CRYPTO_ASSETS, "explaining the PQC verdict",
                        () -> formatExplanation(uuid, ilmApiClient.getCryptoAssetPqcExplanation(uuid)));
    }

    private static String formatStatistics(CryptographicAssetStatisticsDto statistics) {
        StringBuilder sb = new StringBuilder();
        title(sb, "Cryptographic Asset Inventory");
        field(sb, "", "Total Assets", statistics.getTotalAssets());
        field(sb, "", "Source CBOMs", statistics.getSourceCbomCount());
        field(sb, "", "Untyped Assets", statistics.getUntypedAssetCount());
        field(sb, "", "Distinct Algorithm Families", statistics.getDistinctAlgorithmFamilyCount());
        field(sb, "", "Assets Without Algorithm Family", statistics.getUnassignedAssetCount());
        counts(sb, "Assets by PQC Verdict", statistics.getStatByPqcVerdict());
        counts(sb, "Assets by Type", statistics.getStatByType());
        counts(sb, "Top Algorithm Families", statistics.getStatByAlgorithmFamily());
        CryptographicAssetSyncCompletenessDto sync = statistics.getSyncCompleteness();
        if (sync != null) {
            counts(sb, "CBOMs by Sync State", sync.getCbomStatBySyncState());
            field(sb, "", "Last Completed Sync", sync.getLastCompletedSyncAt());
        }
        return sb.toString();
    }

    private static String formatDetail(CryptographicAssetDetailDto asset) {
        StringBuilder sb = new StringBuilder();
        sb.append("Cryptographic Asset: ").append(orNa(asset.getName())).append("\n");
        field(sb, INDENT, "UUID", asset.getUuid());
        field(sb, INDENT, "Type", code(asset.getType()));
        field(sb, INDENT, "PQC Verdict", code(asset.getPqcVerdict()));
        field(sb, INDENT, "Source CBOMs", asset.getSourceCbomCount());
        field(sb, INDENT, "Occurrences", asset.getOccurrenceCount());
        if (asset.isQuarantined()) {
            field(sb, INDENT, "Quarantined", true);
        }
        appendVerdict(sb, asset.getVerdict());
        appendNormalizedFields(sb, asset.getNormalizedFields());
        field(sb, INDENT, "OIDs", oids(asset.getOids()));
        appendSources(sb, asset.getSources());
        return sb.toString();
    }

    private static void appendVerdict(StringBuilder sb, CryptographicAssetVerdictDto verdict) {
        if (verdict == null) {
            return;
        }
        field(sb, INDENT, "Verdict Rule", verdict.getRuleId());
        field(sb, INDENT, "Verdict Reason", verdict.getReason());
        field(sb, INDENT, "Verdict Decided", verdict.getDecidedAt());
        field(sb, INDENT, "Verdict Evaluated", verdict.getEvaluatedAt());
        field(sb, INDENT, REFERENCED_ASSET, reference(verdict.getReferencedAsset()));
    }

    private static void appendNormalizedFields(StringBuilder sb, CryptographicAssetNormalizedFieldsDto fields) {
        if (fields == null) {
            return;
        }
        field(sb, INDENT, "Algorithm Family", fields.getAlgorithmFamily());
        field(sb, INDENT, "Primitive", fields.getPrimitive());
        field(sb, INDENT, "Parameter Set", fields.getParameterSet());
        field(sb, INDENT, "Curve", fields.getCurve());
        field(sb, INDENT, "Mode", fields.getMode());
        field(sb, INDENT, "Padding", fields.getPadding());
        field(sb, INDENT, "Variant", fields.getVariant());
    }

    private static String oids(List<CryptographicAssetOidDto> oids) {
        if (oids == null) {
            return null;
        }
        return oids
                .stream()
                .map(oid -> oid.isRefuted() ? oid.getOid() + " (refuted)" : oid.getOid())
                .collect(Collectors.joining(", "));
    }

    private static void appendSources(StringBuilder sb, List<CryptographicAssetSourceDto> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        sb.append("\n  Sources:\n");
        for (CryptographicAssetSourceDto source : sources) {
            sb.append("    - ").append(source.getSerialNumber()).append(" v").append(source.getVersion());
            if (source.getSource() != null) {
                sb.append(" (").append(source.getSource()).append(")");
            }
            long locations = source.getOccurrenceCount();
            sb
                    .append(", recorded locations: ")
                    .append(locations == 0 ? "none" : String.valueOf(locations))
                    .append("\n");
        }
    }

    private static String formatExplanation(String uuid, CryptographicAssetPqcExplanationDto explanation) {
        StringBuilder sb = new StringBuilder();
        title(sb, "PQC Verdict Explanation for " + uuid);
        field(sb, INDENT, "Verdict", code(explanation.getVerdict()));
        field(sb, INDENT, "Rule", explanation.getRuleId());
        field(sb, INDENT, "Reason", explanation.getReason());
        field(sb, INDENT, "Matches Stored Verdict", explanation.isMatchesStored());
        if (!explanation.isMatchesStored() && explanation.getStoredVerdict() != null) {
            String rule = explanation.getStoredRuleId() == null ? "" : " (rule " + explanation.getStoredRuleId() + ")";
            field(sb, INDENT, "Stored Verdict", code(explanation.getStoredVerdict()) + rule);
        }
        field(sb, INDENT, "Inputs", pairs(explanation.getInputs()));
        List<PqcExplanationStepDto> steps = explanation.getSteps();
        if (steps != null && !steps.isEmpty()) {
            sb.append("\n  Steps:\n");
            for (int i = 0; i < steps.size(); i++) {
                appendStep(sb, i + 1, steps.get(i));
            }
        }
        return sb.toString();
    }

    private static void appendStep(StringBuilder sb, int number, PqcExplanationStepDto step) {
        sb
                .append("    ")
                .append(number)
                .append(". ")
                .append(orNa(step.getTitle()))
                .append(" [")
                .append(step.getRuleId())
                .append("]: ")
                .append(code(step.getOutcome()));
        if (step.getVerdict() != null) {
            sb.append(" (verdict ").append(code(step.getVerdict())).append(")");
        }
        sb.append("\n");
        if (step.getMessage() != null && !step.getMessage().isBlank()) {
            sb.append(STEP_INDENT).append(step.getMessage()).append("\n");
        }
        field(sb, STEP_INDENT, "Fields", pairs(step.getEvaluatedFields()));
        field(sb, STEP_INDENT, REFERENCED_ASSET, reference(step.getReferencedAsset()));
    }

    private static String reference(PqcReferencedAssetDto asset) {
        return asset == null ? null : orNa(asset.getName()) + " [" + asset.getUuid() + "]";
    }
}
