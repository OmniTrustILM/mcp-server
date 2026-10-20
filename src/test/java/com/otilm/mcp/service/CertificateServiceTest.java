package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class CertificateServiceTest {

    private CertificateService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        IlmApiClient apiClient = IlmTestSupport.client(wmInfo);
        service = new CertificateServiceImpl(apiClient);
    }

    @Test
    void shouldFormatStatistics() {
        stubFor(get(urlEqualTo("/v1/statistics"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "totalCertificates": 150,
                            "totalGroups": 8,
                            "totalDiscoveries": 5,
                            "totalConnectors": 3,
                            "totalRaProfiles": 6,
                            "totalAuthorities": 2,
                            "totalCredentials": 4,
                            "certificateStatByState": {
                                "issued": 120,
                                "revoked": 15,
                                "expired": 15
                            },
                            "certificateStatByKeySize": {
                                "2048": 80,
                                "4096": 70
                            },
                            "certificateStatByExpiry": {},
                            "certificateStatByValidationStatus": {},
                            "certificateStatByComplianceStatus": {}
                        }
                        """)));

        String result = service.getStatistics();

        assertThat(result)
                .contains("ILM Platform Statistics", "Total Certificates: 150", "Total Groups: 8",
                        "Total Discoveries: 5", "Total Connectors: 3", "Total RA Profiles: 6", "Total Authorities: 2",
                        "Total Credentials: 4", "Certificates by State:", "issued: 120", "revoked: 15", "expired: 15",
                        "Certificates by Key Size:", "2048: 80", "4096: 70");
    }

    @Test
    void shouldFormatSearchCertificates() {
        stubFor(post(urlEqualTo("/v1/certificates"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "certificates": [
                                {
                                    "uuid": "3f1c2a4e-5b6d-4c7e-8f90-a1b2c3d4e5f6",
                                    "commonName": "web.example.com",
                                    "serialNumber": "AABB11",
                                    "state": "issued",
                                    "validationStatus": "valid",
                                    "publicKeyAlgorithm": "RSA",
                                    "keySize": 2048,
                                    "notAfter": "2027-01-01T00:00:00.000Z"
                                },
                                {
                                    "uuid": "7a8b9c0d-1e2f-4a3b-9c4d-5e6f7a8b9c0d",
                                    "commonName": "api.example.com",
                                    "serialNumber": "CCDD22",
                                    "state": "issued",
                                    "validationStatus": "valid",
                                    "publicKeyAlgorithm": "ECDSA",
                                    "keySize": 256,
                                    "notAfter": "2026-06-15T00:00:00.000Z"
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 2
                        }
                        """)));

        String result = service.searchCertificates(null, null, null);

        assertThat(result)
                .contains("Found 2 certificates", "web.example.com", "api.example.com", "RSA", "ECDSA",
                        "3f1c2a4e-5b6d-4c7e-8f90-a1b2c3d4e5f6", "7a8b9c0d-1e2f-4a3b-9c4d-5e6f7a8b9c0d",
                        "Serial: AABB11", "Serial: CCDD22");
    }

    @Test
    void shouldFormatCertificateDetail() {
        stubFor(get(urlEqualTo("/v1/certificates/uuid-1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "uuid-1",
                            "commonName": "server.example.com",
                            "serialNumber": "FF1122334455",
                            "subjectDn": "CN=server.example.com,O=Example Corp,C=US",
                            "issuerCommonName": "Example Intermediate CA",
                            "issuerDn": "CN=Example Intermediate CA,O=Example Corp,C=US",
                            "notBefore": "2025-01-01T00:00:00.000Z",
                            "notAfter": "2026-01-01T00:00:00.000Z",
                            "state": "issued",
                            "validationStatus": "valid",
                            "publicKeyAlgorithm": "RSA",
                            "keySize": 4096,
                            "signatureAlgorithm": "SHA256withRSA",
                            "complianceStatus": "ok",
                            "fingerprint": "AB:CD:EF:12:34:56:78:90",
                            "certificateContent": "MIIBkTCB..."
                        }
                        """)));

        String result = service.getCertificate("uuid-1");

        assertThat(result)
                .contains("Certificate: server.example.com", "UUID: uuid-1", "Serial: FF1122334455",
                        "Subject DN: CN=server.example.com,O=Example Corp,C=US", "Issuer: Example Intermediate CA",
                        "Issuer DN: CN=Example Intermediate CA,O=Example Corp,C=US", "Key Algorithm: RSA 4096",
                        "Signature Algorithm: SHA256withRSA", "State: issued", "Validation: valid", "Compliance: ok",
                        "Fingerprint: AB:CD:EF:12:34:56:78:90");
    }

    @Test
    void shouldFormatCertificateHistory() {
        stubFor(get(urlEqualTo("/v1/certificates/uuid-1/history"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "event-uuid-1",
                                "event": "Issue Certificate",
                                "status": "SUCCESS",
                                "message": "Certificate issued successfully",
                                "created": "2025-03-15T10:30:00.000Z",
                                "createdBy": "admin@example.com"
                            }
                        ]
                        """)));

        String result = service.getCertificateHistory("uuid-1");

        assertThat(result)
                .contains("Event History for Certificate uuid-1", "Issue Certificate", "SUCCESS", "admin@example.com",
                        "Certificate issued successfully");
    }

    @Test
    void shouldFormatStatisticsWithSecretsData() {
        stubFor(get(urlEqualTo("/v1/statistics"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "totalCertificates": 150,
                            "totalGroups": 8,
                            "totalDiscoveries": 5,
                            "totalConnectors": 3,
                            "totalRaProfiles": 6,
                            "totalAuthorities": 2,
                            "totalCredentials": 4,
                            "totalSecrets": 25,
                            "totalVaultInstances": 2,
                            "totalVaultProfiles": 3,
                            "certificateStatByState": {
                                "issued": 120,
                                "revoked": 30
                            },
                            "secretStatByType": {
                                "basicAuth": 10,
                                "apiKey": 8,
                                "generic": 7
                            },
                            "secretStatByState": {
                                "active": 20,
                                "expired": 5
                            }
                        }
                        """)));

        String result = service.getStatistics();

        assertThat(result)
                .contains("Total Certificates: 150", "Total Secrets: 25", "Total Vault Instances: 2",
                        "Total Vault Profiles: 3", "Secrets by Type:", "basicAuth: 10", "apiKey: 8",
                        "Secrets by State:", "active: 20", "expired: 5");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(get(urlEqualTo("/v1/statistics"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody("""
                        {"message": "Internal Server Error"}
                        """)));

        String result = service.getStatistics();

        assertThat(result).contains("Error");
    }

    @Test
    void shouldPassFiltersToSearchRequest() {
        stubFor(post(urlEqualTo("/v1/certificates"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "certificates": [
                                {
                                    "uuid": "9d8c7b6a-5f4e-4d3c-8b2a-1f0e9d8c7b6a",
                                    "commonName": "example.com",
                                    "serialNumber": "ABC123",
                                    "state": "issued",
                                    "publicKeyAlgorithm": "RSA",
                                    "keySize": 2048
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String filters = """
                [{"fieldSource":"property","fieldIdentifier":"commonName","condition":"CONTAINS","value":"example.com"}]
                """;
        String result = service.searchCertificates(filters, 10, 1);

        assertThat(result).contains("example.com").contains("1 certificates");

        verify(postRequestedFor(urlEqualTo("/v1/certificates"))
                .withRequestBody(containing("\"fieldIdentifier\":\"commonName\""))
                .withRequestBody(containing("\"condition\":\"CONTAINS\""))
                .withRequestBody(containing("\"value\":\"example.com\"")));
    }

    @Test
    void shouldReturnErrorForInvalidFiltersJson() {
        String result = service.searchCertificates("not valid json", null, null);

        assertThat(result).contains("Invalid filters JSON");
    }
}
