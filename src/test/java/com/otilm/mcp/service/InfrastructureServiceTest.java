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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class InfrastructureServiceTest {

    private InfrastructureService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        service = new InfrastructureServiceImpl(apiClient, new ObjectMapper());
    }

    @Test
    void shouldFormatAuthorities() {
        stubFor(get(urlEqualTo("/v1/authorities"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "auth-uuid-1",
                                "name": "ADCS Authority",
                                "status": "connected",
                                "connectorName": "MS-ADCS-Connector",
                                "kind": "ADCS"
                            },
                            {
                                "uuid": "auth-uuid-2",
                                "name": "EJBCA Authority",
                                "status": "connected",
                                "connectorName": "EJBCA-Connector",
                                "kind": "EJBCA"
                            }
                        ]
                        """)));

        String result = service.listAuthorities();

        assertThat(result).contains("Authority Instances (2)");
        assertThat(result).contains("ADCS Authority");
        assertThat(result).contains("auth-uuid-1");
        assertThat(result).contains("EJBCA Authority");
        assertThat(result).contains("auth-uuid-2");
        assertThat(result).contains("Status: connected");
        assertThat(result).contains("Connector: MS-ADCS-Connector");
        assertThat(result).contains("Kind: ADCS");
    }

    @Test
    void shouldFormatConnectors() {
        stubFor(get(urlEqualTo("/v1/connectors"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "conn-uuid-1",
                                "name": "MS-ADCS-Connector",
                                "status": "connected",
                                "url": "https://adcs.example.com/api",
                                "authType": "certificate",
                                "functionGroups": [
                                    {"functionGroupCode": "authorityProvider", "name": "authorityProvider"}
                                ]
                            }
                        ]
                        """)));

        String result = service.listConnectors();

        assertThat(result).contains("Connectors (1)");
        assertThat(result).contains("MS-ADCS-Connector");
        assertThat(result).contains("conn-uuid-1");
        assertThat(result).contains("Status: connected");
        assertThat(result).contains("URL: https://adcs.example.com/api");
        assertThat(result).contains("Auth Type: certificate");
        assertThat(result).contains("Function Groups: 1");
    }

    @Test
    void shouldFormatGroups() {
        stubFor(get(urlEqualTo("/v1/groups"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "group-uuid-1",
                                "name": "Production Certificates",
                                "description": "Group for production certificates",
                                "email": "certs@example.com"
                            },
                            {
                                "uuid": "group-uuid-2",
                                "name": "Development Certificates",
                                "description": "Group for dev certificates"
                            }
                        ]
                        """)));

        String result = service.listGroups();

        assertThat(result).contains("Groups (2)");
        assertThat(result).contains("Production Certificates");
        assertThat(result).contains("group-uuid-1");
        assertThat(result).contains("Description: Group for production certificates");
        assertThat(result).contains("Email: certs@example.com");
        assertThat(result).contains("Development Certificates");
        assertThat(result).contains("group-uuid-2");
    }

    @Test
    void shouldFormatCredentials() {
        stubFor(get(urlEqualTo("/v1/credentials"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "cred-uuid-1",
                                "name": "ADCS-Service-Credential",
                                "enabled": true,
                                "kind": "Basic",
                                "connectorName": "MS-ADCS-Connector"
                            }
                        ]
                        """)));

        String result = service.listCredentials();

        assertThat(result).contains("Credentials (1)");
        assertThat(result).contains("ADCS-Service-Credential");
        assertThat(result).contains("cred-uuid-1");
        assertThat(result).contains("Enabled: true");
        assertThat(result).contains("Kind: Basic");
        assertThat(result).contains("Connector: MS-ADCS-Connector");
    }

    @Test
    void shouldFormatTokenInstances() {
        stubFor(get(urlEqualTo("/v1/tokens"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "token-uuid-1",
                                "name": "SoftHSM-Instance",
                                "status": "Connected",
                                "connectorName": "SoftHSM-Connector",
                                "kind": "SoftHSM",
                                "tokenProfiles": 3
                            }
                        ]
                        """)));

        String result = service.listTokenInstances();

        assertThat(result).contains("Token Instances (1)");
        assertThat(result).contains("SoftHSM-Instance");
        assertThat(result).contains("token-uuid-1");
        assertThat(result).contains("Status: Connected");
        assertThat(result).contains("Connector: SoftHSM-Connector");
        assertThat(result).contains("Kind: SoftHSM");
    }

    @Test
    void shouldFormatDiscoveries() {
        stubFor(post(urlEqualTo("/v1/discoveries/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "discoveries": [
                                {
                                    "uuid": "disc-uuid-1",
                                    "name": "Network Scan Q1",
                                    "status": "completed",
                                    "kind": "IP-Hostname",
                                    "connectorName": "Network-Discovery-Connector",
                                    "startTime": "2025-03-01T08:00:00.000Z",
                                    "endTime": "2025-03-01T12:00:00.000Z",
                                    "totalCertificatesDiscovered": 42
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String result = service.listDiscoveries(null);

        assertThat(result).contains("Discoveries (1 total");
        assertThat(result).contains("Network Scan Q1");
        assertThat(result).contains("disc-uuid-1");
        assertThat(result).contains("Status: completed");
        assertThat(result).contains("Kind: IP-Hostname");
        assertThat(result).contains("Connector: Network-Discovery-Connector");
        assertThat(result).contains("Certificates Discovered: 42");
    }

    @Test
    void shouldFormatRaProfiles() {
        stubFor(get(urlEqualTo("/v1/raProfiles"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "ra-uuid-1",
                                "name": "Web Server Profile",
                                "enabled": true,
                                "description": "Profile for web server certificates",
                                "authorityInstanceName": "ADCS Authority"
                            }
                        ]
                        """)));

        String result = service.listRaProfiles();

        assertThat(result).contains("RA Profiles (1)");
        assertThat(result).contains("Web Server Profile");
        assertThat(result).contains("ra-uuid-1");
        assertThat(result).contains("Enabled: true");
        assertThat(result).contains("Description: Profile for web server certificates");
        assertThat(result).contains("Authority: ADCS Authority");
    }

    @Test
    void shouldFormatEntities() {
        stubFor(post(urlEqualTo("/v1/entities/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "entities": [
                                {
                                    "uuid": "entity-uuid-1",
                                    "name": "Web Server Entity",
                                    "status": "connected",
                                    "connectorName": "Entity-Connector",
                                    "kind": "Keystore"
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String result = service.listEntities(null);

        assertThat(result).contains("Entity Instances (1 total");
        assertThat(result).contains("Web Server Entity");
        assertThat(result).contains("entity-uuid-1");
        assertThat(result).contains("Status: connected");
        assertThat(result).contains("Connector: Entity-Connector");
        assertThat(result).contains("Kind: Keystore");
    }

    @Test
    void shouldPassFiltersToEntities() {
        stubFor(post(urlEqualTo("/v1/entities/list"))
                .withRequestBody(matchingJsonPath("$.filters[0].fieldSource", equalTo("property")))
                .withRequestBody(matchingJsonPath("$.filters[0].fieldIdentifier", equalTo("name")))
                .withRequestBody(matchingJsonPath("$.filters[0].condition", equalTo("CONTAINS")))
                .withRequestBody(matchingJsonPath("$.filters[0].value", equalTo("web")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "entities": [
                                {
                                    "uuid": "entity-uuid-1",
                                    "name": "Web Server Entity",
                                    "status": "connected"
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String filters = """
                [{"fieldSource":"property","fieldIdentifier":"name","condition":"CONTAINS","value":"web"}]
                """;
        String result = service.listEntities(filters);

        assertThat(result).contains("Web Server Entity");
        verify(postRequestedFor(urlEqualTo("/v1/entities/list"))
                .withRequestBody(matchingJsonPath("$.filters[0].fieldSource", equalTo("property"))));
    }

    @Test
    void shouldPassFiltersToDiscoveries() {
        stubFor(post(urlEqualTo("/v1/discoveries/list"))
                .withRequestBody(matchingJsonPath("$.filters[0].fieldSource", equalTo("property")))
                .withRequestBody(matchingJsonPath("$.filters[0].fieldIdentifier", equalTo("name")))
                .withRequestBody(matchingJsonPath("$.filters[0].condition", equalTo("CONTAINS")))
                .withRequestBody(matchingJsonPath("$.filters[0].value", equalTo("scan")))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "discoveries": [
                                {
                                    "uuid": "disc-uuid-1",
                                    "name": "Network Scan",
                                    "status": "completed",
                                    "totalCertificatesDiscovered": 10
                                }
                            ],
                            "itemsPerPage": 10,
                            "pageNumber": 1,
                            "totalPages": 1,
                            "totalItems": 1
                        }
                        """)));

        String filters = """
                [{"fieldSource":"property","fieldIdentifier":"name","condition":"CONTAINS","value":"scan"}]
                """;
        String result = service.listDiscoveries(filters);

        assertThat(result).contains("Network Scan");
        verify(postRequestedFor(urlEqualTo("/v1/discoveries/list"))
                .withRequestBody(matchingJsonPath("$.filters[0].fieldSource", equalTo("property"))));
    }

    @Test
    void shouldReturnErrorForInvalidEntitiesFiltersJson() {
        String result = service.listEntities("bad json");
        assertThat(result).contains("Invalid filters JSON");
    }

    @Test
    void shouldReturnErrorForInvalidDiscoveriesFiltersJson() {
        String result = service.listDiscoveries("bad json");
        assertThat(result).contains("Invalid filters JSON");
    }

    @Test
    void shouldHandleApiError() {
        stubFor(get(urlEqualTo("/v1/authorities"))
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "application/json").withBody("""
                        {"message": "Internal Server Error"}
                        """)));

        String result = service.listAuthorities();

        assertThat(result).contains("Error listing authorities");
    }
}
