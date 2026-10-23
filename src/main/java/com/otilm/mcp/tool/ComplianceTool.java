package com.otilm.mcp.tool;

import com.otilm.mcp.service.ComplianceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ComplianceTool {
    private final ComplianceService service;

    public ComplianceTool(ComplianceService service) {
        this.service = service;
    }

    @McpTool(name = "list_compliance_profiles",
            description = "List compliance profile metadata and composition counts. Baseline ILM 2.17 v2 API.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listComplianceProfiles() {
        return service.listComplianceProfiles();
    }

    @McpTool(name = "get_compliance_profile",
            description = "Read profile metadata, internal rules and provider groups/rules without protected attribute content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceProfile(@McpToolParam(
            description = "Compliance profile or internal rule UUID as named by this operation.") String uuid) {
        return service.getComplianceProfile(uuid);
    }

    @McpTool(name = "create_compliance_profile",
            description = "Create a compliance profile. Configuration: name required; description, internalRules (UUID array), providerRules, customAttributes optional. providerRules entries: connectorUuid, kind, groups (UUID array), rules (uuid plus attributes name-value object). All schemas and choices validated before creation.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createComplianceProfile(@McpToolParam(
            description = "JSON configuration using documented fields. Updates preserve omitted readable state; protected attributes require fresh input.") String configuration) {
        return service.createComplianceProfile(configuration);
    }

    @McpTool(name = "update_compliance_profile",
            description = "Update profile description/custom attributes and complete rule/group composition. Configuration: description, internalRules, providerRules, customAttributes. Omitted composition is preserved, including provider attribute references; explicit arrays replace their list. Protected rules need fresh values. Prefer targeted rule/group tools for one association change.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateComplianceProfile(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(
                    description = "JSON configuration using documented fields. Updates preserve omitted readable state; protected attributes require fresh input.") String configuration) {
        return service.updateComplianceProfile(uuid, configuration);
    }

    @McpTool(name = "delete_compliance_profile",
            description = "Delete compliance profile using normal dependency checks; requires full access. No force fallback.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteComplianceProfile(@McpToolParam(
            description = "Compliance profile or internal rule UUID as named by this operation.") String uuid) {
        return service.deleteComplianceProfile(uuid);
    }

    @McpTool(name = "get_compliance_rules",
            description = "List internal rules or provider rules, optionally filtered by compliance resource/type/format. Provider attribute content omitted; use get_compliance_rule_attributes for schema.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceRules(@McpToolParam(
            description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.",
            required = false) String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.",
                    required = false) String kind,
            @McpToolParam(
                    description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.",
                    required = false) String resource,
            @McpToolParam(description = "Optional provider object type filter, such as X.509; requires resource.",
                    required = false) String type,
            @McpToolParam(description = "Optional provider format filter; requires resource.",
                    required = false) String format) {
        return service.getComplianceRules(connectorUuid, kind, resource, type, format);
    }

    @McpTool(name = "get_compliance_rule_attributes",
            description = "Discover an offered provider rule schema and its callbacks without populated protected content.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceRuleAttributes(@McpToolParam(
            description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.") String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.") String kind,
            @McpToolParam(description = "Internal or provider compliance rule UUID.") String ruleUuid,
            @McpToolParam(
                    description = "Optional JSON object of rule attribute names and values for callback resolution.",
                    required = false) String values) {
        return service.getComplianceRuleAttributes(connectorUuid, kind, ruleUuid, values);
    }

    @McpTool(name = "get_compliance_groups",
            description = "List offered compliance provider groups for a connector kind and optional compliance resource.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceGroups(@McpToolParam(
            description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.") String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.") String kind,
            @McpToolParam(
                    description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.",
                    required = false) String resource) {
        return service.getComplianceGroups(connectorUuid, kind, resource);
    }

    @McpTool(name = "get_compliance_group_rules",
            description = "List the rules offered by one provider compliance group.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceGroupRules(@McpToolParam(
            description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.") String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.") String kind,
            @McpToolParam(description = "Provider compliance group UUID.") String groupUuid) {
        return service.getComplianceGroupRules(connectorUuid, kind, groupUuid);
    }

    @McpTool(name = "get_compliance_resources",
            description = "Discover platform compliance subjects and resources that accept compliance profile associations.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceResources() {
        return service.getComplianceResources();
    }

    @McpTool(name = "get_compliance_catalog",
            description = "Discover resource/type/format combinations from the actual internal/provider rule inventory; no guessed catalog endpoints.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceCatalog(@McpToolParam(
            description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.",
            required = false) String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.",
                    required = false) String kind) {
        return service.getComplianceCatalog(connectorUuid, kind);
    }

    @McpTool(name = "get_compliance_condition_fields",
            description = "Discover fields and supported operators usable in an internal compliance rule for this compliance subject.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceConditionFields(@McpToolParam(
            description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.") String resource) {
        return service.getComplianceConditionFields(resource);
    }

    @McpTool(name = "get_compliance_internal_rule",
            description = "Read internal rule metadata and condition field/operator definitions; condition values omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceInternalRule(@McpToolParam(
            description = "Compliance profile or internal rule UUID as named by this operation.") String uuid) {
        return service.getComplianceInternalRule(uuid);
    }

    @McpTool(name = "create_compliance_internal_rule",
            description = "Create an internal compliance rule. Configuration: name, resource, nonempty conditionItems required; description optional. Each condition has fieldSource, fieldIdentifier, operator and value; fields/operators/types validated against resource schema.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createComplianceInternalRule(@McpToolParam(
            description = "JSON configuration using documented fields. Updates preserve omitted readable state; protected attributes require fresh input.") String configuration) {
        return service.createComplianceInternalRule(configuration);
    }

    @McpTool(name = "update_compliance_internal_rule",
            description = "Update internal compliance rule with omitted name/resource/conditionItems retained. Configuration matches create_compliance_internal_rule. Resource field/operator choices are validated before PUT.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateComplianceInternalRule(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(
                    description = "JSON configuration using documented fields. Updates preserve omitted readable state; protected attributes require fresh input.") String configuration) {
        return service.updateComplianceInternalRule(uuid, configuration);
    }

    @McpTool(name = "delete_compliance_internal_rule",
            description = "Delete internal compliance rule with authoritative dependency checks; requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteComplianceInternalRule(@McpToolParam(
            description = "Compliance profile or internal rule UUID as named by this operation.") String uuid) {
        return service.deleteComplianceInternalRule(uuid);
    }

    @McpTool(name = "add_compliance_rule",
            description = "Add or update exactly one internal/provider rule association using PATCH. Provider attributes are schema-driven; unrelated rules and groups retained.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String addComplianceRule(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(description = "Internal or provider compliance rule UUID.") String ruleUuid,
            @McpToolParam(
                    description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.",
                    required = false) String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.",
                    required = false) String kind,
            @McpToolParam(description = "Optional JSON object of provider rule attribute names and values.",
                    required = false) String attributes) {
        return service.addComplianceRule(uuid, ruleUuid, connectorUuid, kind, attributes);
    }

    @McpTool(name = "remove_compliance_rule",
            description = "Remove exactly one internal/provider rule association using PATCH, retaining unrelated composition.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String removeComplianceRule(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(description = "Internal or provider compliance rule UUID.") String ruleUuid,
            @McpToolParam(
                    description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.",
                    required = false) String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.",
                    required = false) String kind) {
        return service.removeComplianceRule(uuid, ruleUuid, connectorUuid, kind);
    }

    @McpTool(name = "add_compliance_group",
            description = "Add exactly one offered provider group to the compliance profile using PATCH; other associations retained.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String addComplianceGroup(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(
                    description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.") String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.") String kind,
            @McpToolParam(description = "Provider compliance group UUID.") String groupUuid) {
        return service.addComplianceGroup(uuid, connectorUuid, kind, groupUuid);
    }

    @McpTool(name = "remove_compliance_group",
            description = "Remove exactly one provider group association using PATCH; other associations retained.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String removeComplianceGroup(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(
                    description = "Optional compliance provider connector UUID. Supply together with kind; omit both for internal rules.") String connectorUuid,
            @McpToolParam(description = "Compliance provider kind; required together with connectorUuid.") String kind,
            @McpToolParam(description = "Provider compliance group UUID.") String groupUuid) {
        return service.removeComplianceGroup(uuid, connectorUuid, kind, groupUuid);
    }

    @McpTool(name = "get_compliance_profile_associations",
            description = "List resources and objects associated with a compliance profile.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceProfileAssociations(@McpToolParam(
            description = "Compliance profile or internal rule UUID as named by this operation.") String uuid) {
        return service.getComplianceProfileAssociations(uuid);
    }

    @McpTool(name = "get_resource_compliance_profiles",
            description = "List compliance profiles associated with a resource object.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getResourceComplianceProfiles(@McpToolParam(
            description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.") String resource,
            @McpToolParam(description = "Resource object UUID.") String objectUuid) {
        return service.getResourceComplianceProfiles(resource, objectUuid);
    }

    @McpTool(name = "associate_compliance_profile",
            description = "Associate one compliance profile with an assignable RA/token/vault profile without replacing unrelated associations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String associateComplianceProfile(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(
                    description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.") String resource,
            @McpToolParam(description = "Resource object UUID.") String objectUuid) {
        return service.associateComplianceProfile(uuid, resource, objectUuid);
    }

    @McpTool(name = "detach_compliance_profile",
            description = "Detach one compliance profile from a resource object without replacing unrelated associations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String detachComplianceProfile(
            @McpToolParam(
                    description = "Compliance profile or internal rule UUID as named by this operation.") String uuid,
            @McpToolParam(
                    description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.") String resource,
            @McpToolParam(description = "Resource object UUID.") String objectUuid) {
        return service.detachComplianceProfile(uuid, resource, objectUuid);
    }

    @McpTool(name = "check_compliance",
            description = "Request compliance checking for one object or associated profile. Reports acceptance; use get_compliance_result for latest completed object results.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String checkCompliance(@McpToolParam(
            description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.") String resource,
            @McpToolParam(description = "Resource object UUID.") String objectUuid) {
        return service.checkCompliance(resource, objectUuid);
    }

    @McpTool(name = "check_compliance_profiles",
            description = "Request checks for explicit compliance profile UUIDs, optionally limited by resource and type. Processing can continue asynchronously.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String checkComplianceProfiles(
            @McpToolParam(
                    description = "JSON array of explicit compliance profile UUIDs to check.") String profileUuids,
            @McpToolParam(
                    description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.",
                    required = false) String resource,
            @McpToolParam(description = "Optional provider object type filter, such as X.509; requires resource.",
                    required = false) String type) {
        return service.checkComplianceProfiles(profileUuids, resource, type);
    }

    @McpTool(name = "get_compliance_result",
            description = "Read the latest completed object compliance status/time and failed-rule metadata. Provider messages and protected attributes omitted. Use keyItems for individual key item results.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getComplianceResult(@McpToolParam(
            description = "ILM resource code; use get_compliance_resources to discover compliance subjects and assignable resources.") String resource,
            @McpToolParam(description = "Resource object UUID.") String objectUuid) {
        return service.getComplianceResult(resource, objectUuid);
    }

}
