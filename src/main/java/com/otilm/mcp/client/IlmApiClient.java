package com.otilm.mcp.client;

import com.czertainly.api.model.client.certificate.CertificateResponseDto;
import com.czertainly.api.model.client.certificate.DiscoveryResponseDto;
import com.czertainly.api.model.client.certificate.EntityInstanceResponseDto;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.czertainly.api.model.client.dashboard.StatisticsDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.core.authority.AuthorityInstanceDto;
import com.czertainly.api.model.core.certificate.CertificateChainResponseDto;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateEventHistoryDto;
import com.czertainly.api.model.core.certificate.CertificateValidationResultDto;
import com.czertainly.api.model.core.certificate.group.GroupDto;
import com.czertainly.api.model.core.connector.ConnectorDto;
import com.czertainly.api.model.core.credential.CredentialDto;
import com.czertainly.api.model.core.cryptography.key.KeyDetailDto;
import com.czertainly.api.model.core.cryptography.token.TokenInstanceDto;
import com.czertainly.api.model.core.info.CoreInfoResponseDto;
import com.czertainly.api.model.core.raprofile.RaProfileDto;
import com.czertainly.api.model.core.search.SearchFieldDataByGroupDto;
import com.czertainly.api.model.core.secret.SecretDetailDto;
import com.czertainly.api.model.core.secret.SecretDto;
import com.czertainly.api.model.core.secret.SecretVersionDto;
import com.czertainly.api.model.core.vault.VaultInstanceDto;
import com.czertainly.api.model.core.vaultprofile.VaultProfileDto;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IlmApiClient {

    private static final Logger logger = LoggerFactory.getLogger(IlmApiClient.class);

    private final RestClient restClient;

    public IlmApiClient(RestClient ilmRestClient) {
        this.restClient = ilmRestClient;
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
}
