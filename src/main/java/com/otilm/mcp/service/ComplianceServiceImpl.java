package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ComplianceServiceImpl implements ComplianceService {
    private static final String RULES_ENDPOINT = "/rules";
    private static final String INTERNAL_RULE = "Internal rule";
    private static final String PROVIDER_GROUP = "Provider group";
    private static final String PROVIDER_KIND = "Provider kind";
    private static final String PROVIDER_RULE = "Provider rule";
    private static final String ATTRIBUTES = "attributes";
    private static final String COMPLIANCE_PROVIDER_V2 = "complianceProviderV2";
    private static final String CONDITION_ITEMS = "conditionItems";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String FORMAT = "format";
    private static final String GROUPS = "groups";
    private static final String INTERNAL_RULES = "internalRules";
    private static final String PROVIDER_RULES = "providerRules";
    private static final String RESOURCE = "resource";
    private static final String RULES = "rules";

    private static final String BASE = "/v2/complianceProfiles";
    private static final String PROFILE = BASE + "/{uuid}";
    private static final String[] PROFILE_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "internalRulesCount",
            "providerRulesCount",
            "providerGroupsCount",
            "associations"};
    private static final String[] RULE_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            CONNECTOR_UUID,
            "connectorName",
            "kind",
            "groupUuid",
            RESOURCE,
            "type",
            FORMAT,
            "availabilityStatus",
            "status"};
    private final ConfigurationApiClient client;

    public ComplianceServiceImpl(ConfigurationApiClient client) {
        this.client = client;
    }

    @Override
    public String listComplianceProfiles() {
        return ConfigurationSupport
                .run("listing compliance profiles",
                        () -> ConfigurationSupport.list("Compliance profiles", client.get(BASE), PROFILE_FIELDS));
    }

    @Override
    public String getComplianceProfile(String uuid) {
        return ConfigurationSupport
                .run("retrieving compliance profile",
                        () -> profileText("Compliance profile", client.get(PROFILE, CryptoInput.uuid(uuid))));
    }

    private String profileText(String title, JsonNode profile) {
        StringBuilder output = new StringBuilder(ConfigurationSupport.describe(title, profile, PROFILE_FIELDS));
        for (JsonNode rule : profile.path(INTERNAL_RULES)) {
            output.append(ConfigurationSupport.describe(INTERNAL_RULE, rule, RULE_FIELDS));
        }
        for (JsonNode provider : profile.path(PROVIDER_RULES)) {
            output.append(ConfigurationSupport.describe("Provider", provider, CONNECTOR_UUID, "connectorName", "kind"));
            for (JsonNode rule : provider.path(RULES)) {
                output.append(ConfigurationSupport.describe(PROVIDER_RULE, rule, RULE_FIELDS));
            }
            for (JsonNode group : provider.path(GROUPS)) {
                output.append(ConfigurationSupport.describe(PROVIDER_GROUP, group, RULE_FIELDS));
            }
        }
        return output.toString();
    }

    @Override
    public String createComplianceProfile(String configuration) {
        return saveProfile(null, configuration);
    }

    @Override
    public String updateComplianceProfile(String uuid, String configuration) {
        return saveProfile(uuid, configuration);
    }

    private String saveProfile(String uuid, String configuration) {
        return ConfigurationSupport.run("saving compliance profile", () -> {
            ObjectNode body = profileBody(configuration, uuid == null);
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(PROFILE, CryptoInput.uuid(uuid));
            CryptoInput.preserve(body, existing, DESCRIPTION);
            body.set(INTERNAL_RULES, internalRules(body, existing));
            boolean supplied = body.has(PROVIDER_RULES);
            JsonNode providers = supplied ? body.path(PROVIDER_RULES) : existing.path(PROVIDER_RULES);
            body.set(PROVIDER_RULES, providers(providers, existing.path(PROVIDER_RULES), supplied));
            body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "complianceProfiles", existing, body));
            return profileText("Compliance profile saved",
                    uuid == null ? client.post(BASE, body) : client.put(PROFILE, body, uuid));
        });
    }

    private static ObjectNode profileBody(String configuration, boolean create) {
        ObjectNode body = create
                ? CryptoInput
                        .configuration(configuration, "name", DESCRIPTION, INTERNAL_RULES, PROVIDER_RULES,
                                CUSTOM_ATTRIBUTES)
                : CryptoInput
                        .configuration(configuration, DESCRIPTION, INTERNAL_RULES, PROVIDER_RULES, CUSTOM_ATTRIBUTES);
        CryptoInput.text(body, "name", DESCRIPTION);
        if (create) {
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Profile name"));
        }
        return body;
    }

    private ArrayNode internalRules(JsonNode body, JsonNode existing) {
        ArrayNode internal = body.has(INTERNAL_RULES)
                ? CryptoInput.strings(body.path(INTERNAL_RULES), true)
                : ids(existing.path(INTERNAL_RULES));
        if (!internal.isEmpty()) {
            JsonNode offered = rules(null, null, null, null, null);
            validateInventory(internal, offered, INTERNAL_RULE);
        }
        return internal;
    }

    private static void validateInventory(ArrayNode selected, JsonNode offered, String label) {
        for (JsonNode entry : selected) {
            find(offered, entry.asString(), label);
        }
    }

    private ArrayNode providers(JsonNode requested, JsonNode existing, boolean supplied) {
        if (!requested.isArray() && !requested.isMissingNode()) {
            throw new IllegalArgumentException("providerRules must be an array of provider compositions.");
        }
        ArrayNode result = ConfigurationAttributes.JSON.createArrayNode();
        Set<String> seen = new HashSet<>();
        for (JsonNode provider : requested) {
            result.add(providerComposition(provider, existing, supplied, seen));
        }
        return result;
    }

    private ObjectNode providerComposition(JsonNode provider, JsonNode existing, boolean supplied, Set<String> seen) {
        if (!provider.isObject()) {
            throw new IllegalArgumentException("Provider compositions must be objects.");
        }
        if (supplied) {
            CryptoInput.configuration(provider.toString(), CONNECTOR_UUID, "kind", RULES, GROUPS);
        }
        String connector = CryptoInput.uuid(provider.path(CONNECTOR_UUID).asString(null));
        String kind = ConfigurationSupport.required(provider.path("kind").asString(null), PROVIDER_KIND);
        if (!seen.add(connector + ":" + kind)) {
            throw new IllegalArgumentException("Duplicate compliance provider composition.");
        }
        JsonNode previous = provider(existing, connector, kind);
        ConfigurationAttributes.ConnectorContext context = context(connector, kind);
        ObjectNode body = ConfigurationAttributes.JSON
                .createObjectNode()
                .put(CONNECTOR_UUID, connector)
                .put("kind", kind);
        ArrayNode groups = providerGroups(provider, previous, supplied, context);
        body.set(GROUPS, groups);
        ArrayNode encoded = providerRules(provider, previous, supplied, context);
        body.set(RULES, encoded);
        if (groups.isEmpty() && encoded.isEmpty()) {
            throw new IllegalArgumentException(
                    "A provider composition requires at least one group or rule; remove the provider block to clear it.");
        }
        return body;
    }

    private ArrayNode providerGroups(JsonNode provider, JsonNode previous, boolean supplied,
            ConfigurationAttributes.ConnectorContext context) {
        ArrayNode selected = supplied && provider.has(GROUPS)
                ? CryptoInput.strings(provider.path(GROUPS), true)
                : ids(previous.path(GROUPS));
        if (!selected.isEmpty()) {
            JsonNode offered = groups(context.connectorUuid(), context.kind(), null);
            validateInventory(selected, offered, PROVIDER_GROUP);
        }
        return selected;
    }

    private ArrayNode providerRules(JsonNode provider, JsonNode previous, boolean supplied,
            ConfigurationAttributes.ConnectorContext context) {
        JsonNode desiredRules = provider.has(RULES) ? provider.path(RULES) : previous.path(RULES);
        if (!desiredRules.isArray() && !desiredRules.isMissingNode()) {
            throw new IllegalArgumentException("Provider rules must be an array.");
        }
        ArrayNode encoded = ConfigurationAttributes.JSON.createArrayNode();
        JsonNode offered = rules(context.connectorUuid(), context.kind(), null, null, null);
        Set<String> seen = new HashSet<>();
        boolean suppliedRules = supplied && provider.has(RULES);
        for (JsonNode rule : desiredRules) {
            if (suppliedRules) {
                CryptoInput.configuration(rule.toString(), "uuid", ATTRIBUTES);
            }
            String ruleUuid = CryptoInput.uuid(rule.path("uuid").asString(null));
            if (!seen.add(ruleUuid)) {
                throw new IllegalArgumentException("Duplicate provider rule.");
            }
            JsonNode definition = find(offered, ruleUuid, PROVIDER_RULE);
            JsonNode old = findOrEmpty(previous.path(RULES), ruleUuid);
            String values = suppliedRules ? CryptoInput.values(rule, ATTRIBUTES) : "{}";
            encoded.add(providerRule(ruleUuid, definition, old, values, context));
        }
        return encoded;
    }

    private ObjectNode providerRule(String uuid, JsonNode definition, JsonNode previous, String values,
            ConfigurationAttributes.ConnectorContext context) {
        JsonNode definitions = ConfigurationAttributes
                .resolveReplacement(client, context, array(definition.path(ATTRIBUTES)),
                        array(previous.path(ATTRIBUTES)), values);
        ObjectNode request = ConfigurationAttributes.JSON.createObjectNode().put("uuid", uuid);
        request.set(ATTRIBUTES, ConfigurationAttributes.replace(definitions, array(previous.path(ATTRIBUTES)), values));
        return request;
    }

    private static JsonNode provider(JsonNode providers, String connector, String kind) {
        for (JsonNode candidate : providers) {
            if (connector.equals(candidate.path(CONNECTOR_UUID).asString())
                    && kind.equals(candidate.path("kind").asString())) {
                return candidate;
            }
        }
        return ConfigurationAttributes.JSON.createObjectNode();
    }

    @Override
    public String deleteComplianceProfile(String uuid) {
        return ConfigurationSupport.run("deleting compliance profile", () -> {
            client.delete(PROFILE, CryptoInput.uuid(uuid));
            return "Deleted compliance profile [" + uuid + "].";
        });
    }

    @Override
    public String getComplianceRules(String connectorUuid, String kind, String resource, String type, String format) {
        return ConfigurationSupport
                .run("retrieving compliance rules", () -> ConfigurationSupport
                        .list("Compliance rules", rules(connectorUuid, kind, resource, type, format), RULE_FIELDS));
    }

    private JsonNode rules(String connectorUuid, String kind, String resource, String type, String format) {
        Map<String, String> parameters = providerParameters(connectorUuid, kind);
        if (resource != null && !resource.isBlank()) {
            parameters.put(RESOURCE, subject(resource).getCode());
        } else if (type != null || format != null) {
            throw new IllegalArgumentException("Compliance type/format filters require a resource.");
        }
        if (type != null) {
            parameters.put("type", ConfigurationSupport.required(type, "Compliance type"));
        }
        if (format != null) {
            parameters.put(FORMAT, ConfigurationSupport.required(format, "Compliance format"));
        }
        return get(BASE + RULES_ENDPOINT, parameters);
    }

    private Map<String, String> providerParameters(String connectorUuid, String kind) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (connectorUuid == null && kind == null) {
            return parameters;
        }
        context(connectorUuid, kind);
        parameters.put(CONNECTOR_UUID, connectorUuid);
        parameters.put("kind", kind);
        return parameters;
    }

    private ConfigurationAttributes.ConnectorContext context(String connectorUuid, String kind) {
        CryptoInput.uuid(connectorUuid);
        ConfigurationSupport.required(kind, "Compliance provider kind");
        JsonNode connector = client.get("/v1/connectors/{uuid}", connectorUuid);
        FunctionGroupCode selected = null;
        for (JsonNode group : connector.path("functionGroups")) {
            String code = group.path("functionGroupCode").asString();
            if (!Set.of("complianceProvider", COMPLIANCE_PROVIDER_V2).contains(code)) {
                continue;
            }
            for (JsonNode offered : group.path("kinds")) {
                if (kind.equals(offered.asString()) && (selected == null || COMPLIANCE_PROVIDER_V2.equals(code))) {
                    selected = COMPLIANCE_PROVIDER_V2.equals(code)
                            ? FunctionGroupCode.COMPLIANCE_PROVIDER_V2
                            : FunctionGroupCode.COMPLIANCE_PROVIDER;
                }
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Connector does not offer the requested compliance kind.");
        }
        return new ConfigurationAttributes.ConnectorContext(connectorUuid, selected, kind);
    }

    @Override
    public String getComplianceRuleAttributes(String connectorUuid, String kind, String ruleUuid, String values) {
        return ConfigurationSupport.run("retrieving compliance rule settings", () -> {
            JsonNode definition = find(rules(connectorUuid, kind, null, null, null), CryptoInput.uuid(ruleUuid),
                    PROVIDER_RULE);
            return ConfigurationAttributes
                    .settings("Compliance rule settings", expand(context(connectorUuid, kind), definition, values));
        });
    }

    private JsonNode expand(ConfigurationAttributes.ConnectorContext context, JsonNode rule, String values) {
        return ConfigurationAttributes.resolve(client, context, array(rule.path(ATTRIBUTES)), values);
    }

    @Override
    public String getComplianceGroups(String connectorUuid, String kind, String resource) {
        return ConfigurationSupport
                .run("retrieving compliance groups", () -> ConfigurationSupport
                        .list("Compliance provider groups", groups(connectorUuid, kind, resource), RULE_FIELDS));
    }

    private JsonNode groups(String connectorUuid, String kind, String resource) {
        context(connectorUuid, kind);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(CONNECTOR_UUID, connectorUuid);
        parameters.put("kind", kind);
        if (resource != null && !resource.isBlank()) {
            parameters.put(RESOURCE, subject(resource).getCode());
        }
        return get(BASE + "/groups", parameters);
    }

    @Override
    public String getComplianceGroupRules(String connectorUuid, String kind, String groupUuid) {
        return ConfigurationSupport.run("retrieving compliance group rules", () -> {
            Map<String, String> parameters = providerParameters(connectorUuid, kind);
            return ConfigurationSupport
                    .list("Compliance group rules",
                            get(BASE + "/groups/" + CryptoInput.uuid(groupUuid) + RULES_ENDPOINT, parameters),
                            RULE_FIELDS);
        });
    }

    @Override
    public String getComplianceResources() {
        return ConfigurationSupport.run("retrieving compliance resources", () -> {
            ArrayNode resources = ConfigurationAttributes.JSON.createArrayNode();
            for (JsonNode resource : client.get("/v1/resources")) {
                if (resource.path("complianceSubject").asBoolean(false)
                        || resource.path("hasComplianceProfiles").asBoolean(false)) {
                    resources.add(resource);
                }
            }
            return ConfigurationSupport
                    .list("Compliance resources", resources, RESOURCE, "complianceSubject", "hasComplianceProfiles");
        });
    }

    @Override
    public String getComplianceCatalog(String connectorUuid, String kind) {
        return ConfigurationSupport
                .run("retrieving compliance type and format catalog",
                        () -> ConfigurationSupport
                                .list("Offered compliance resource/type/format catalog",
                                        rules(connectorUuid, kind, null, null, null), "uuid", "name", RESOURCE, "type",
                                        FORMAT));
    }

    @Override
    public String getComplianceConditionFields(String resource) {
        return ConfigurationSupport.run("retrieving compliance condition fields", () -> {
            StringBuilder output = new StringBuilder("Compliance condition fields:\n");
            for (JsonNode group : conditionFields(resource)) {
                output.append(ConfigurationSupport.describe("Field source", group, "filterFieldSource"));
                for (JsonNode field : group.path("searchFieldData")) {
                    output
                            .append(ConfigurationSupport
                                    .describe("Field", field, "fieldIdentifier", "fieldLabel", "type", "conditions",
                                            "multiValue", "attributeContentType"));
                }
            }
            return output.toString();
        });
    }

    private JsonNode conditionFields(String resource) {
        return client.get("/v1/resources/{resource}/filters/rules", subject(resource).getCode());
    }

    @Override
    public String getComplianceInternalRule(String uuid) {
        return ConfigurationSupport
                .run("retrieving internal compliance rule", () -> ruleText(
                        find(rules(null, null, null, null, null), CryptoInput.uuid(uuid), INTERNAL_RULE)));
    }

    private static String ruleText(JsonNode rule) {
        StringBuilder output = new StringBuilder(
                ConfigurationSupport.describe("Internal compliance rule", rule, RULE_FIELDS));
        for (JsonNode condition : rule.path(CONDITION_ITEMS)) {
            output
                    .append(ConfigurationSupport
                            .describe("Condition", condition, "fieldSource", "fieldIdentifier", "operator"));
        }
        return output.toString();
    }

    @Override
    public String createComplianceInternalRule(String configuration) {
        return saveInternal(null, configuration);
    }

    @Override
    public String updateComplianceInternalRule(String uuid, String configuration) {
        return saveInternal(uuid, configuration);
    }

    private String saveInternal(String uuid, String configuration) {
        return ConfigurationSupport.run("saving internal compliance rule", () -> {
            ObjectNode body = CryptoInput.configuration(configuration, "name", DESCRIPTION, RESOURCE, CONDITION_ITEMS);
            CryptoInput.text(body, "name", DESCRIPTION, RESOURCE);
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : find(rules(null, null, null, null, null), CryptoInput.uuid(uuid), INTERNAL_RULE);
            CryptoInput.preserve(body, existing, "name", DESCRIPTION, RESOURCE, CONDITION_ITEMS);
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Internal rule name"));
            String resource = subject(body.path(RESOURCE).asString(null)).getCode();
            ComplianceRuleInput.conditions(body.path(CONDITION_ITEMS), conditionFields(resource));
            return ruleText(uuid == null
                    ? client.post(BASE + RULES_ENDPOINT, body)
                    : client.put(BASE + "/rules/{uuid}", body, uuid));
        });
    }

    @Override
    public String deleteComplianceInternalRule(String uuid) {
        return ConfigurationSupport.run("deleting internal compliance rule", () -> {
            client.delete(BASE + "/rules/{uuid}", CryptoInput.uuid(uuid));
            return "Deleted internal compliance rule [" + uuid + "].";
        });
    }

    @Override
    public String addComplianceRule(String uuid, String ruleUuid, String connectorUuid, String kind,
            String attributes) {
        return rulePatch(uuid, ruleUuid, connectorUuid, kind, attributes, false);
    }

    @Override
    public String removeComplianceRule(String uuid, String ruleUuid, String connectorUuid, String kind) {
        return rulePatch(uuid, ruleUuid, connectorUuid, kind, null, true);
    }

    private String rulePatch(String uuid, String ruleUuid, String connectorUuid, String kind, String attributes,
            boolean removal) {
        return ConfigurationSupport.run("changing compliance rule association", () -> {
            CryptoInput.uuid(uuid);
            ObjectNode body = ConfigurationAttributes.JSON
                    .createObjectNode()
                    .put("removal", removal)
                    .put("ruleUuid", CryptoInput.uuid(ruleUuid));
            if (connectorUuid != null || kind != null) {
                body
                        .put(CONNECTOR_UUID, CryptoInput.uuid(connectorUuid))
                        .put("kind", ConfigurationSupport.required(kind, PROVIDER_KIND));
                if (!removal) {
                    JsonNode rule = find(rules(connectorUuid, kind, null, null, null), ruleUuid, PROVIDER_RULE);
                    JsonNode profile = client.get(PROFILE, uuid);
                    JsonNode previous = findOrEmpty(
                            provider(profile.path(PROVIDER_RULES), connectorUuid, kind).path(RULES), ruleUuid);
                    JsonNode existing = array(previous.path(ATTRIBUTES));
                    JsonNode definitions = ConfigurationAttributes
                            .resolveReplacement(client, context(connectorUuid, kind), array(rule.path(ATTRIBUTES)),
                                    existing, attributes);
                    body.set(ATTRIBUTES, ConfigurationAttributes.replace(definitions, existing, attributes));
                }
            } else if (!removal) {
                find(rules(null, null, null, null, null), ruleUuid, INTERNAL_RULE);
                if (attributes != null && !ConfigurationSupport.object(attributes, "Attributes").isEmpty()) {
                    throw new IllegalArgumentException("Internal rule associations do not take provider attributes.");
                }
            }
            client.patch(PROFILE + RULES_ENDPOINT, body, uuid);
            return "Compliance rule association change accepted [" + uuid + "]. Other associations are retained.";
        });
    }

    @Override
    public String addComplianceGroup(String uuid, String connectorUuid, String kind, String groupUuid) {
        return groupPatch(uuid, connectorUuid, kind, groupUuid, false);
    }

    @Override
    public String removeComplianceGroup(String uuid, String connectorUuid, String kind, String groupUuid) {
        return groupPatch(uuid, connectorUuid, kind, groupUuid, true);
    }

    private String groupPatch(String uuid, String connectorUuid, String kind, String groupUuid, boolean removal) {
        return ConfigurationSupport.run("changing compliance group association", () -> {
            CryptoInput.uuid(uuid);
            ObjectNode body = ConfigurationAttributes.JSON
                    .createObjectNode()
                    .put("removal", removal)
                    .put("groupUuid", CryptoInput.uuid(groupUuid))
                    .put(CONNECTOR_UUID, CryptoInput.uuid(connectorUuid))
                    .put("kind", ConfigurationSupport.required(kind, PROVIDER_KIND));
            if (!removal) {
                find(groups(connectorUuid, kind, null), groupUuid, PROVIDER_GROUP);
            }
            client.patch(PROFILE + "/groups", body, uuid);
            return "Compliance group association change accepted [" + uuid + "]. Other associations are retained.";
        });
    }

    @Override
    public String getComplianceProfileAssociations(String uuid) {
        return ConfigurationSupport
                .run("retrieving compliance associations",
                        () -> ConfigurationSupport
                                .list("Compliance profile associations",
                                        client.get(PROFILE + "/associations", CryptoInput.uuid(uuid)), RESOURCE, "uuid",
                                        "name", "objectUuid", "objectName"));
    }

    @Override
    public String getResourceComplianceProfiles(String resource, String objectUuid) {
        return ConfigurationSupport
                .run("retrieving associated compliance profiles", () -> ConfigurationSupport
                        .list("Associated compliance profiles",
                                client
                                        .get(BASE + "/associations/{resource}/{uuid}", assignable(resource).getCode(),
                                                CryptoInput.uuid(objectUuid)),
                                PROFILE_FIELDS));
    }

    @Override
    public String associateComplianceProfile(String uuid, String resource, String objectUuid) {
        return association(uuid, resource, objectUuid, false);
    }

    @Override
    public String detachComplianceProfile(String uuid, String resource, String objectUuid) {
        return association(uuid, resource, objectUuid, true);
    }

    private String association(String uuid, String resource, String objectUuid, boolean remove) {
        return ConfigurationSupport.run("changing compliance resource association", () -> {
            CryptoInput.uuid(uuid);
            CryptoInput.uuid(objectUuid);
            String code = assignable(resource).getCode();
            if (remove) {
                client.delete(PROFILE + "/associations/{resource}/{objectUuid}", uuid, code, objectUuid);
            } else {
                client.patch(PROFILE + "/associations/{resource}/{objectUuid}", null, uuid, code, objectUuid);
            }
            return "Compliance resource association change accepted [" + uuid + "].";
        });
    }

    @Override
    public String checkCompliance(String resource, String objectUuid) {
        return ConfigurationSupport.run("checking compliance", () -> {
            Resource selected = resource(resource);
            if (selected == Resource.CERTIFICATE_REQUEST || (!selected.complianceSubject()
                    && !selected.hasComplianceProfiles() && selected != Resource.CRYPTOGRAPHIC_KEY_ITEM)) {
                throw new IllegalArgumentException("Resource cannot initiate a compliance check.");
            }
            client.post("/v2/compliance/{resource}/{uuid}", null, selected.getCode(), CryptoInput.uuid(objectUuid));
            return "Compliance check request accepted; get_compliance_result returns the latest completed result.";
        });
    }

    @Override
    public String checkComplianceProfiles(String profileUuids, String resource, String type) {
        return ConfigurationSupport.run("checking compliance profiles", () -> {
            ArrayNode profiles = CryptoInput.strings(profileUuids, true);
            if (profiles.isEmpty()) {
                throw new IllegalArgumentException("At least one explicit compliance profile UUID is required.");
            }
            Map<String, String> parameters = new LinkedHashMap<>();
            if (resource != null && !resource.isBlank()) {
                parameters.put(RESOURCE, subject(resource).getCode());
            }
            if (type != null) {
                if (!parameters.containsKey(RESOURCE)) {
                    throw new IllegalArgumentException("Compliance type requires a resource.");
                }
                parameters.put("type", ConfigurationSupport.required(type, "Compliance type"));
            }
            Query query = query("/v2/compliance", parameters);
            client.post(query.path(), profiles, query.variables());
            return "Compliance profile check request accepted; processing may continue asynchronously.";
        });
    }

    @Override
    public String getComplianceResult(String resource, String objectUuid) {
        return ConfigurationSupport.run("retrieving compliance results", () -> {
            Resource selected = resource(resource);
            if (!selected.complianceSubject() && selected != Resource.CRYPTOGRAPHIC_KEY_ITEM) {
                throw new IllegalArgumentException("Resource has no object compliance result.");
            }
            JsonNode result = client
                    .get("/v2/compliance/{resource}/{uuid}", selected.getCode(), CryptoInput.uuid(objectUuid));
            StringBuilder output = new StringBuilder(
                    ConfigurationSupport.describe("Latest completed compliance result", result, "status", "timestamp"));
            for (JsonNode rule : result.path("failedRules")) {
                output.append(ConfigurationSupport.describe("Failed rule", rule, RULE_FIELDS));
            }
            return output.toString();
        });
    }

    private static Resource resource(String code) {
        return Arrays
                .stream(Resource.values())
                .filter(value -> value.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported resource code."));
    }

    private static Resource subject(String code) {
        Resource resource = resource(code);
        if (!resource.complianceSubject()) {
            throw new IllegalArgumentException("Resource is not a compliance subject.");
        }
        return resource;
    }

    private static Resource assignable(String code) {
        Resource resource = resource(code);
        if (!resource.hasComplianceProfiles()) {
            throw new IllegalArgumentException("Resource does not accept compliance profile associations.");
        }
        return resource;
    }

    private JsonNode get(String path, Map<String, String> parameters) {
        Query query = query(path, parameters);
        return client.get(query.path(), query.variables());
    }

    private static Query query(String path, Map<String, String> parameters) {
        StringBuilder template = new StringBuilder(path);
        List<Object> variables = new ArrayList<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            template
                    .append(variables.isEmpty() ? '?' : '&')
                    .append(entry.getKey())
                    .append("={p")
                    .append(variables.size())
                    .append('}');
            variables.add(entry.getValue());
        }
        return new Query(template.toString(), variables.toArray());
    }

    private record Query(String path, Object[] variables) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Query(var otherPath, var otherVariables) && Objects.equals(path, otherPath)
                    && Arrays.equals(variables, otherVariables);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(path) + Arrays.hashCode(variables);
        }

        @Override
        public String toString() {
            return "Query[path=" + path + ", variables=" + Arrays.toString(variables) + "]";
        }
    }

    private static JsonNode find(JsonNode entries, String uuid, String label) {
        JsonNode result = findOrEmpty(entries, uuid);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(label + " is not available in the offered inventory.");
        }
        return result;
    }

    private static JsonNode findOrEmpty(JsonNode entries, String uuid) {
        for (JsonNode entry : entries) {
            if (uuid.equals(entry.path("uuid").asString())) {
                return entry;
            }
        }
        return ConfigurationAttributes.JSON.createObjectNode();
    }

    private static ArrayNode ids(JsonNode entries) {
        ArrayNode result = ConfigurationAttributes.JSON.createArrayNode();
        for (JsonNode entry : entries) {
            result.add(CryptoInput.uuid(entry.isString() ? entry.asString() : entry.path("uuid").asString(null)));
        }
        return result;
    }

    private static JsonNode array(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return ConfigurationAttributes.JSON.createArrayNode();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("Platform returned an invalid attribute array.");
        }
        return value;
    }
}
