package com.otilm.mcp.service;

public interface ComplianceService {
    String listComplianceProfiles();

    String getComplianceProfile(String uuid);

    String createComplianceProfile(String configuration);

    String updateComplianceProfile(String uuid, String configuration);

    String deleteComplianceProfile(String uuid);

    String getComplianceRules(String connectorUuid, String kind, String resource, String type, String format);

    String getComplianceRuleAttributes(String connectorUuid, String kind, String ruleUuid, String values);

    String getComplianceGroups(String connectorUuid, String kind, String resource);

    String getComplianceGroupRules(String connectorUuid, String kind, String groupUuid);

    String getComplianceResources();

    String getComplianceCatalog(String connectorUuid, String kind);

    String getComplianceConditionFields(String resource);

    String getComplianceInternalRule(String uuid);

    String createComplianceInternalRule(String configuration);

    String updateComplianceInternalRule(String uuid, String configuration);

    String deleteComplianceInternalRule(String uuid);

    String addComplianceRule(String uuid, String ruleUuid, String connectorUuid, String kind, String attributes);

    String removeComplianceRule(String uuid, String ruleUuid, String connectorUuid, String kind);

    String addComplianceGroup(String uuid, String connectorUuid, String kind, String groupUuid);

    String removeComplianceGroup(String uuid, String connectorUuid, String kind, String groupUuid);

    String getComplianceProfileAssociations(String uuid);

    String getResourceComplianceProfiles(String resource, String objectUuid);

    String associateComplianceProfile(String uuid, String resource, String objectUuid);

    String detachComplianceProfile(String uuid, String resource, String objectUuid);

    String checkCompliance(String resource, String objectUuid);

    String checkComplianceProfiles(String profileUuids, String resource, String type);

    String getComplianceResult(String resource, String objectUuid);
}
