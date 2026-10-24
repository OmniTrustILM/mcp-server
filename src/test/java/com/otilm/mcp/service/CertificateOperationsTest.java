package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.CertificateOperationsTool;
import com.otilm.mcp.tool.SensitiveOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class CertificateOperationsTest {
    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String R = "22222222-2222-4222-8222-222222222222";
    private static final String C = "33333333-3333-4333-8333-333333333333";
    private static final String BASE = "/v2/operations/authorities/" + A + "/raProfiles/" + R;
    private CertificateOperationsTool tool;

    @BeforeEach
    void setup(WireMockRuntimeInfo wm) {
        tool = new CertificateOperationsTool(new CertificateOperationsServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wm.getHttpBaseUrl()).build()),
                new PlatformVersionCheck(IlmTestSupport.client(wm))));
        IlmTestSupport.stubIlmVersion("2.20.0");
        stubFor(get(urlPathMatching("/v1/attributes/custom/resources/.*")).willReturn(okJson("[]")));
        stubFor(get(urlPathMatching("/v2/operations/authorities/.*/raProfiles/.*/attributes/.*"))
                .willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/authorities/" + A + "/attributes/raProfile")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/certificates/" + C))
                .willReturn(
                        okJson("{\"uuid\":\"" + C + "\",\"state\":\"Pending approval\",\"privateKey\":\"SENTINEL\"}")));
    }

    @Test
    void newIssueReportsAcceptedIdentifierAndActualPendingStateWithoutMaterial() {
        stubFor(post(urlEqualTo(BASE + "/certificates"))
                .willReturn(okJson("{\"uuid\":\"" + C + "\",\"certificateData\":\"SENTINEL\"}")));
        assertThat(tool.issueCertificate(A, R, "{\"request\":\"synthetic-csr\",\"format\":\"pkcs10\"}"))
                .contains(C, "accepted", "Pending approval")
                .doesNotContain("SENTINEL", "Issued successfully");
        verify(postRequestedFor(urlEqualTo(BASE + "/certificates"))
                .withRequestBody(matchingJsonPath("$.request", equalTo("synthetic-csr")))
                .withRequestBody(matchingJsonPath("$.attributes", equalToJson("[]"))));
    }

    @Test
    void registrationSecretAndExistingIssueBodyAreActuallySerialized() {
        stubFor(post(urlEqualTo(BASE + "/certificates/register")).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo(BASE + "/certificates/" + C + "/issue"))
                .willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        assertThat(tool
                .registerCertificate(A, R,
                        "{\"subjectDn\":\"CN=synthetic.test\",\"authorizationSecret\":\"synthetic-registration-secret\"}"))
                .contains(C)
                .doesNotContain("synthetic-registration-secret");
        verify(postRequestedFor(urlEqualTo(BASE + "/certificates/register"))
                .withRequestBody(matchingJsonPath("$.authorizationSecret", equalTo("synthetic-registration-secret"))));
        assertThat(tool
                .issueExistingCertificate(A, R, C,
                        "{\"request\":\"synthetic-csr\",\"authorizationSecret\":\"synthetic-registration-secret\"}"))
                .contains(C)
                .doesNotContain("synthetic-registration-secret");
        verify(postRequestedFor(urlEqualTo(BASE + "/certificates/" + C + "/issue"))
                .withRequestBody(matchingJsonPath("$.authorizationSecret", equalTo("synthetic-registration-secret"))));
    }

    @Test
    void gatesNewOperationsAndFieldsBeforeAnyMutation() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        assertThat(tool.finalizeCertificate(A, R, C, "{\"certificate\":\"synthetic-cert\"}")).contains("2.18");
        assertThat(tool.registerCertificate(A, R, "{\"subjectDn\":\"CN=test\"}")).contains("2.19");
        assertThat(tool.renewCertificate(A, R, C, "{\"attributes\":{}}")).contains("2.20");
        assertThat(tool.importCertificates("{\"file\":\"aGVsbG8=\"}")).contains("2.20");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void keystorePassphraseIsBodyOnlyAndBinaryResultIsExplicitSensitiveOutput() throws Exception {
        stubFor(post(urlEqualTo("/v1/certificates/" + C + "/keystore"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-pkcs12")
                        .withHeader("Content-Disposition", "attachment; filename=certificate.p12")
                        .withBody(new byte[]{1, 2, 3})));
        assertThat(tool.exportCertificateKeystore(C, "{\"passphrase\":\"synthetic-passphrase-123\"}"))
                .contains("certificate.p12", "application/x-pkcs12", "AQID")
                .doesNotContain("synthetic-passphrase-123");
        verify(postRequestedFor(urlEqualTo("/v1/certificates/" + C + "/keystore"))
                .withRequestBody(matchingJsonPath("$.passphrase", equalTo("synthetic-passphrase-123"))));
        var method = CertificateOperationsTool.class.getMethod("exportCertificateKeystore", String.class, String.class);
        assertThat(method.isAnnotationPresent(SensitiveOutput.class)).isTrue();
        assertThat(method.getAnnotation(McpTool.class).annotations().readOnlyHint()).isTrue();
        assertThat(tool.exportCertificateKeystore(C, "{\"passphrase\":\"short\"}")).contains("12");
        verify(1, postRequestedFor(urlEqualTo("/v1/certificates/" + C + "/keystore")));
    }

    @Test
    void singleMetadataFieldsAndRelationsUseTargetedCalls() {
        stubFor(patch(urlEqualTo("/v1/certificates/" + C)).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/certificates/" + C + "/relations/" + R)).willReturn(noContent()));
        assertThat(tool.updateCertificateMetadata(C, "{\"ownerUuid\":\"\"}")).contains(C);
        assertThat(tool.setCertificateRelation(C, R, true)).contains(C, R);
        verify(patchRequestedFor(urlEqualTo("/v1/certificates/" + C))
                .withRequestBody(equalToJson("{\"ownerUuid\":\"\"}")));
        assertThat(tool.updateCertificateMetadata(C, "{\"ownerUuid\":\"\",\"groupUuids\":[]}")).contains("one");
        verify(1, patchRequestedFor(urlEqualTo("/v1/certificates/" + C)));
    }

    @Test
    void baselineKeyBasedIssueUsesUnscopedCsrSchemaOn217() {
        IlmTestSupport.stubIlmVersion("2.17.0");
        stubFor(get(urlEqualTo("/v1/certificates/csr/attributes")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/keys/" + C)).willReturn(okJson("""
                {"tokenInstanceUuid":"%s","items":[{"uuid":"%s","type":"Private"}]}
                """.formatted(A, R))));
        stubFor(get(urlEqualTo("/v1/operations/tokens/" + A + "/tokenProfiles/" + R + "/keys/" + C + "/items/" + R
                + "/sign/attributes")).willReturn(okJson("[]")));
        stubFor(post(urlEqualTo(BASE + "/certificates")).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        assertThat(tool.issueCertificate(A, R, "{\"tokenProfileUuid\":\"" + R + "\",\"keyUuid\":\"" + C + "\"}"))
                .contains(C, "accepted");
        verify(getRequestedFor(urlEqualTo("/v1/certificates/csr/attributes")));
        verify(0, getRequestedFor(urlPathEqualTo("/v1/certificates/csr/attributes"))
                .withQueryParam("raProfileUuid", matching(".*")));
    }

    @Test
    void lifecycleActionsUseTheirReleasedRoutesAndReportCurrentState() {
        for (String route : new String[]{
                "renew",
                "rekey",
                "issue/finalize",
                "revoke",
                "revoke/confirm",
                "cancel",
                "issue"}) {
            stubFor(post(urlEqualTo(BASE + "/certificates/" + C + "/" + route))
                    .willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        }
        assertThat(tool.renewCertificate(A, R, C, "{\"replaceInLocations\":true}")).contains("accepted", C);
        assertThat(tool.rekeyCertificate(A, R, C, "{\"request\":\"synthetic-csr\"}")).contains("accepted", C);
        assertThat(tool.finalizeCertificate(A, R, C, "{\"certificate\":\"synthetic-cert\"}")).contains("accepted", C);
        assertThat(tool.revokeCertificate(A, R, C, "{\"reason\":\"keyCompromise\",\"destroyKey\":true}"))
                .contains("accepted", C);
        assertThat(tool.confirmCertificateRevocation(A, R, C)).contains("accepted", C);
        assertThat(tool.cancelCertificateOperation(A, R, C, "Operator cancelled")).contains("accepted", C);
        assertThat(tool.issueExistingCertificate(A, R, C, null)).contains("accepted", C);
        verify(postRequestedFor(urlEqualTo(BASE + "/certificates/" + C + "/revoke"))
                .withRequestBody(equalToJson("{\"reason\":\"keyCompromise\",\"destroyKey\":true,\"attributes\":[]}")));
        verify(postRequestedFor(urlEqualTo(BASE + "/certificates/" + C + "/issue/finalize"))
                .withRequestBody(matchingJsonPath("$.certificate", equalTo("synthetic-cert"))));
    }

    @Test
    void publicExportsUploadsRequestImportAndInventoryLifecycleWorkWithoutLeakingMaterial() {
        stubFor(post(urlEqualTo("/v1/certificates/create")).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/certificates/upload")).willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        stubFor(post(urlEqualTo("/v1/certificates/upload/async"))
                .willReturn(okJson("{\"fingerprint\":\"synthetic-fingerprint\"}")));
        stubFor(post(urlEqualTo("/v1/certificates/import"))
                .willReturn(okJson("{\"results\":[{\"certificateUuid\":\"" + C + "\",\"imported\":true}]}")));
        stubFor(get(urlEqualTo("/v1/certificates/" + C + "/raw?encoding=pem"))
                .willReturn(okJson("{\"format\":\"raw\",\"encoding\":\"pem\",\"content\":\"PUBLIC-CERTIFICATE\"}")));
        stubFor(get(urlEqualTo("/v1/certificates/" + C + "/chain/pkcs7?withEndCertificate=true&encoding=der"))
                .willReturn(okJson("{\"content\":\"PUBLIC-CHAIN\",\"completeChain\":true}")));
        stubFor(get(urlEqualTo("/v1/certificates/" + C + "/relations"))
                .willReturn(okJson(
                        "{\"certificateUuid\":\"" + C + "\",\"successorCertificates\":[{\"uuid\":\"" + R + "\"}]}")));
        stubFor(get(urlEqualTo("/v1/certificates/" + C + "/approvals?pageNumber=1&itemsPerPage=10"))
                .willReturn(okJson("{\"approvals\":[{\"status\":\"PENDING\"}]}")));
        stubFor(patch(urlEqualTo("/v1/certificates/" + C + "/archive")).willReturn(noContent()));
        stubFor(patch(urlEqualTo("/v1/certificates/" + C + "/unarchive")).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/certificates/" + C)).willReturn(noContent()));
        stubFor(delete(urlEqualTo("/v1/certificates/" + C + "/relations/" + R)).willReturn(noContent()));
        assertThat(tool.createCertificateRequest("{\"request\":\"synthetic-csr\"}")).contains(C);
        assertThat(tool.uploadCertificate("synthetic-cert", null, false)).contains(C);
        assertThat(tool.uploadCertificate("synthetic-cert", null, true)).contains("synthetic-fingerprint", "pending");
        assertThat(tool
                .importCertificates(
                        "{\"file\":\"aGVsbG8=\",\"passphrase\":\"synthetic-passphrase\",\"entries\":[{\"entryReference\":\"entry-1\"}]}"))
                .contains(C, "true");
        verify(postRequestedFor(urlEqualTo("/v1/certificates/import"))
                .withRequestBody(matchingJsonPath("$.file", equalTo("aGVsbG8=")))
                .withRequestBody(matchingJsonPath("$.passphrase", equalTo("synthetic-passphrase"))));
        assertThat(tool.exportCertificate(C, "raw", "pem", false, false)).contains("PUBLIC-CERTIFICATE");
        assertThat(tool.exportCertificate(C, "pkcs7", "der", true, true)).contains("PUBLIC-CHAIN");
        assertThat(tool.getCertificateRelations(C)).contains(C, R);
        assertThat(tool.getCertificateApprovals(C, 1, 10)).contains("PENDING");
        assertThat(tool.archiveCertificate(C)).contains(C, "true");
        assertThat(tool.unarchiveCertificate(C)).contains(C, "false");
        assertThat(tool.setCertificateRelation(C, R, false)).contains(C, R);
        assertThat(tool.deleteCertificate(C)).contains(C, "Deleted");
    }

    @Test
    void capabilitiesReturnTheActualOperationSupportList() {
        stubFor(get(urlEqualTo(BASE + "/availableOperations")).willReturn(okJson("""
                {"operations":[{"operation":"issue","supported":true,"asyncSupported":true,"cancelSupported":false}]}
                """)));
        assertThat(tool.getCertificateOperations(A, R)).contains("issue", "asyncSupported", "true", "cancelSupported");
    }

    @Test
    void providerCallbacksUseCertificateIssuanceScopeForInterfaceAuthorities() {
        stubFor(get(urlEqualTo(BASE + "/attributes/issue")).willReturn(okJson("""
                [{"name":"options","version":3,"type":"data","contentType":"string","properties":{"list":true},
                "attributeCallback":{"dependsOn":[]}}]
                """)));
        stubFor(get(urlEqualTo("/v1/authorities/" + A))
                .willReturn(okJson("{\"connectorInterface\":{\"uuid\":\"" + A + "\"}}")));
        stubFor(post(urlEqualTo("/v1/certificates/" + R + "/callback"))
                .willReturn(okJson("[{\"contentType\":\"string\",\"data\":\"RSA\"}]")));
        assertThat(tool.getCertificateOperationAttributes(A, R, "issue", "{}")).contains("RSA");
        verify(postRequestedFor(urlEqualTo("/v1/certificates/" + R + "/callback")));
        verify(0, postRequestedFor(urlEqualTo("/v1/raProfiles/" + A + "/callback")));
    }

    @Test
    void keyBasedRekeyUsesSourceIdentityWithoutSendingUnsupportedCsrAttributes() {
        stubFor(get(urlEqualTo("/v1/keys/" + C))
                .willReturn(okJson("{\"tokenInstanceUuid\":\"" + A + "\",\"items\":[{\"uuid\":\"" + R
                        + "\",\"type\":\"Private\"}]}")));
        stubFor(get(urlEqualTo("/v1/operations/tokens/" + A + "/tokenProfiles/" + R + "/keys/" + C + "/items/" + R
                + "/sign/attributes")).willReturn(okJson("[]")));
        stubFor(post(urlEqualTo(BASE + "/certificates/" + C + "/rekey"))
                .willReturn(okJson("{\"uuid\":\"" + C + "\"}")));
        assertThat(tool.rekeyCertificate(A, R, C, "{\"keyUuid\":\"" + C + "\",\"tokenProfileUuid\":\"" + R + "\"}"))
                .contains("accepted", C);
        verify(postRequestedFor(urlEqualTo(BASE + "/certificates/" + C + "/rekey"))
                .withRequestBody(matchingJsonPath("$.signatureAttributes", equalToJson("[]")))
                .withRequestBody(notMatching(".*csrAttributes.*")));
    }

    @Test
    void registrationSourceAssociationRequires220BeforeWrite() {
        IlmTestSupport.stubIlmVersion("2.19.0");
        assertThat(
                tool.registerCertificate(A, R, "{\"subjectDn\":\"CN=test\",\"sourceCertificateUuid\":\"" + C + "\"}"))
                .contains("2.20");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void containerKeyDestinationsUseReleasedKeyCustomResourceAndImportRequestTypes() {
        stubFor(get(urlEqualTo("/v1/tokenProfiles/" + R)).willReturn(okJson("{\"tokenInstanceUuid\":\"" + A + "\"}")));
        for (String type : new String[]{"keyPair", "secret"}) {
            stubFor(get(urlEqualTo("/v1/tokens/" + A + "/tokenProfiles/" + R + "/keys/" + type + "/import/attributes"))
                    .willReturn(okJson("[]")));
        }
        stubFor(post(urlEqualTo("/v1/certificates/import"))
                .willReturn(okJson("{\"results\":[{\"certificateUuid\":\"" + C + "\",\"keyUuid\":\"" + R + "\"}]}")));
        for (String type : new String[]{"", ",\"keyType\":\"keyPair\"", ",\"keyType\":\"secret\""}) {
            assertThat(tool
                    .importCertificates(
                            "{\"file\":\"aGVsbG8=\",\"entries\":[{\"entryReference\":\"key-entry\",\"keyDestination\":{\"tokenProfileUuid\":\""
                                    + R + "\"" + type + "}}]}"))
                    .contains(C, R);
        }
        verify(2, getRequestedFor(
                urlEqualTo("/v1/tokens/" + A + "/tokenProfiles/" + R + "/keys/keyPair/import/attributes")));
        verify(getRequestedFor(
                urlEqualTo("/v1/tokens/" + A + "/tokenProfiles/" + R + "/keys/secret/import/attributes")));
        verify(3, getRequestedFor(urlEqualTo("/v1/attributes/custom/resources/keys")));
        verify(3,
                postRequestedFor(urlEqualTo("/v1/certificates/import"))
                        .withRequestBody(
                                matchingJsonPath("$.entries[0].keyDestination.importAttributes", equalToJson("[]")))
                        .withRequestBody(notMatching(".*keyType.*")));
    }

    @Test
    void containerKeyDestinationsRejectItemTypesBeforeProviderRequestsOrWrites() {
        for (String type : new String[]{"Private", "Public", "Secret", "Split"}) {
            assertThat(tool
                    .importCertificates(
                            "{\"file\":\"aGVsbG8=\",\"entries\":[{\"entryReference\":\"key-entry\",\"keyDestination\":{\"tokenProfileUuid\":\""
                                    + R + "\",\"keyType\":\"" + type + "\"}}]}"))
                    .contains("keyType", "keyPair", "secret");
        }
        verify(0, getRequestedFor(urlPathMatching("/v1/tokenProfiles/.*")));
        verify(0, getRequestedFor(urlPathMatching("/v1/tokens/.*")));
        verify(0, postRequestedFor(anyUrl()));
    }

}
