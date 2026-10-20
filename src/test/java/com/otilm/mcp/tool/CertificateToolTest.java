package com.otilm.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.CertificateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class CertificateToolTest {

    private CertificateTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        CertificateServiceImpl service = new CertificateServiceImpl(apiClient, new ObjectMapper());
        tool = new CertificateTool(service);
    }

    @Test
    void getStatisticsDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/statistics"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "totalCertificates": 10,
                            "totalGroups": 2,
                            "totalDiscoveries": 1,
                            "totalConnectors": 1,
                            "totalRaProfiles": 1,
                            "totalAuthorities": 1,
                            "totalCredentials": 1
                        }
                        """)));

        String result = tool.getStatistics();

        assertThat(result).contains("ILM Platform Statistics");
        assertThat(result).contains("Total Certificates: 10");

        verify(getRequestedFor(urlEqualTo("/v1/statistics")));
    }

    @Test
    void searchCertificatesDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/certificates"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "certificates": [
                                {
                                    "uuid": "cert-1",
                                    "commonName": "test.example.com",
                                    "serialNumber": "AABB",
                                    "state": "issued",
                                    "validationStatus": "valid",
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

        String result = tool.searchCertificates(null, 10, 1);

        assertThat(result).contains("Found 1 certificates");
        assertThat(result).contains("test.example.com");

        verify(postRequestedFor(urlEqualTo("/v1/certificates")));
    }

    @Test
    void getCertificateDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/certificates/cert-uuid"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "uuid": "cert-uuid",
                            "commonName": "detail.example.com",
                            "serialNumber": "112233",
                            "subjectDn": "CN=detail.example.com",
                            "issuerCommonName": "Test CA",
                            "issuerDn": "CN=Test CA",
                            "state": "issued",
                            "validationStatus": "valid",
                            "publicKeyAlgorithm": "RSA",
                            "keySize": 2048,
                            "complianceStatus": "ok",
                            "certificateContent": "MIIBkTCB..."
                        }
                        """)));

        String result = tool.getCertificate("cert-uuid");

        assertThat(result).contains("Certificate: detail.example.com");
        assertThat(result).contains("UUID: cert-uuid");

        verify(getRequestedFor(urlEqualTo("/v1/certificates/cert-uuid")));
    }

    @Test
    void validateCertificateDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/certificates/cert-uuid/validate"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "resultStatus": "valid",
                            "validationChecks": {}
                        }
                        """)));

        String result = tool.validateCertificate("cert-uuid");

        assertThat(result).contains("Validation Result for Certificate cert-uuid");

        verify(getRequestedFor(urlEqualTo("/v1/certificates/cert-uuid/validate")));
    }

    @Test
    void getCertificateChainDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/certificates/cert-uuid/chain?withEndCertificate=true"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "completeChain": true,
                            "certificates": []
                        }
                        """)));

        String result = tool.getCertificateChain("cert-uuid");

        assertThat(result).contains("Certificate Chain for cert-uuid");

        verify(getRequestedFor(urlEqualTo("/v1/certificates/cert-uuid/chain?withEndCertificate=true")));
    }

    @Test
    void getCertificateHistoryDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/certificates/cert-uuid/history"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "evt-1",
                                "event": "Issue Certificate",
                                "status": "SUCCESS",
                                "message": "Issued",
                                "created": "2025-03-01T10:00:00.000Z",
                                "createdBy": "admin"
                            }
                        ]
                        """)));

        String result = tool.getCertificateHistory("cert-uuid");

        assertThat(result).contains("Event History for Certificate cert-uuid");
        assertThat(result).contains("Issue Certificate");
        assertThat(result).contains("SUCCESS");

        verify(getRequestedFor(urlEqualTo("/v1/certificates/cert-uuid/history")));
    }

    @Test
    void searchCertificatesWithFiltersDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/certificates"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "certificates": [
                                {
                                    "uuid": "cert-1",
                                    "commonName": "filtered.example.com",
                                    "serialNumber": "DEF456",
                                    "state": "issued",
                                    "publicKeyAlgorithm": "RSA",
                                    "keySize": 4096
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String filters = """
                [{"fieldSource":"property","fieldIdentifier":"commonName","condition":"CONTAINS","value":"filtered"}]
                """;
        String result = tool.searchCertificates(filters, 10, 1);

        assertThat(result).contains("filtered.example.com").contains("1 certificates");
    }
}
