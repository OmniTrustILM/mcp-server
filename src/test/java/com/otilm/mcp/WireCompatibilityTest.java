package com.otilm.mcp;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.service.AttributeServiceImpl;
import com.otilm.mcp.service.CertificateServiceImpl;
import com.otilm.mcp.service.InfrastructureServiceImpl;
import com.otilm.mcp.service.KeyServiceImpl;
import com.otilm.mcp.service.PlatformVersionCheck;
import com.otilm.mcp.service.SearchServiceImpl;
import com.otilm.mcp.service.SecretServiceImpl;
import com.otilm.mcp.service.UserServiceImpl;
import com.otilm.mcp.service.VaultServiceImpl;
import com.otilm.mcp.service.WorkflowServiceImpl;
import com.otilm.mcp.tool.AttributeTool;
import com.otilm.mcp.tool.CertificateTool;
import com.otilm.mcp.tool.InfrastructureTool;
import com.otilm.mcp.tool.KeyTool;
import com.otilm.mcp.tool.SearchTool;
import com.otilm.mcp.tool.SecretTool;
import com.otilm.mcp.tool.UserTool;
import com.otilm.mcp.tool.VaultTool;
import com.otilm.mcp.tool.WorkflowTool;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@WireMockTest
class WireCompatibilityTest {

    private static final String CERTIFICATE_UUID = "870576fe-04e3-4e01-986e-61369be6a098";
    private static final String KEY_UUID = "7f248902-d5b4-4b4a-8799-ad22424097db";
    private static final String SECRET_UUID = "3fd5f538-a516-4002-a97f-9b6184d9f709";

    private Tools tools;

    record Tools(CertificateTool certificates, KeyTool keys, InfrastructureTool infrastructure, SearchTool search,
            SecretTool secrets, VaultTool vaults, WorkflowTool workflows, AttributeTool attributes, UserTool users) {
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        IlmApiClient client = IlmTestSupport.client(wmInfo);
        tools = new Tools(new CertificateTool(new CertificateServiceImpl(client)),
                new KeyTool(new KeyServiceImpl(client)), new InfrastructureTool(new InfrastructureServiceImpl(client)),
                new SearchTool(new SearchServiceImpl(client, new PlatformVersionCheck(client))),
                new SecretTool(new SecretServiceImpl(client)), new VaultTool(new VaultServiceImpl(client)),
                new WorkflowTool(new WorkflowServiceImpl(client)), new AttributeTool(new AttributeServiceImpl(client)),
                new UserTool(new UserServiceImpl(client)));
    }

    static Stream<Arguments> recordedResponses() {
        String certificate = "/v1/certificates/" + CERTIFICATE_UUID;
        return Stream
                .of(recorded("get_statistics", get(urlEqualTo("/v1/statistics")), "statistics",
                        t -> t.certificates().getStatistics(), "Total Certificates: 161175"),
                        recorded("search_certificates", post(urlEqualTo("/v1/certificates")), "certificates",
                                t -> t.certificates().searchCertificates(null, null, null), "commonName-1.example.com"),
                        recorded("get_certificate", get(urlEqualTo(certificate)), "certificate",
                                t -> t.certificates().getCertificate(CERTIFICATE_UUID),
                                "Certificate: commonName-3.example.com"),
                        recorded("validate_certificate", get(urlEqualTo(certificate + "/validate")),
                                "certificate-validation", t -> t.certificates().validateCertificate(CERTIFICATE_UUID),
                                "Status: INVALID"),
                        recorded("get_certificate_chain",
                                get(urlEqualTo(certificate + "/chain?withEndCertificate=true")), "certificate-chain",
                                t -> t.certificates().getCertificateChain(CERTIFICATE_UUID),
                                "commonName-4.example.com"),
                        recorded("get_certificate_history", get(urlEqualTo(certificate + "/history")),
                                "certificate-history", t -> t.certificates().getCertificateHistory(CERTIFICATE_UUID),
                                "Message: message-8"),
                        recorded("search_keys", post(urlEqualTo("/v1/keys")), "keys",
                                t -> t.keys().searchKeys(null, null, null), "Key UUID: " + KEY_UUID),
                        recorded("get_key", get(urlEqualTo("/v1/keys/" + KEY_UUID)), "key",
                                t -> t.keys().getKey(KEY_UUID), "Key: name-11"),
                        recorded("list_authorities", get(urlEqualTo("/v1/authorities")), "authorities",
                                t -> t.infrastructure().listAuthorities(), "name-14"),
                        recorded("list_ra_profiles", get(urlEqualTo("/v1/raProfiles")), "ra-profiles",
                                t -> t.infrastructure().listRaProfiles(), "name-18"),
                        recorded("list_connectors", get(urlEqualTo("/v1/connectors")), "connectors",
                                t -> t.infrastructure().listConnectors(), "name-20"),
                        recorded("list_groups", get(urlEqualTo("/v1/groups")), "groups",
                                t -> t.infrastructure().listGroups(), "name-24"),
                        recorded("list_entities", post(urlEqualTo("/v1/entities/list")), "entities",
                                t -> t.infrastructure().listEntities(null), "name-26"),
                        recorded("list_credentials", get(urlEqualTo("/v1/credentials")), "credentials",
                                t -> t.infrastructure().listCredentials(), "name-27"),
                        recorded("list_token_instances", get(urlEqualTo("/v1/tokens")), "tokens",
                                t -> t.infrastructure().listTokenInstances(), "name-29"),
                        recorded("list_discoveries", post(urlEqualTo("/v1/discoveries/list")), "discoveries",
                                t -> t.infrastructure().listDiscoveries(null), "name-31"),
                        recorded("search_secrets", post(urlEqualTo("/v1/secrets")), "secrets",
                                t -> t.secrets().searchSecrets(null, null, null), "name-33"),
                        recorded("get_secret", get(urlEqualTo("/v1/secrets/" + SECRET_UUID)), "secret",
                                t -> t.secrets().getSecret(SECRET_UUID), "Secret: name-39"),
                        recorded("get_secret_versions", get(urlEqualTo("/v1/secrets/" + SECRET_UUID + "/versions")),
                                "secret-versions", t -> t.secrets().getSecretVersions(SECRET_UUID), "Version: 1"),
                        recorded("list_vault_instances", post(urlEqualTo("/v1/vaults/list")), "vaults",
                                t -> t.vaults().listVaultInstances(null, null, null), "name-42"),
                        recorded("list_vault_profiles", post(urlEqualTo("/v1/vaultProfiles/list")), "vault-profiles",
                                t -> t.vaults().listVaultProfiles(null, null, null), "name-46"),
                        recorded("get_searchable_fields", get(urlEqualTo("/v1/certificates/search")),
                                "certificate-search-fields", t -> t.search().getSearchableFields("certificates"),
                                "COMMON_NAME"),
                        recorded("list_triggers", get(urlEqualTo("/v1/workflows/triggers")), "workflow-triggers",
                                t -> t.workflows().listTriggers(null), "name-44"),
                        recorded("list_trigger_fields", get(urlPathEqualTo("/v1/resources/certificates/filters/rules")),
                                "rule-fields-certificates",
                                t -> t.workflows().listTriggerFields("certificate_expiring"),
                                "GROUP_NAME | Groups (LIST)"),
                        recorded("list_custom_attributes", get(urlEqualTo("/v1/attributes/custom")),
                                "custom-attributes", t -> t.attributes().listCustomAttributes(), "name-51"),
                        recorded("list_users", get(urlEqualTo("/v1/users")), "users", t -> t.users().listUsers(),
                                "username-63"),
                        recorded("list_roles", get(urlEqualTo("/v1/roles")), "roles", t -> t.users().listRoles(),
                                "name-74"));
    }

    private static Arguments recorded(String tool, MappingBuilder request, String fixture, Function<Tools, String> call,
            String expected) {
        return arguments(tool, request, fixture, call, expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedResponses")
    void toolReadsRecordedResponse(String tool, MappingBuilder request, String fixture, Function<Tools, String> call,
            String expected) {
        stubFor(request.willReturn(okJson(IlmTestSupport.fixture(fixture))));

        assertThat(call.apply(tools)).doesNotStartWith("Error").contains(expected);
    }
}
