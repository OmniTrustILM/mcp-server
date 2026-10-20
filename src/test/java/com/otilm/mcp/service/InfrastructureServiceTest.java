package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        IlmApiClient apiClient = IlmTestSupport.client(wmInfo);
        service = new InfrastructureServiceImpl(apiClient);
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

        assertThat(result)
                .contains("Authority Instances (2)", "ADCS Authority", "auth-uuid-1", "EJBCA Authority", "auth-uuid-2",
                        "Status: connected", "Connector: MS-ADCS-Connector", "Kind: ADCS");
    }

    @Test
    void authorityConnectorComesFromConnectorReference() {
        stubFor(get(urlEqualTo("/v1/authorities"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        [
                                            {
                                                "uuid": "4a5b6c7d-8e9f-4a0b-9c1d-2e3f4a5b6c7d",
                                                "name": "issuing-ca",
                                                "status": "connected",
                                                "connector": {"uuid": "5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e", "name": "authority-connector"},
                                                "connectorName": "legacy-connector-name",
                                                "kind": "EJBCA"
                                            }
                                        ]
                                        """)));

        assertThat(service.listAuthorities())
                .contains("  Connector: authority-connector")
                .doesNotContain("legacy-connector-name");
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

        assertThat(result)
                .contains("Connectors (1)", "MS-ADCS-Connector", "conn-uuid-1", "Status: connected",
                        "URL: https://adcs.example.com/api", "Auth Type: certificate", "Function Groups: 1");
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

        assertThat(result)
                .contains("Groups (2)", "Production Certificates", "group-uuid-1",
                        "Description: Group for production certificates", "Email: certs@example.com",
                        "Development Certificates", "group-uuid-2");
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

        assertThat(result)
                .contains("Credentials (1)", "ADCS-Service-Credential", "cred-uuid-1", "Enabled: true", "Kind: Basic",
                        "Connector: MS-ADCS-Connector");
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

        assertThat(result)
                .contains("Token Instances (1)", "SoftHSM-Instance", "token-uuid-1", "Status: Connected",
                        "Connector: SoftHSM-Connector", "Kind: SoftHSM");
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

        assertThat(result)
                .contains("Discoveries (1 total", "Network Scan Q1", "disc-uuid-1", "Status: completed",
                        "Kind: IP-Hostname", "Connector: Network-Discovery-Connector", "Certificates Discovered: 42");
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

        assertThat(result)
                .contains("RA Profiles (1)", "Web Server Profile", "ra-uuid-1", "Enabled: true",
                        "Description: Profile for web server certificates", "Authority: ADCS Authority");
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

        assertThat(result)
                .contains("Entity Instances (1 total", "Web Server Entity", "entity-uuid-1", "Status: connected",
                        "Connector: Entity-Connector", "Kind: Keystore");
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
