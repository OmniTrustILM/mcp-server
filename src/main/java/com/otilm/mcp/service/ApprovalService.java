package com.otilm.mcp.service;

public interface ApprovalService {
    String listApprovalProfiles(int page, int size);

    String getApprovalProfile(String uuid, Integer version);

    String createApprovalProfile(String configuration);

    String updateApprovalProfile(String uuid, String configuration);

    String deleteApprovalProfile(String uuid);

    String getApprovalProfileAssociations(String uuid);

    String getAssociatedApprovalProfiles(String resource, String objectUuid);

    String setApprovalProfileAssociation(String uuid, String resource, String objectUuid, boolean associate);

    String listApprovals(int page, int size, boolean currentUser, boolean history);

    String getApproval(String uuid);

    String decideApproval(String uuid, boolean approve, String comment);
}
