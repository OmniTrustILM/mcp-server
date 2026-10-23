package com.otilm.mcp.client;

import com.otilm.api.model.client.attribute.custom.CustomAttributeCreateRequestDto;
import com.otilm.api.model.client.attribute.custom.CustomAttributeDefinitionDetailDto;
import com.otilm.api.model.client.attribute.custom.CustomAttributeDefinitionDto;
import com.otilm.api.model.client.certificate.CertificateResponseDto;
import com.otilm.api.model.client.certificate.DiscoveryResponseDto;
import com.otilm.api.model.client.certificate.EntityInstanceResponseDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.otilm.api.model.client.dashboard.CryptographicAssetStatisticsDto;
import com.otilm.api.model.client.dashboard.SigningRecordStatisticsDto;
import com.otilm.api.model.client.dashboard.StatisticsDto;
import com.otilm.api.model.client.notification.NotificationProfileDetailDto;
import com.otilm.api.model.client.notification.NotificationProfileRequestDto;
import com.otilm.api.model.client.notification.NotificationProfileResponseDto;
import com.otilm.api.model.client.signing.profile.SigningProfileDto;
import com.otilm.api.model.client.signing.profile.SigningProfileListDto;
import com.otilm.api.model.client.signing.protocols.tsp.TspProfileListDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.common.UuidDto;
import com.otilm.api.model.core.auth.RoleDto;
import com.otilm.api.model.core.auth.UserDto;
import com.otilm.api.model.core.authority.AuthorityInstanceDto;
import com.otilm.api.model.core.cbom.CbomContributedAssetDto;
import com.otilm.api.model.core.cbom.CbomDetailDto;
import com.otilm.api.model.core.cbom.CbomDto;
import com.otilm.api.model.core.certificate.CertificateChainResponseDto;
import com.otilm.api.model.core.certificate.CertificateDetailDto;
import com.otilm.api.model.core.certificate.CertificateEventHistoryDto;
import com.otilm.api.model.core.certificate.CertificateValidationResultDto;
import com.otilm.api.model.core.certificate.group.GroupDto;
import com.otilm.api.model.core.certificate.group.GroupRequestDto;
import com.otilm.api.model.core.connector.ConnectorDto;
import com.otilm.api.model.core.credential.CredentialDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDetailDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetDto;
import com.otilm.api.model.core.cryptoasset.CryptographicAssetPqcExplanationDto;
import com.otilm.api.model.core.cryptography.key.KeyDetailDto;
import com.otilm.api.model.core.cryptography.token.TokenInstanceDto;
import com.otilm.api.model.core.info.CoreInfoResponseDto;
import com.otilm.api.model.core.notification.NotificationInstanceDto;
import com.otilm.api.model.core.raprofile.RaProfileDto;
import com.otilm.api.model.core.search.SearchFieldDataByGroupDto;
import com.otilm.api.model.core.secret.SecretDetailDto;
import com.otilm.api.model.core.secret.SecretDto;
import com.otilm.api.model.core.secret.SecretVersionDto;
import com.otilm.api.model.core.settings.EventSettingsDto;
import com.otilm.api.model.core.settings.EventsSettingsDto;
import com.otilm.api.model.core.signing.signingrecord.SigningRecordListDto;
import com.otilm.api.model.core.vault.VaultInstanceDto;
import com.otilm.api.model.core.vaultprofile.VaultProfileDto;
import com.otilm.api.model.core.workflows.ActionDetailDto;
import com.otilm.api.model.core.workflows.ActionRequestDto;
import com.otilm.api.model.core.workflows.ConditionDto;
import com.otilm.api.model.core.workflows.ConditionRequestDto;
import com.otilm.api.model.core.workflows.ExecutionDto;
import com.otilm.api.model.core.workflows.ExecutionRequestDto;
import com.otilm.api.model.core.workflows.RuleDetailDto;
import com.otilm.api.model.core.workflows.RuleRequestDto;
import com.otilm.api.model.core.workflows.TriggerDetailDto;
import com.otilm.api.model.core.workflows.TriggerDto;
import com.otilm.api.model.core.workflows.TriggerRequestDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IlmApiClient {

    private static final Logger logger = LoggerFactory.getLogger(IlmApiClient.class);

    private final RestClient restClient;

    public IlmApiClient(RestClient ilmRestClient) {
        this.restClient = ilmRestClient;
    }

    public static RestClient.Builder restClientBuilder(String baseUrl) {
        JsonMapper jsonMapper = JsonMapper
                .builder()
                .findAndAddModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        return RestClient
                .builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .configureMessageConverters(converters -> converters
                        .registerDefaults()
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)));
    }

    // ---- Certificate endpoints ----

    public StatisticsDto getStatistics() {
        logger.debug("Fetching statistics");
        return restClient.get().uri("/v1/statistics").retrieve().body(StatisticsDto.class);
    }

    public CertificateResponseDto searchCertificates(SearchRequestDto request) {
        logger.debug("Searching certificates");
        return restClient.post().uri("/v1/certificates").body(request).retrieve().body(CertificateResponseDto.class);
    }

    public CertificateDetailDto getCertificate(String uuid) {
        logger.debug("Fetching certificate {}", uuid);
        return restClient.get().uri("/v1/certificates/{uuid}", uuid).retrieve().body(CertificateDetailDto.class);
    }

    public CertificateValidationResultDto validateCertificate(String uuid) {
        logger.debug("Validating certificate {}", uuid);
        return restClient
                .get()
                .uri("/v1/certificates/{uuid}/validate", uuid)
                .retrieve()
                .body(CertificateValidationResultDto.class);
    }

    public CertificateChainResponseDto getCertificateChain(String uuid) {
        logger.debug("Fetching certificate chain for {}", uuid);
        return restClient
                .get()
                .uri("/v1/certificates/{uuid}/chain?withEndCertificate=true", uuid)
                .retrieve()
                .body(CertificateChainResponseDto.class);
    }

    public List<CertificateEventHistoryDto> getCertificateHistory(String uuid) {
        logger.debug("Fetching certificate history for {}", uuid);
        return restClient
                .get()
                .uri("/v1/certificates/{uuid}/history", uuid)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ---- Key endpoints ----

    public CryptographicKeyResponseDto searchKeys(SearchRequestDto request) {
        logger.debug("Searching keys");
        return restClient.post().uri("/v1/keys").body(request).retrieve().body(CryptographicKeyResponseDto.class);
    }

    public KeyDetailDto getKey(String uuid) {
        logger.debug("Fetching key {}", uuid);
        return restClient.get().uri("/v1/keys/{uuid}", uuid).retrieve().body(KeyDetailDto.class);
    }

    // ---- Infrastructure endpoints ----

    public List<AuthorityInstanceDto> listAuthorities() {
        logger.debug("Listing authorities");
        return restClient.get().uri("/v1/authorities").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<RaProfileDto> listRaProfiles() {
        logger.debug("Listing RA profiles");
        return restClient.get().uri("/v1/raProfiles").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<ConnectorDto> listConnectors() {
        logger.debug("Listing connectors");
        return restClient.get().uri("/v1/connectors").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<GroupDto> listGroups() {
        logger.debug("Listing groups");
        return restClient.get().uri("/v1/groups").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public EntityInstanceResponseDto listEntities(SearchRequestDto request) {
        logger.debug("Listing entities");
        return restClient
                .post()
                .uri("/v1/entities/list")
                .body(request)
                .retrieve()
                .body(EntityInstanceResponseDto.class);
    }

    public List<CredentialDto> listCredentials() {
        logger.debug("Listing credentials");
        return restClient.get().uri("/v1/credentials").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<TokenInstanceDto> listTokenInstances() {
        logger.debug("Listing token instances");
        return restClient.get().uri("/v1/tokens").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public DiscoveryResponseDto listDiscoveries(SearchRequestDto request) {
        logger.debug("Listing discoveries");
        return restClient.post().uri("/v1/discoveries/list").body(request).retrieve().body(DiscoveryResponseDto.class);
    }

    // ---- Info endpoint ----

    public CoreInfoResponseDto getInfo() {
        logger.debug("Fetching platform info");
        return restClient.get().uri("/v1/info").retrieve().body(CoreInfoResponseDto.class);
    }

    // ---- Secret endpoints ----

    public PaginationResponseDto<SecretDto> searchSecrets(SearchRequestDto request) {
        logger.debug("Searching secrets");
        return restClient.post().uri("/v1/secrets").body(request).retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public SecretDetailDto getSecret(String uuid) {
        logger.debug("Fetching secret {}", uuid);
        return restClient.get().uri("/v1/secrets/{uuid}", uuid).retrieve().body(SecretDetailDto.class);
    }

    public List<SecretVersionDto> getSecretVersions(String uuid) {
        logger.debug("Fetching secret versions for {}", uuid);
        return restClient
                .get()
                .uri("/v1/secrets/{uuid}/versions", uuid)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ---- Vault endpoints ----

    public PaginationResponseDto<VaultInstanceDto> listVaultInstances(SearchRequestDto request) {
        logger.debug("Listing vault instances");
        return restClient
                .post()
                .uri("/v1/vaults/list")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public PaginationResponseDto<VaultProfileDto> listVaultProfiles(SearchRequestDto request) {
        logger.debug("Listing vault profiles");
        return restClient
                .post()
                .uri("/v1/vaultProfiles/list")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ---- Search field discovery ----

    public List<SearchFieldDataByGroupDto> getSearchableFields(String resource) {
        logger.debug("Fetching searchable fields for {}", resource);
        return restClient
                .get()
                .uri("/v1/{resource}/search", resource)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<SearchFieldDataByGroupDto> listRuleFilterFields(String resource, boolean settable) {
        logger.debug("Fetching rule filter fields for {}", resource);
        return restClient
                .get()
                .uri("/v1/resources/{resource}/filters/rules?settable={settable}", resource, settable)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ---- Cryptographic asset endpoints ----

    public CryptographicAssetStatisticsDto getCryptoAssetStatistics() {
        logger.debug("Fetching cryptographic asset statistics");
        return restClient
                .get()
                .uri("/v1/statistics/cryptoAssets")
                .retrieve()
                .body(CryptographicAssetStatisticsDto.class);
    }

    public PaginationResponseDto<CryptographicAssetDto> searchCryptoAssets(SearchRequestDto request) {
        logger.debug("Searching cryptographic assets");
        return restClient
                .post()
                .uri("/v1/cryptoAssets")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public CryptographicAssetDetailDto getCryptoAsset(String uuid) {
        logger.debug("Fetching cryptographic asset {}", uuid);
        return restClient.get().uri("/v1/cryptoAssets/{uuid}", uuid).retrieve().body(CryptographicAssetDetailDto.class);
    }

    public CryptographicAssetPqcExplanationDto getCryptoAssetPqcExplanation(String uuid) {
        logger.debug("Fetching PQC verdict explanation for cryptographic asset {}", uuid);
        return restClient
                .get()
                .uri("/v1/cryptoAssets/{uuid}/pqcExplanation", uuid)
                .retrieve()
                .body(CryptographicAssetPqcExplanationDto.class);
    }

    // ---- CBOM endpoints ----

    public PaginationResponseDto<CbomDto> searchCboms(SearchRequestDto request) {
        logger.debug("Searching CBOMs");
        return restClient.post().uri("/v1/cboms").body(request).retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public CbomDetailDto getCbom(String uuid) {
        logger.debug("Fetching CBOM {}", uuid);
        return restClient.get().uri("/v1/cboms/{uuid}", uuid).retrieve().body(CbomDetailDto.class);
    }

    public List<CbomDto> getCbomVersions(String uuid) {
        logger.debug("Fetching versions of CBOM {}", uuid);
        return restClient
                .get()
                .uri("/v1/cboms/{uuid}/versions", uuid)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public PaginationResponseDto<CbomContributedAssetDto> searchCbomAssets(String uuid, SearchRequestDto request) {
        logger.debug("Searching cryptographic assets of CBOM {}", uuid);
        return restClient
                .post()
                .uri("/v1/cboms/{uuid}/assets", uuid)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ---- Signing endpoints ----

    public PaginationResponseDto<SigningProfileListDto> searchSigningProfiles(SearchRequestDto request) {
        logger.debug("Searching signing profiles");
        return restClient
                .post()
                .uri("/v1/signingProfiles/list")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public SigningProfileDto getSigningProfile(String uuid) {
        logger.debug("Fetching signing profile {}", uuid);
        return restClient.get().uri("/v1/signingProfiles/{uuid}", uuid).retrieve().body(SigningProfileDto.class);
    }

    public PaginationResponseDto<SigningRecordListDto> searchSigningRecords(SearchRequestDto request) {
        logger.debug("Searching signing records");
        return restClient
                .post()
                .uri("/v1/signingRecords")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public SigningRecordStatisticsDto getSigningStatistics(String period) {
        logger.debug("Fetching signing record statistics");
        return restClient
                .get()
                .uri(builder -> builder
                        .path("/v1/statistics/signingRecords")
                        .queryParamIfPresent("period", Optional.ofNullable(period))
                        .build())
                .retrieve()
                .body(SigningRecordStatisticsDto.class);
    }

    public PaginationResponseDto<TspProfileListDto> searchTspProfiles(SearchRequestDto request) {
        logger.debug("Searching TSP profiles");
        return restClient
                .post()
                .uri("/v1/tspProfiles/list")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    // ---- Workflow endpoints ----

    public List<TriggerDto> listTriggers() {
        logger.debug("Listing triggers");
        return restClient.get().uri("/v1/workflows/triggers").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public TriggerDetailDto getTrigger(String uuid) {
        logger.debug("Fetching trigger {}", uuid);
        return restClient.get().uri("/v1/workflows/triggers/{uuid}", uuid).retrieve().body(TriggerDetailDto.class);
    }

    public EventsSettingsDto getEventsSettings() {
        logger.debug("Fetching event settings");
        return restClient.get().uri("/v1/settings/events").retrieve().body(EventsSettingsDto.class);
    }

    public void updateEventSettings(EventSettingsDto request) {
        logger.debug("Updating the triggers of event {}", request.getEvent());
        restClient.patch().uri("/v1/settings/events").body(request).retrieve().toBodilessEntity();
    }

    public ConditionDto createCondition(ConditionRequestDto request) {
        logger.debug("Creating condition");
        return restClient.post().uri("/v1/workflows/conditions").body(request).retrieve().body(ConditionDto.class);
    }

    public RuleDetailDto createRule(RuleRequestDto request) {
        logger.debug("Creating rule");
        return restClient.post().uri("/v1/workflows/rules").body(request).retrieve().body(RuleDetailDto.class);
    }

    public ExecutionDto createExecution(ExecutionRequestDto request) {
        logger.debug("Creating execution");
        return restClient.post().uri("/v1/workflows/executions").body(request).retrieve().body(ExecutionDto.class);
    }

    public ActionDetailDto createAction(ActionRequestDto request) {
        logger.debug("Creating action");
        return restClient.post().uri("/v1/workflows/actions").body(request).retrieve().body(ActionDetailDto.class);
    }

    public TriggerDetailDto createTrigger(TriggerRequestDto request) {
        logger.debug("Creating trigger");
        return restClient.post().uri("/v1/workflows/triggers").body(request).retrieve().body(TriggerDetailDto.class);
    }

    // ---- Group, custom attribute, user and role endpoints ----

    public UuidDto createGroup(GroupRequestDto request) {
        logger.debug("Creating group");
        return restClient.post().uri("/v1/groups").body(request).retrieve().body(UuidDto.class);
    }

    public List<CustomAttributeDefinitionDto> listCustomAttributes() {
        logger.debug("Listing custom attributes");
        return restClient.get().uri("/v1/attributes/custom").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public CustomAttributeDefinitionDetailDto createCustomAttribute(CustomAttributeCreateRequestDto request) {
        logger.debug("Creating custom attribute");
        return restClient
                .post()
                .uri("/v1/attributes/custom")
                .body(request)
                .retrieve()
                .body(CustomAttributeDefinitionDetailDto.class);
    }

    public List<UserDto> listUsers() {
        logger.debug("Listing users");
        return restClient.get().uri("/v1/users").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<RoleDto> listRoles() {
        logger.debug("Listing roles");
        return restClient.get().uri("/v1/roles").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public void deleteWorkflowObject(WorkflowCollection collection, String uuid) {
        logger.debug("Deleting {} {}", collection, uuid);
        restClient
                .delete()
                .uri("/v1/workflows/{collection}/{uuid}", collection.path(), uuid)
                .retrieve()
                .toBodilessEntity();
    }

    // ---- Notification endpoints ----

    public ConnectorDto getConnector(String uuid) {
        logger.debug("Fetching connector {}", uuid);
        return restClient.get().uri("/v1/connectors/{uuid}", uuid).retrieve().body(ConnectorDto.class);
    }

    public List<ConnectorAttribute> listNotificationFields(String connectorUuid, String kind) {
        logger.debug("Fetching the notification fields of connector {}", connectorUuid);
        return restClient
                .get()
                .uri("/v1/connectors/{uuid}/attributes/notificationProvider/{kind}", connectorUuid, kind)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<ConnectorAttribute> listNotificationMappings(String connectorUuid, String kind) {
        logger.debug("Fetching the mappable notification attributes of connector {}", connectorUuid);
        return restClient
                .get()
                .uri("/v1/notificationInstances/attributes/mapping/{uuid}/{kind}", connectorUuid, kind)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<ConnectorAttribute> resolveNotificationCallback(String connectorUuid, String kind,
            Map<String, Object> request) {
        logger.debug("Resolving a notification field callback of connector {}", connectorUuid);
        return restClient
                .post()
                .uri("/v1/connectors/{uuid}/notificationProvider/{kind}/callback", connectorUuid, kind)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public List<NotificationInstanceDto> listNotificationInstances() {
        logger.debug("Listing notification instances");
        return restClient.get().uri("/v1/notificationInstances").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public UuidDto createNotificationInstance(NotificationInstanceRequest request) {
        logger.debug("Creating notification instance");
        return restClient.post().uri("/v1/notificationInstances").body(request).retrieve().body(UuidDto.class);
    }

    public void deleteNotificationInstance(String uuid) {
        logger.debug("Deleting notification instance {}", uuid);
        restClient.delete().uri("/v1/notificationInstances/{uuid}", uuid).retrieve().toBodilessEntity();
    }

    public void deleteNotificationProfile(String uuid) {
        logger.debug("Deleting notification profile {}", uuid);
        restClient.delete().uri("/v1/notificationProfiles/{uuid}", uuid).retrieve().toBodilessEntity();
    }

    public NotificationProfileResponseDto listNotificationProfiles() {
        logger.debug("Listing notification profiles");
        return restClient
                .get()
                .uri("/v1/notificationProfiles?itemsPerPage=1000&pageNumber=1")
                .retrieve()
                .body(NotificationProfileResponseDto.class);
    }

    public NotificationProfileDetailDto createNotificationProfile(NotificationProfileRequestDto request) {
        logger.debug("Creating notification profile");
        return restClient
                .post()
                .uri("/v1/notificationProfiles")
                .body(request)
                .retrieve()
                .body(NotificationProfileDetailDto.class);
    }

    public NotificationProfileDetailDto getNotificationProfile(String uuid) {
        logger.debug("Fetching notification profile {}", uuid);
        return restClient
                .get()
                .uri("/v1/notificationProfiles/{uuid}", uuid)
                .retrieve()
                .body(NotificationProfileDetailDto.class);
    }
}
