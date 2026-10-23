package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.client.IlmApiClient;
import com.otilm.mcp.tool.ApprovalTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class ApprovalConfigurationTest {
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private static final String USER = "22222222-2222-4222-8222-222222222222";
    private ApprovalService service;

    @BeforeEach
    void setup(WireMockRuntimeInfo wiremock) {
        service = new ApprovalServiceImpl(
                new ConfigurationApiClient(IlmApiClient.restClientBuilder(wiremock.getHttpBaseUrl()).build()));
    }

    @Test
    void createsOrderedRecipientStepsAndReportsActualVersion() {
        stubFor(post(urlEqualTo("/v1/approvalProfiles")).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        stubFor(get(urlEqualTo("/v1/approvalProfiles/" + ID))
                .willReturn(
                        okJson("{\"uuid\":\"" + ID + "\",\"name\":\"Dual control\",\"version\":3,\"expiry\":3600}")));
        assertThat(service.createApprovalProfile("""
                {"name":"Dual control","expiry":3600,"approvalSteps":[
                  {"order":1,"userUuid":"%s","requiredApprovals":1}]}
                """.formatted(USER))).contains(ID, "version", "3");
        verify(postRequestedFor(urlEqualTo("/v1/approvalProfiles"))
                .withRequestBody(matchingJsonPath("$.approvalSteps[0].userUuid", equalTo(USER)))
                .withRequestBody(matchingJsonPath("$.approvalSteps[0].requiredApprovals", equalTo("1"))));
    }

    @Test
    void rejectsAmbiguousRecipientsDuplicateOrderAndInvalidExpiryBeforeMutation() {
        assertThat(service.createApprovalProfile("""
                {"name":"Invalid","approvalSteps":[{"order":1,"userUuid":"%s","roleUuid":"%s","requiredApprovals":1}]}
                """.formatted(USER, ID))).contains("exactly one");
        assertThat(service.createApprovalProfile("""
                {"name":"Invalid","expiry":0,"approvalSteps":[{"order":1,"userUuid":"%s","requiredApprovals":1}]}
                """.formatted(USER))).contains("expiry");
        assertThat(service.createApprovalProfile("""
                {"name":"Invalid","approvalSteps":[{"order":1,"userUuid":"%s","requiredApprovals":1},
                {"order":1,"userUuid":"%s","requiredApprovals":1}]}
                """.formatted(USER, ID))).contains("order");
        verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void updatePreservesOmittedStepsAndReportsNewVersion() {
        stubFor(get(urlEqualTo("/v1/approvalProfiles/" + ID)).willReturn(okJson("""
                {"uuid":"%s","description":"Original","expiry":3600,"version":4,
                 "approvalSteps":[{"uuid":"%s","order":1,"userUuid":"%s","username":"Reviewer","requiredApprovals":1}]}
                """.formatted(ID, ID, USER))));
        stubFor(put(urlEqualTo("/v1/approvalProfiles/" + ID)).willReturn(okJson("{\"uuid\":\"" + ID + "\"}")));
        assertThat(service.updateApprovalProfile(ID, "{\"description\":\"Revised\"}")).contains(ID, "version", "4");
        verify(putRequestedFor(urlEqualTo("/v1/approvalProfiles/" + ID))
                .withRequestBody(equalToJson(
                        """
                                {"description":"Revised","expiry":3600,"approvalSteps":[{"order":1,"userUuid":"%s","requiredApprovals":1}]}
                                """
                                .formatted(USER))));
    }

    @Test
    void decisionsUseOnlyRecipientEndpointAndDescribePendingUnderlyingOperation() {
        stubFor(patch(urlEqualTo("/v1/approvals/" + ID + "/approveRecipient")).willReturn(noContent()));
        stubFor(get(urlEqualTo("/v1/approvals/" + ID)).willReturn(okJson("""
                {"approvalUuid":"%s","status":"PENDING","resource":"certificate","resourceAction":"revoke",
                "objectUuid":"%s","objectData":{"privateKey":"SECRET_SENTINEL"}}
                """.formatted(ID, USER))));
        assertThat(service.decideApproval(ID, true, "Reviewed"))
                .contains("PENDING", "revoke", USER, "underlying")
                .doesNotContain("SECRET_SENTINEL");
        verify(patchRequestedFor(urlEqualTo("/v1/approvals/" + ID + "/approveRecipient"))
                .withRequestBody(equalToJson("{\"comment\":\"Reviewed\"}")));
        verify(0, patchRequestedFor(urlEqualTo("/v1/approvals/" + ID + "/approve")));
    }

    @Test
    void recipientDecisionsAreClassifiedFullOnly() throws Exception {
        McpTool tool = ApprovalTool.class
                .getMethod("decideApproval", String.class, boolean.class, String.class)
                .getAnnotation(McpTool.class);
        assertThat(tool.annotations().readOnlyHint()).isFalse();
        assertThat(tool.annotations().destructiveHint()).isTrue();
    }

    @Test
    void associationUsesFixedScopedRoute() {
        stubFor(patch(urlEqualTo("/v1/approvalProfiles/" + ID + "/associations/raProfiles/" + USER))
                .willReturn(noContent()));
        assertThat(service.setApprovalProfileAssociation(ID, "raProfiles", USER, true)).contains(ID, USER);
        verify(patchRequestedFor(urlEqualTo("/v1/approvalProfiles/" + ID + "/associations/raProfiles/" + USER)));
        assertThat(service.setApprovalProfileAssociation(ID, "../users", USER, true)).contains("resource");
    }

    @Test
    void readsProfileVersionsApprovalsAndScopedAssociations() {
        stubFor(get(urlEqualTo("/v1/approvalProfiles?pageNumber=1&itemsPerPage=20"))
                .willReturn(okJson("{\"approvalProfiles\":[{\"name\":\"Review\",\"version\":2}]}")));
        stubFor(get(urlEqualTo("/v1/approvalProfiles/" + ID + "?version=2"))
                .willReturn(okJson("{\"name\":\"Review\",\"version\":2,\"approvalSteps\":[]}")));
        stubFor(get(urlEqualTo("/v1/approvals/user?pageNumber=1&itemsPerPage=20&history=false"))
                .willReturn(okJson("{\"approvals\":[{\"approvalUuid\":\"" + ID + "\",\"status\":\"PENDING\"}]}")));
        stubFor(get(urlEqualTo("/v1/approvalProfiles/" + ID + "/associations")).willReturn(okJson("[]")));
        stubFor(get(urlEqualTo("/v1/approvalProfiles/associations/raProfiles/" + ID)).willReturn(okJson("[]")));
        assertThat(service.listApprovalProfiles(1, 20)).contains("Review");
        assertThat(service.getApprovalProfile(ID, 2)).contains("Review", "2");
        assertThat(service.listApprovals(1, 20, true, false)).contains(ID, "PENDING");
        assertThat(service.getApprovalProfileAssociations(ID)).contains("Associations");
        assertThat(service.getAssociatedApprovalProfiles("raProfiles", ID)).contains("Approval profiles");
    }

    @Test
    void rejectionDoesNotClaimExecutionAndPreservesAcceptedDecisionOnReadFailure() {
        stubFor(patch(urlEqualTo("/v1/approvals/" + ID + "/rejectRecipient")).willReturn(noContent()));
        stubFor(get(urlEqualTo("/v1/approvals/" + ID)).willReturn(serverError()));
        assertThat(service.decideApproval(ID, false, null)).contains("recorded", ID, "could not");
        verify(patchRequestedFor(urlEqualTo("/v1/approvals/" + ID + "/rejectRecipient")));
    }

    @Test
    void unknownStepFieldsAreRefusedBeforeCreation() {
        assertThat(service.createApprovalProfile("""
                {"name":"Invalid","approvalSteps":[{"order":1,"userUuid":"%s","requiredApprovals":1,"override":true}]}
                """.formatted(USER))).contains("Unknown");
        verify(0, postRequestedFor(anyUrl()));
    }

}
