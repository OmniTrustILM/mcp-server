package com.otilm.mcp.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.api.model.client.certificate.CertificateResponseDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.client.cryptography.CryptographicKeyResponseDto;
import com.otilm.api.model.client.dashboard.StatisticsDto;
import com.otilm.api.model.common.PaginationResponseDto;
import com.otilm.api.model.core.certificate.CertificateDetailDto;
import com.otilm.api.model.core.cryptography.key.KeyItemDto;
import com.otilm.api.model.core.info.CoreInfoResponseDto;
import com.otilm.api.model.core.secret.SecretDetailDto;
import com.otilm.api.model.core.secret.SecretDto;
import com.otilm.api.model.core.vault.VaultInstanceDto;
import com.otilm.api.model.core.vaultprofile.VaultProfileDto;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class IlmApiClientTest {

    private IlmApiClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        client = IlmTestSupport.client(wmRuntimeInfo);
    }

    @Test
    void getStatistics_shouldReturnStatistics() {
        stubFor(get(urlEqualTo("/v1/statistics"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "totalCertificates": 42,
                            "totalGroups": 5,
                            "totalDiscoveries": 3,
                            "totalConnectors": 2,
                            "totalRaProfiles": 4,
                            "totalCredentials": 1,
                            "totalAuthorities": 2
                        }
                        """)));

        StatisticsDto stats = client.getStatistics();

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalCertificates()).isEqualTo(42L);
        assertThat(stats.getTotalGroups()).isEqualTo(5L);
        assertThat(stats.getTotalDiscoveries()).isEqualTo(3L);
        assertThat(stats.getTotalConnectors()).isEqualTo(2L);
        assertThat(stats.getTotalRaProfiles()).isEqualTo(4L);

        verify(getRequestedFor(urlEqualTo("/v1/statistics")));
    }

    @Test
    void restClientBuilderReadsUnknownFieldsAndMissingPrimitives(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(post(urlEqualTo("/v1/keys"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "cryptographicKeys": [
                                {
                                    "uuid": "0b5e6f4c-8a3d-4f7e-9c21-5d6e7f8a9b0c",
                                    "name": "signing-key",
                                    "length": 2048,
                                    "futureField": 1
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        CryptographicKeyResponseDto response = IlmTestSupport.client(wmRuntimeInfo).searchKeys(new SearchRequestDto());

        assertThat(response.getCryptographicKeys())
                .singleElement()
                .extracting(KeyItemDto::getAssociations)
                .isEqualTo(0);
    }

    @Test
    void searchCertificates_shouldReturnPaginatedResults() {
        stubFor(post(urlEqualTo("/v1/certificates"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "certificates": [],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 5,
                            "totalItems": 42
                        }
                        """)));

        SearchRequestDto request = new SearchRequestDto();
        request.setItemsPerPage(10);
        request.setPageNumber(1);

        CertificateResponseDto response = client.searchCertificates(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalItems()).isEqualTo(42L);
        assertThat(response.getItemsPerPage()).isEqualTo(10);
        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(5);
        assertThat(response.getCertificates()).isEmpty();

        verify(postRequestedFor(urlEqualTo("/v1/certificates"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void getCertificate_shouldReturnCertificateDetail() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

        stubFor(get(urlEqualTo("/v1/certificates/" + uuid))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "%s",
                            "commonName": "test.example.com",
                            "serialNumber": "1234567890ABCDEF",
                            "issuerCommonName": "Test CA",
                            "certificateContent": "MIIBkTCB..."
                        }
                        """.formatted(uuid))));

        CertificateDetailDto detail = client.getCertificate(uuid);

        assertThat(detail).isNotNull();
        assertThat(detail.getCommonName()).isEqualTo("test.example.com");
        assertThat(detail.getSerialNumber()).isEqualTo("1234567890ABCDEF");
        assertThat(detail.getIssuerCommonName()).isEqualTo("Test CA");
        assertThat(detail.getCertificateContent()).isEqualTo("MIIBkTCB...");

        verify(getRequestedFor(urlEqualTo("/v1/certificates/" + uuid)));
    }

    @Test
    void getInfo_shouldReturnPlatformInfo() {
        stubFor(get(urlEqualTo("/v1/info"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "app": {
                                "name": "CZERTAINLY",
                                "version": "2.17.0"
                            },
                            "db": {
                                "system": "PostgreSQL",
                                "version": "16.2"
                            }
                        }
                        """)));

        CoreInfoResponseDto info = client.getInfo();

        assertThat(info).isNotNull();
        assertThat(info.getApp().getVersion()).isEqualTo("2.17.0");

        verify(getRequestedFor(urlEqualTo("/v1/info")));
    }

    @Test
    void searchSecrets_shouldReturnPaginatedResults() {
        stubFor(post(urlEqualTo("/v1/secrets"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "secret-uuid-1",
                                    "name": "db-password",
                                    "type": "basicAuth",
                                    "state": "active",
                                    "version": 1,
                                    "enabled": true
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<SecretDto> response = client.searchSecrets(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalItems()).isEqualTo(1L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getName()).isEqualTo("db-password");

        verify(postRequestedFor(urlEqualTo("/v1/secrets")));
    }

    @Test
    void getSecret_shouldReturnSecretDetail() {
        stubFor(get(urlEqualTo("/v1/secrets/secret-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "secret-1",
                            "name": "api-key-prod",
                            "type": "apiKey",
                            "state": "active",
                            "version": 3,
                            "enabled": true,
                            "createdAt": "2026-01-15T08:00:00.000Z",
                            "updatedAt": "2026-03-20T14:30:00.000Z"
                        }
                        """)));

        SecretDetailDto detail = client.getSecret("secret-1");

        assertThat(detail).isNotNull();
        assertThat(detail.getName()).isEqualTo("api-key-prod");
        assertThat(detail.getVersion()).isEqualTo(3);

        verify(getRequestedFor(urlEqualTo("/v1/secrets/secret-1")));
    }

    @Test
    void getSecretVersions_shouldReturnVersionList() {
        stubFor(get(urlEqualTo("/v1/secrets/secret-1/versions"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "version": 1,
                                "createdAt": "2026-01-15T08:00:00.000Z",
                                "fingerprint": "sha256:abc123"
                            },
                            {
                                "version": 2,
                                "createdAt": "2026-02-20T10:00:00.000Z",
                                "fingerprint": "sha256:def456"
                            }
                        ]
                        """)));

        var versions = client.getSecretVersions("secret-1");

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(1).getFingerprint()).isEqualTo("sha256:def456");

        verify(getRequestedFor(urlEqualTo("/v1/secrets/secret-1/versions")));
    }

    @Test
    void listVaultInstances_shouldReturnPaginatedResults() {
        stubFor(post(urlEqualTo("/v1/vaults/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vault-uuid-1",
                                    "name": "HashiCorp Vault",
                                    "description": "Production vault",
                                    "connector": {
                                        "uuid": "conn-1",
                                        "name": "hcv-connector"
                                    }
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<VaultInstanceDto> response = client.listVaultInstances(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalItems()).isEqualTo(1L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getName()).isEqualTo("HashiCorp Vault");

        verify(postRequestedFor(urlEqualTo("/v1/vaults/list")));
    }

    @Test
    void listVaultProfiles_shouldReturnPaginatedResults() {
        stubFor(post(urlEqualTo("/v1/vaultProfiles/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "items": [
                                {
                                    "uuid": "vp-uuid-1",
                                    "name": "prod-vault-profile",
                                    "description": "Production vault profile",
                                    "vaultInstance": {
                                        "uuid": "vault-1",
                                        "name": "HashiCorp Vault"
                                    },
                                    "enabled": true
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        SearchRequestDto request = new SearchRequestDto();
        PaginationResponseDto<VaultProfileDto> response = client.listVaultProfiles(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalItems()).isEqualTo(1L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getName()).isEqualTo("prod-vault-profile");

        verify(postRequestedFor(urlEqualTo("/v1/vaultProfiles/list")));
    }

    @Test
    void getSearchableFields_shouldReturnFieldData() {
        stubFor(get(urlEqualTo("/v1/certificates/search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        [
                                            {
                                                "filterFieldSource": "property",
                                                "searchFieldData": [
                                                    {
                                                        "fieldIdentifier": "commonName",
                                                        "fieldLabel": "Common Name",
                                                        "type": "string",
                                                        "conditions": ["EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "STARTS_WITH", "ENDS_WITH", "EMPTY", "NOT_EMPTY"],
                                                        "multiValue": false
                                                    },
                                                    {
                                                        "fieldIdentifier": "state",
                                                        "fieldLabel": "State",
                                                        "type": "list",
                                                        "conditions": ["EQUALS", "NOT_EQUALS"],
                                                        "value": ["issued", "revoked", "requested"],
                                                        "multiValue": false
                                                    }
                                                ]
                                            }
                                        ]
                                        """)));

        var result = client.getSearchableFields("certificates");

        assertThat(result).isNotNull().hasSize(1);
        assertThat(result.get(0).getSearchFieldData()).hasSize(2);
        assertThat(result.get(0).getSearchFieldData().get(0).getFieldIdentifier()).isEqualTo("commonName");
        assertThat(result.get(0).getSearchFieldData().get(1).getFieldIdentifier()).isEqualTo("state");

        verify(getRequestedFor(urlEqualTo("/v1/certificates/search")));
    }
}
