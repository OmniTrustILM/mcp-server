package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.dashboard.SigningRecordStatisticsDto;
import com.otilm.api.model.client.dashboard.SigningRecordStatisticsPeriod;
import com.otilm.api.model.client.signing.profile.SigningProfileDto;
import com.otilm.api.model.client.signing.profile.SigningProfileListDto;
import com.otilm.api.model.client.signing.profile.record.SigningRecordPolicyDto;
import com.otilm.api.model.client.signing.profile.scheme.DelegatedSigningDto;
import com.otilm.api.model.client.signing.profile.scheme.ManagedSigningDto;
import com.otilm.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningDto;
import com.otilm.api.model.client.signing.profile.scheme.SigningSchemeDto;
import com.otilm.api.model.client.signing.profile.scheme.StaticKeyManagedSigningDto;
import com.otilm.api.model.client.signing.protocols.tsp.TspProfileListDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.certificate.CertificateSimpleDto;
import com.otilm.api.model.core.signing.SigningProtocol;
import com.otilm.api.model.core.signing.signingrecord.SigningRecordListDto;
import com.otilm.mcp.client.IlmApiClient;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

import static com.otilm.mcp.service.TextFormat.code;
import static com.otilm.mcp.service.TextFormat.counts;
import static com.otilm.mcp.service.TextFormat.customAttributes;
import static com.otilm.mcp.service.TextFormat.field;
import static com.otilm.mcp.service.TextFormat.item;
import static com.otilm.mcp.service.TextFormat.nameOf;
import static com.otilm.mcp.service.TextFormat.orNa;
import static com.otilm.mcp.service.TextFormat.pagedList;
import static com.otilm.mcp.service.TextFormat.title;

@Service
public class SigningServiceImpl implements SigningService {

    private static final String INDENT = "  ";
    private static final String DESCRIPTION = "Description";
    private static final String ENABLED = "Enabled";
    private static final List<String> PERIODS = Arrays
            .stream(SigningRecordStatisticsPeriod.values())
            .map(SigningRecordStatisticsPeriod::getCode)
            .toList();

    private final IlmApiClient ilmApiClient;
    private final PlatformVersionCheck versionCheck;

    public SigningServiceImpl(IlmApiClient ilmApiClient, PlatformVersionCheck versionCheck) {
        this.ilmApiClient = ilmApiClient;
        this.versionCheck = versionCheck;
    }

    @Override
    public String searchSigningProfiles(String filters, Integer itemsPerPage, Integer pageNumber) {
        return versionCheck.run(IlmFeature.SIGNING_PROFILES, "searching signing profiles", () -> {
            PaginationResponseDto<SigningProfileListDto> page = ilmApiClient
                    .searchSigningProfiles(
                            SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(page.getTotalItems(), "signing profiles", page.getPageNumber(), page.getTotalPages(),
                    page.getItems(), SigningServiceImpl::appendProfile);
        });
    }

    @Override
    public String getSigningProfile(String uuid) {
        return versionCheck
                .run(IlmFeature.SIGNING_PROFILES, "retrieving signing profile",
                        () -> formatProfile(ilmApiClient.getSigningProfile(uuid)));
    }

    @Override
    public String searchSigningRecords(String filters, Integer itemsPerPage, Integer pageNumber) {
        return versionCheck.run(IlmFeature.SIGNING_RECORDS, "searching signing records", () -> {
            PaginationResponseDto<SigningRecordListDto> page = ilmApiClient
                    .searchSigningRecords(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(page.getTotalItems(), "signing records", page.getPageNumber(), page.getTotalPages(),
                    page.getItems(), SigningServiceImpl::appendRecord);
        });
    }

    @Override
    public String getSigningStatistics(String period) {
        if (period != null && !PERIODS.contains(period)) {
            return "Unknown period '" + period + "'. Use one of: " + String.join(", ", PERIODS) + ".";
        }
        return versionCheck
                .run(IlmFeature.SIGNING_STATISTICS, "retrieving signing statistics",
                        () -> formatStatistics(period, ilmApiClient.getSigningStatistics(period)));
    }

    @Override
    public String searchTspProfiles(String filters, Integer itemsPerPage, Integer pageNumber) {
        return versionCheck.run(IlmFeature.TSP_PROFILES, "searching TSP profiles", () -> {
            PaginationResponseDto<TspProfileListDto> page = ilmApiClient
                    .searchTspProfiles(SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber));
            return pagedList(page.getTotalItems(), "TSP profiles", page.getPageNumber(), page.getTotalPages(),
                    page.getItems(), SigningServiceImpl::appendTspProfile);
        });
    }

    private static void appendProfile(StringBuilder sb, SigningProfileListDto profile) {
        item(sb, profile.getName(), profile.getUuid());
        field(sb, INDENT, DESCRIPTION, profile.getDescription());
        field(sb, INDENT, "Version", profile.getVersion());
        field(sb, INDENT, "Signing Scheme", code(profile.getSigningScheme()));
        field(sb, INDENT, "Workflow", code(profile.getSigningWorkflowType()));
        field(sb, INDENT, ENABLED, profile.isEnabled());
        field(sb, INDENT, "Protocols", protocols(profile.getEnabledProtocols()));
    }

    private static String formatProfile(SigningProfileDto profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Signing Profile: ").append(profile.getName()).append("\n");
        field(sb, INDENT, "UUID", profile.getUuid());
        field(sb, INDENT, DESCRIPTION, profile.getDescription());
        field(sb, INDENT, "Version", profile.getVersion());
        field(sb, INDENT, ENABLED, profile.isEnabled());
        field(sb, INDENT, "Protocols", protocols(profile.getEnabledProtocols()));
        appendScheme(sb, profile.getSigningScheme());
        field(sb, INDENT, "Workflow", profile.getWorkflow() == null ? null : code(profile.getWorkflow().getType()));
        field(sb, INDENT, "Recording", recording(profile.getRecordPolicy()));
        customAttributes(sb, INDENT, profile.getCustomAttributes());
        return sb.toString();
    }

    private static void appendScheme(StringBuilder sb, SigningSchemeDto scheme) {
        if (scheme == null) {
            return;
        }
        String managedType = scheme instanceof ManagedSigningDto managed
                ? " (" + code(managed.getManagedSigningType()) + ")"
                : "";
        field(sb, INDENT, "Signing Scheme", code(scheme.getSigningScheme()) + managedType);
        switch (scheme) {
            case StaticKeyManagedSigningDto staticKey ->
                field(sb, INDENT, "Certificate", certificate(staticKey.getCertificate()));
            case OneTimeKeyManagedSigningDto oneTimeKey -> {
                field(sb, INDENT, "RA Profile", nameOf(oneTimeKey.getRaProfile()));
                field(sb, INDENT, "CSR Template", nameOf(oneTimeKey.getCsrTemplate()));
                field(sb, INDENT, "Token Profile", nameOf(oneTimeKey.getTokenProfile()));
            }
            case DelegatedSigningDto delegated -> field(sb, INDENT, "Connector", nameOf(delegated.getConnector()));
            default -> {
                // other schemes carry no references to show
            }
        }
    }

    private static String certificate(CertificateSimpleDto certificate) {
        return certificate == null ? null : orNa(certificate.getCommonName()) + " [" + certificate.getUuid() + "]";
    }

    private static String recording(SigningRecordPolicyDto policy) {
        if (policy == null) {
            return null;
        }
        if (!policy.isRecordingEnabled()) {
            return "disabled";
        }
        return policy.getRetentionDays() == null
                ? "enabled"
                : "enabled, retention " + policy.getRetentionDays() + " days";
    }

    private static String protocols(List<SigningProtocol> protocols) {
        if (protocols == null) {
            return null;
        }
        return protocols.stream().map(SigningProtocol::getCode).collect(Collectors.joining(", "));
    }

    private static void appendRecord(StringBuilder sb, SigningRecordListDto signingRecord) {
        item(sb, signingRecord.getName(), signingRecord.getUuid());
        field(sb, INDENT, "Signing Profile", nameOf(signingRecord.getSigningProfile()));
        field(sb, INDENT, "Protocol", code(signingRecord.getProtocol()));
        field(sb, INDENT, "Signing Time", signingRecord.getSigningTime());
        List<String> serials = signingRecord.getTimestampTokenSerialNumbers();
        field(sb, INDENT, "Timestamp Token Serials", serials == null ? null : String.join(", ", serials));
    }

    private static String formatStatistics(String period, SigningRecordStatisticsDto statistics) {
        StringBuilder sb = new StringBuilder();
        title(sb, "Signing Statistics");
        field(sb, "", "Records Retained", statistics.getTotalRetained());
        field(sb, "", "Signed in Last 24 Hours", statistics.getCountLast24h());
        field(sb, "", "Signed in Last 7 Days", statistics.getCountLast7d());
        field(sb, "", "Active Profiles", statistics.getActiveProfileCount());
        field(sb, "", "Distinct Requesters", statistics.getDistinctRequesterCount());
        counts(sb, period == null ? "Volume" : "Volume (" + period + ")", statistics.getVolumeOverTime());
        counts(sb, "Signings by Profile", statistics.getStatByProfile());
        counts(sb, "Signings by Requester", statistics.getStatByRequester());
        counts(sb, "Signings by Workflow Type", statistics.getStatByWorkflowType());
        counts(sb, "Signings by Protocol", statistics.getStatByProtocol());
        counts(sb, "Signings by Scheme", statistics.getStatByScheme());
        return sb.toString();
    }

    private static void appendTspProfile(StringBuilder sb, TspProfileListDto profile) {
        item(sb, profile.getName(), profile.getUuid());
        field(sb, INDENT, DESCRIPTION, profile.getDescription());
        field(sb, INDENT, "Default Signing Profile", nameOf(profile.getDefaultSigningProfile()));
        field(sb, INDENT, "Signing URL", profile.getSigningUrl());
        field(sb, INDENT, ENABLED, profile.isEnabled());
    }
}
