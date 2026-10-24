package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.service.ConfigurationAttributes.ConnectorInterfaceContext;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Fixed schema routes and complete replacement construction for vault-backed resources. */
final class VaultConfigurationRequests {
    private static final String ATTRIBUTES = "attributes";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String SECRET = "secret";

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String VAULTS = "/v1/vaults";
    static final String PROFILES = VAULTS + "/{vault}/vaultProfiles";
    static final String PROFILE = PROFILES + "/{profile}";
    static final String SECRETS = PROFILE + "/secrets";
    static final Set<String> UPDATE_FIELDS = Set.of(DESCRIPTION, ATTRIBUTES, CUSTOM_ATTRIBUTES);
    private final ConfigurationApiClient client;

    VaultConfigurationRequests(ConfigurationApiClient client) {
        this.client = client;
    }

    static String id(String value) {
        return ConfigurationSupport.uuid(value, "Object UUID");
    }

    static ObjectNode input(String json, Set<String> fields) {
        ObjectNode node = ConfigurationSupport.object(json, "Configuration");
        for (var entry : node.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!fields.contains(key)) {
                throw new IllegalArgumentException("Unsupported configuration field.");
            }
            if ((key.equals(ATTRIBUTES) || key.equals(CUSTOM_ATTRIBUTES)) && !value.isObject()) {
                throw new IllegalArgumentException("Attribute values must be JSON objects keyed by schema names.");
            }
            if ((key.equals("name") || key.equals(DESCRIPTION)) && !value.isString() && !value.isNull()) {
                throw new IllegalArgumentException("Name and description must be text.");
            }
        }
        return node;
    }

    static String values(ObjectNode input, String field) {
        return input.has(field) ? input.get(field).toString() : "{}";
    }

    static JsonNode description(ObjectNode patch, JsonNode current) {
        JsonNode value = patch.has(DESCRIPTION) ? patch.get(DESCRIPTION) : current.get(DESCRIPTION);
        return value == null ? JSON.nullNode() : value;
    }

    JsonNode vault(String uuid) {
        return client.get(VAULTS + "/{uuid}", id(uuid));
    }

    JsonNode getProfile(String vaultUuid, String profileUuid) {
        String vault = id(vaultUuid);
        String profile = id(profileUuid);
        JsonNode result = client.get(PROFILE, vault, profile);
        if (result.path("vaultInstance").path("uuid").isString()
                && !result.path("vaultInstance").path("uuid").asString().equalsIgnoreCase(vault)) {
            throw new IllegalArgumentException("Vault profile does not belong to the selected vault instance.");
        }
        return result;
    }

    ConnectorInterfaceContext context(JsonNode vault) {
        String connector = ConfigurationSupport
                .uuid(vault.path("connector").path("uuid").asString(), "Vault connector UUID");
        String iface = ConfigurationSupport
                .uuid(vault.path("connectorInterface").path("uuid").asString(), "Vault connector interface UUID");
        if (!vault.path("connectorInterface").path("code").asString(SECRET).equals(SECRET)) {
            throw new IllegalArgumentException("Vault requires a secret connector interface.");
        }
        return new ConnectorInterfaceContext(connector, iface);
    }

    ConnectorInterfaceContext connector(String connectorUuid, String interfaceUuid) {
        String connector = id(connectorUuid);
        String iface = id(interfaceUuid);
        JsonNode detail = client.get("/v2/connectors/{uuid}", connector);
        for (JsonNode offered : detail.path("interfaces")) {
            if (iface.equalsIgnoreCase(offered.path("uuid").asString())
                    && offered.path("code").asString().equals(SECRET)) {
                return new ConnectorInterfaceContext(connector, iface);
            }
        }
        throw new IllegalArgumentException("Connector does not expose the selected secret interface.");
    }

    JsonNode vaultDefinitions(String connectorUuid) {
        return client.get(VAULTS + "/{connector}/attributes", id(connectorUuid));
    }

    JsonNode profileDefinitions(String vaultUuid) {
        return client.get(PROFILES + "/attributes", id(vaultUuid));
    }

    JsonNode secretDefinitions(String vaultUuid, String profileUuid, String type) {
        String vault = id(vaultUuid);
        String profile = id(profileUuid);
        String selected = SecretContentValues.type(type);
        return client.get(SECRETS + "/{type}/attributes", vault, profile, selected);
    }

    JsonNode customDefinitions(Resource resource) {
        return client.get("/v1/attributes/custom/resources/{resource}", resource.getCode());
    }

    ArrayNode attributes(JsonNode definitions, ConnectorInterfaceContext context, ObjectNode config, JsonNode current,
            boolean replacing) {
        String values = values(config, ATTRIBUTES);
        JsonNode expanded = replacing
                ? ConfigurationAttributes
                        .resolveReplacement(client, context, definitions, array(current.path(ATTRIBUTES)), values)
                : ConfigurationAttributes.resolve(client, context, definitions, values);
        return replacing
                ? ConfigurationAttributes.replace(expanded, array(current.path(ATTRIBUTES)), values)
                : ConfigurationAttributes.encode(expanded, values);
    }

    ArrayNode custom(Resource resource, ObjectNode config, JsonNode current, boolean replacing) {
        JsonNode definitions = customDefinitions(resource);
        String values = values(config, CUSTOM_ATTRIBUTES);
        return replacing
                ? ConfigurationAttributes.replace(definitions, array(current.path(CUSTOM_ATTRIBUTES)), values)
                : ConfigurationAttributes.encode(definitions, values);
    }

    String schema(String title, JsonNode definitions, ConnectorInterfaceContext context, String values,
            Resource resource) {
        JsonNode expanded = ConfigurationAttributes.resolve(client, context, definitions, values);
        return ConfigurationAttributes.settings(title + " provider attributes", expanded) + "\n"
                + ConfigurationAttributes.settings("Custom attribute values", customDefinitions(resource));
    }

    static JsonNode array(JsonNode value) {
        return value.isArray() ? value : JSON.createArrayNode();
    }
}
