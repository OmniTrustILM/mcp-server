package com.otilm.mcp.tool;

import com.otilm.mcp.service.ApprovalService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ApprovalTool {
    private final ApprovalService service;

    public ApprovalTool(ApprovalService service) {
        this.service = service;
    }

    @McpTool(name = "list_approval_profiles",
            description = "List approval profiles with their latest version, expiry in hours and step counts.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listApprovalProfiles(@McpToolParam(description = "Page number, starting at 1") int page,
            @McpToolParam(description = "Items per page, between 1 and 1000") int size) {
        return service.listApprovalProfiles(page, size);
    }

    @McpTool(name = "get_approval_profile",
            description = "Inspect an approval profile and ordered recipient steps. A version selects a historical profile version.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getApprovalProfile(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "Optional positive profile version; omitted means latest",
                    required = false) Integer version) {
        return service.getApprovalProfile(uuid, version);
    }

    @McpTool(name = "create_approval_profile",
            description = "Create an approval profile with validated ordered recipient steps, required approval counts and optional expiry. Reports the actual profile version.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createApprovalProfile(@McpToolParam(
            description = "JSON object with name, optional description/enabled/expiry (hours), approvalSteps array. Each step has unique positive order, exactly one userUuid/roleUuid/groupUuid, positive requiredApprovals, and optional description.") String configuration) {
        return service.createApprovalProfile(configuration);
    }

    @McpTool(name = "update_approval_profile",
            description = "Update approval description, expiry in hours or the complete ordered steps. Omitted fields are retained. ILM may create a new version; the result reads the actual version.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateApprovalProfile(@McpToolParam(description = "Canonical object UUID") String uuid, @McpToolParam(
            description = "JSON object with optional description, expiry (positive hours or null), and complete approvalSteps array. Each step selects exactly one userUuid/roleUuid/groupUuid and has order and requiredApprovals.") String configuration) {
        return service.updateApprovalProfile(uuid, configuration);
    }

    @McpTool(name = "delete_approval_profile",
            description = "Delete an approval profile subject to ILM association and dependency checks.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteApprovalProfile(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.deleteApprovalProfile(uuid);
    }

    @McpTool(name = "get_approval_profile_associations",
            description = "List resource objects associated with an approval profile.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getApprovalProfileAssociations(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getApprovalProfileAssociations(uuid);
    }

    @McpTool(name = "get_associated_approval_profiles",
            description = "List approval profiles associated with one RA or vault profile.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAssociatedApprovalProfiles(
            @McpToolParam(description = "Association resource: raProfiles or vaultProfiles") String resource,
            @McpToolParam(description = "Target object UUID") String objectUuid) {
        return service.getAssociatedApprovalProfiles(resource, objectUuid);
    }

    @McpTool(name = "set_approval_profile_association",
            description = "Associate or disassociate one approval profile with one RA or vault profile. Existing incompatible associations are refused by ILM.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String setApprovalProfileAssociation(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "Association resource: raProfiles or vaultProfiles") String resource,
            @McpToolParam(description = "Target object UUID") String objectUuid,
            @McpToolParam(description = "True to associate; false to disassociate") boolean associate) {
        return service.setApprovalProfileAssociation(uuid, resource, objectUuid, associate);
    }

    @McpTool(name = "list_approvals",
            description = "List approval requests and pending/closed states. currentUser=true lists the authenticated recipient requests; history includes that user completed requests.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listApprovals(@McpToolParam(description = "Page number, starting at 1") int page,
            @McpToolParam(description = "Items per page, between 1 and 1000") int size,
            @McpToolParam(description = "True for the authenticated user recipient requests") boolean currentUser,
            @McpToolParam(
                    description = "Include authenticated user history; requires currentUser=true") boolean history) {
        return service.listApprovals(page, size, currentUser, history);
    }

    @McpTool(name = "get_approval",
            description = "Read an approval request status, operation target and recipient steps without protected operation content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getApproval(@McpToolParam(description = "Canonical object UUID") String uuid) {
        return service.getApproval(uuid);
    }

    @McpTool(name = "decide_approval",
            description = "At full access, approve or reject as the authenticated eligible recipient. Uses the normal recipient route; platform eligibility remains authoritative. Approval may execute a destructive underlying operation. Reports actual approval state and warns that resource completion must be checked.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String decideApproval(@McpToolParam(description = "Canonical object UUID") String uuid,
            @McpToolParam(description = "True to approve; false to reject") boolean approve,
            @McpToolParam(description = "Optional recipient decision comment", required = false) String comment) {
        return service.decideApproval(uuid, approve, comment);
    }
}
