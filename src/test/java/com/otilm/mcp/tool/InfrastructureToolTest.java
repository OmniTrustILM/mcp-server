package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.InfrastructureServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
class InfrastructureToolTest {

    private InfrastructureTool tool;
    private InfrastructureServiceImpl service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        IlmApiClient apiClient = IlmTestSupport.client(wmInfo);
        service = new InfrastructureServiceImpl(apiClient);
        tool = new InfrastructureTool(service);
    }

    @Test
    void listAuthoritiesDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/authorities"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "auth-1",
                                "name": "Test Authority",
                                "status": "connected",
                                "connectorName": "Test-Connector",
                                "kind": "ADCS"
                            }
                        ]
                        """)));

        String result = tool.listAuthorities();
        assertThat(new AuthorityInventoryTool(service).listAuthorities()).isEqualTo(result);

        assertThat(result).contains("Authority Instances (1)", "Test Authority");

        verify(getRequestedFor(urlEqualTo("/v1/authorities")));
    }

    @Test
    void listConnectorsDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/connectors"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "conn-1",
                                "name": "My Connector",
                                "status": "connected",
                                "url": "https://connector.example.com",
                                "authType": "none"
                            }
                        ]
                        """)));

        String result = tool.listConnectors();
        assertThat(new ConnectorInventoryTool(service).listConnectors()).isEqualTo(result);

        assertThat(result).contains("Connectors (1)", "My Connector");

        verify(getRequestedFor(urlEqualTo("/v1/connectors")));
    }

    @Test
    void listGroupsDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/groups"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "grp-1",
                                "name": "My Group",
                                "description": "Test group"
                            }
                        ]
                        """)));

        String result = tool.listGroups();
        assertThat(new GroupTool(service).listGroups()).isEqualTo(result);

        assertThat(result).contains("Groups (1)", "My Group");

        verify(getRequestedFor(urlEqualTo("/v1/groups")));
    }

    @Test
    void listTokenInstancesDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/tokens"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "tok-1",
                                "name": "SoftHSM",
                                "status": "Connected",
                                "connectorName": "Crypto-Connector"
                            }
                        ]
                        """)));

        String result = tool.listTokenInstances();
        assertThat(new TokenInventoryTool(service).listTokenInstances()).isEqualTo(result);

        assertThat(result).contains("Token Instances (1)", "SoftHSM");

        verify(getRequestedFor(urlEqualTo("/v1/tokens")));
    }

    @Test
    void listDiscoveriesDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/discoveries/list"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                        {
                                            "discoveries": [{"uuid": "d1", "name": "Scan", "status": "completed", "totalCertificatesDiscovered": 10}],
                                            "itemsPerPage": 10, "pageNumber": 1, "totalPages": 1, "totalItems": 1
                                        }
                                        """)));

        String result = tool.listDiscoveries(null);
        assertThat(new DiscoveryInventoryTool(service).listDiscoveries(null)).isEqualTo(result);

        assertThat(result).contains("Discoveries", "Scan");

        verify(postRequestedFor(urlEqualTo("/v1/discoveries/list")));
    }

    @Test
    void listRaProfilesDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/raProfiles"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [{"uuid": "rp1", "name": "Default RA", "enabled": true}]
                        """)));

        String result = tool.listRaProfiles();
        assertThat(new RaProfileInventoryTool(service).listRaProfiles()).isEqualTo(result);

        assertThat(result).contains("RA Profiles (1)", "Default RA");

        verify(getRequestedFor(urlEqualTo("/v1/raProfiles")));
    }

    @Test
    void listEntitiesDelegatesToService() {
        stubFor(post(urlEqualTo("/v1/entities/list"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {
                            "entities": [{"uuid": "e1", "name": "Web Server", "status": "active"}],
                            "itemsPerPage": 10, "pageNumber": 1, "totalPages": 1, "totalItems": 1
                        }
                        """)));

        String result = tool.listEntities(null);
        assertThat(new EntityInventoryTool(service).listEntities(null)).isEqualTo(result);

        assertThat(result).contains("Entity Instances", "Web Server");

        verify(postRequestedFor(urlEqualTo("/v1/entities/list")));
    }

    @Test
    void listCredentialsDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/credentials"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [
                            {
                                "uuid": "cred-1",
                                "name": "My Credential",
                                "enabled": true,
                                "kind": "ApiKey"
                            }
                        ]
                        """)));

        String result = tool.listCredentials();

        assertThat(result).contains("Credentials (1)", "My Credential");

        verify(getRequestedFor(urlEqualTo("/v1/credentials")));
    }

    @Test
    void createGroupDelegatesAndNeedsReadWrite() {
        stubFor(post(urlEqualTo("/v1/groups"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"uuid\":\"00000000-0000-4000-8000-000000000300\"}")));

        assertThat(new GroupTool(service).createGroup("Web servers", null, null))
                .isEqualTo(tool.createGroup("Web servers", null, null));
        assertThat(tool.createGroup("Web servers", null, null))
                .isEqualTo("Created group Web servers [00000000-0000-4000-8000-000000000300].");
        assertThat(ToolNames.allowed(AccessLevel.READ_ONLY, new GroupTool(null))).doesNotContain("create_group");
        assertThat(ToolNames.allowed(AccessLevel.READ_WRITE, new GroupTool(null))).contains("create_group");
    }
}
