package com.otilm.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.InfrastructureServiceImpl;
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
class InfrastructureToolTest {

    private InfrastructureTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wmInfo) {
        RestClient restClient = RestClient
                .builder()
                .baseUrl(wmInfo.getHttpBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        IlmApiClient apiClient = new IlmApiClient(restClient);
        InfrastructureServiceImpl service = new InfrastructureServiceImpl(apiClient, new ObjectMapper());
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

        assertThat(result).contains("Authority Instances (1)");
        assertThat(result).contains("Test Authority");

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

        assertThat(result).contains("Connectors (1)");
        assertThat(result).contains("My Connector");

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

        assertThat(result).contains("Groups (1)");
        assertThat(result).contains("My Group");

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

        assertThat(result).contains("Token Instances (1)");
        assertThat(result).contains("SoftHSM");

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

        assertThat(result).contains("Discoveries");
        assertThat(result).contains("Scan");

        verify(postRequestedFor(urlEqualTo("/v1/discoveries/list")));
    }

    @Test
    void listRaProfilesDelegatesToService() {
        stubFor(get(urlEqualTo("/v1/raProfiles"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        [{"uuid": "rp1", "name": "Default RA", "enabled": true}]
                        """)));

        String result = tool.listRaProfiles();

        assertThat(result).contains("RA Profiles (1)");
        assertThat(result).contains("Default RA");

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

        assertThat(result).contains("Entity Instances");
        assertThat(result).contains("Web Server");

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

        assertThat(result).contains("Credentials (1)");
        assertThat(result).contains("My Credential");

        verify(getRequestedFor(urlEqualTo("/v1/credentials")));
    }
}
