package com.otilm.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

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
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        service = new CertificateServiceImpl(apiClient, new ObjectMapper());
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

        assertThat(result).contains("ILM Platform Statistics");
        assertThat(result).contains("Total Certificates: 150");
        assertThat(result).contains("Total Groups: 8");
        assertThat(result).contains("Total Discoveries: 5");
        assertThat(result).contains("Total Connectors: 3");
        assertThat(result).contains("Total RA Profiles: 6");
        assertThat(result).contains("Total Authorities: 2");
        assertThat(result).contains("Total Credentials: 4");
        assertThat(result).contains("Certificates by State:");
        assertThat(result).contains("issued: 120");
        assertThat(result).contains("revoked: 15");
        assertThat(result).contains("expired: 15");
        assertThat(result).contains("Certificates by Key Size:");
        assertThat(result).contains("2048: 80");
        assertThat(result).contains("4096: 70");
    }

    @Test
    void shouldFormatSearchCertificates() {
        stubFor(post(urlEqualTo("/v1/certificates"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "certificates": [
                                {
                                    "uuid": "cert-uuid-1",
                                    "commonName": "web.example.com",
                                    "serialNumber": "AABB11",
                                    "state": "issued",
                                    "validationStatus": "valid",
                                    "publicKeyAlgorithm": "RSA",
                                    "keySize": 2048,
                                    "notAfter": "2027-01-01T00:00:00.000Z"
                                },
                                {
                                    "uuid": "cert-uuid-2",
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

        assertThat(result).contains("Found 2 certificates");
        assertThat(result).contains("web.example.com");
        assertThat(result).contains("api.example.com");
        assertThat(result).contains("RSA");
        assertThat(result).contains("ECDSA");
        assertThat(result).contains("cert-uuid-1");
        assertThat(result).contains("cert-uuid-2");
        assertThat(result).contains("Serial: AABB11");
        assertThat(result).contains("Serial: CCDD22");
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

        assertThat(result).contains("Certificate: server.example.com");
        assertThat(result).contains("UUID: uuid-1");
        assertThat(result).contains("Serial: FF1122334455");
        assertThat(result).contains("Subject DN: CN=server.example.com,O=Example Corp,C=US");
        assertThat(result).contains("Issuer: Example Intermediate CA");
        assertThat(result).contains("Issuer DN: CN=Example Intermediate CA,O=Example Corp,C=US");
        assertThat(result).contains("Key Algorithm: RSA 4096");
        assertThat(result).contains("Signature Algorithm: SHA256withRSA");
        assertThat(result).contains("State: issued");
        assertThat(result).contains("Validation: valid");
        assertThat(result).contains("Compliance: ok");
        assertThat(result).contains("Fingerprint: AB:CD:EF:12:34:56:78:90");
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

        assertThat(result).contains("Event History for Certificate uuid-1");
        assertThat(result).contains("Issue Certificate");
        assertThat(result).contains("SUCCESS");
        assertThat(result).contains("admin@example.com");
        assertThat(result).contains("Certificate issued successfully");
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

        assertThat(result).contains("Total Certificates: 150");
        assertThat(result).contains("Total Secrets: 25");
        assertThat(result).contains("Total Vault Instances: 2");
        assertThat(result).contains("Total Vault Profiles: 3");
        assertThat(result).contains("Secrets by Type:");
        assertThat(result).contains("basicAuth: 10");
        assertThat(result).contains("apiKey: 8");
        assertThat(result).contains("Secrets by State:");
        assertThat(result).contains("active: 20");
        assertThat(result).contains("expired: 5");
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
                                    "uuid": "cert-1",
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
