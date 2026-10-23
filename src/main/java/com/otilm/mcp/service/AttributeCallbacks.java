package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import com.otilm.mcp.service.ConfigurationAttributes.CallbackContext;
import com.otilm.mcp.service.ConfigurationAttributes.ConnectorContext;
import com.otilm.mcp.service.ConfigurationAttributes.ConnectorInterfaceContext;
import com.otilm.mcp.service.ConfigurationAttributes.ResourceContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Resolves schema callback dependencies through the platform's fixed callback endpoints. */
final class AttributeCallbacks {
    private static final String DEPENDS_ON = "dependsOn";
    private static final String FILTER = "filter";
    private static final String TARGETS = "targets";

    private static final String ATTRIBUTE_CALLBACK = "attributeCallback";
    private static final int MAX_CALLBACKS = 32;

    private AttributeCallbacks() {
    }

    static ArrayNode resolve(ConfigurationApiClient client, CallbackContext context, JsonNode definitions,
            ObjectNode values) {
        if (client == null || context == null) {
            throw new IllegalArgumentException("A client and typed callback context are required.");
        }
        return new Resolution(client, context, definitions, values).resolve();
    }

    private static final class Resolution {
        private final ConfigurationApiClient client;
        private final CallbackContext context;
        private final ObjectNode values;
        private final Map<String, JsonNode> fields = new LinkedHashMap<>();
        private final Set<String> completed = new HashSet<>();
        private final Map<String, String> latest = new HashMap<>();
        private final Map<String, String> parents = new HashMap<>();
        private int calls;
        private Boolean modernRequestFields;

        private Resolution(ConfigurationApiClient client, CallbackContext context, JsonNode definitions,
                ObjectNode values) {
            this.client = client;
            this.context = context;
            this.values = values;
            ConfigurationAttributes
                    .definitions(definitions)
                    .forEach((name, definition) -> fields.put(name, definition.deepCopy()));
        }

        private ArrayNode resolve() {
            while (true) {
                List<String> unresolved = new ArrayList<>();
                if (!resolvePass(unresolved)) {
                    if (!unresolved.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Unresolved attribute callbacks: " + String.join("; ", unresolved)
                                        + ". Supply missing inputs; dependency cycles cannot be resolved.");
                    }
                    ArrayNode result = ConfigurationAttributes.JSON.createArrayNode();
                    fields.values().forEach(result::add);
                    return result;
                }
            }
        }

        private boolean resolvePass(List<String> unresolved) {
            boolean changed = false;
            for (JsonNode definition : new ArrayList<>(fields.values())) {
                if (resolveCallback(definition, unresolved)) {
                    changed = true;
                }
            }
            return changed;
        }

        private boolean resolveCallback(JsonNode definition, List<String> unresolved) {
            JsonNode callback = definition.path(ATTRIBUTE_CALLBACK);
            if (!callback.isObject() || !active(callback)) {
                return false;
            }
            ObjectNode request = request(definition, unresolved, new HashSet<>());
            if (request == null) {
                return false;
            }
            String name = ConfigurationAttributes.name(definition);
            String signature = signature(definition, request);
            if (completed.contains(signature)) {
                if (!signature.equals(latest.get(name))) {
                    throw new IllegalArgumentException("Attribute callback dependency cycle involving " + name + ".");
                }
                return false;
            }
            if (++calls > MAX_CALLBACKS) {
                throw new IllegalArgumentException("Attribute callback resolution exceeded its bounded call limit.");
            }
            if (modernRequestFields == null) {
                modernRequestFields = modernFields(client);
            }
            JsonNode response = invoke(client, context, request, modernRequestFields);
            completed.add(signature);
            latest.put(name, signature);
            apply(definition, response, fields, parents);
            return true;
        }

        private static boolean active(JsonNode callback) {
            boolean dependencies = callback.path(DEPENDS_ON).isArray();
            boolean context = callback.path("callbackContext").isString();
            if (dependencies && context) {
                throw new IllegalArgumentException(
                        "An attribute callback cannot combine dependsOn and callbackContext.");
            }
            return dependencies || context;
        }

        private ObjectNode request(JsonNode definition, List<String> unresolved, Set<String> resolving) {
            ObjectNode request = ConfigurationAttributes.JSON.createObjectNode();
            if (definition.path("uuid").isString()) {
                request
                        .put("uuid", ConfigurationSupport
                                .uuid(definition.path("uuid").asString(), "Callback attribute UUID"));
            }
            request.put("name", ConfigurationAttributes.name(definition));
            ObjectNode path = request.putObject("pathVariable");
            ObjectNode parameters = request.putObject("requestParameter");
            ObjectNode body = request.putObject("body");
            JsonNode callback = definition.path(ATTRIBUTE_CALLBACK);
            if (callback.path(DEPENDS_ON).isArray()) {
                ArrayNode attributes = request.putArray("attributes");
                for (JsonNode dependency : callback.path(DEPENDS_ON)) {
                    String name = dependency.asString();
                    JsonNode source = fields.get(name);
                    ArrayNode content = sourceContent(name, unresolved, resolving);
                    if (source == null || content == null) {
                        unresolved.add(ConfigurationAttributes.name(definition) + " needs " + name);
                        return null;
                    }
                    attributes.add(ConfigurationAttributes.request(source, content));
                }
            }
            for (JsonNode mapping : callback.path("mappings")) {
                JsonNode value = mapping.get("value");
                if (value == null || value.isNull()) {
                    value = sourceValue(mapping.path("from").asString(""), unresolved, resolving);
                }
                if (value == null || value.isNull() || value.isMissingNode()) {
                    unresolved
                            .add(ConfigurationAttributes.name(definition) + " needs "
                                    + mapping.path("from").asString("input"));
                    return null;
                }
                map(request, path, parameters, body, mapping, value);
            }
            return request;
        }

        private static void map(ObjectNode request, ObjectNode path, ObjectNode parameters, ObjectNode body,
                JsonNode mapping, JsonNode value) {
            String to = ConfigurationSupport.required(mapping.path("to").asString(""), "Callback mapping target");
            if (!mapping.path(TARGETS).isArray() || mapping.path(TARGETS).isEmpty()) {
                throw new IllegalArgumentException("Callback mapping targets are required.");
            }
            for (JsonNode target : mapping.path(TARGETS)) {
                switch (target.asString()) {
                    case "pathVariable" -> path.set(to, value);
                    case "requestParameter" -> parameters.set(to, value);
                    case "body" -> body.set(to, value);
                    case FILTER -> {
                        if (!request.has(FILTER)) {
                            request.putObject(FILTER);
                        }
                        ((ObjectNode) request.path(FILTER)).set(to, value);
                    }
                    default -> throw new IllegalArgumentException("Unsupported callback mapping target.");
                }
            }
        }

        private JsonNode sourceValue(String from, List<String> unresolved, Set<String> resolving) {
            String[] segments = from.split("\\.");
            ArrayNode content = sourceContent(segments[0], unresolved, resolving);
            if (content == null) {
                return null;
            }
            JsonNode value = content.path(0);
            if (segments.length == 1) {
                value = value.path("data");
            }
            for (int index = 1; index < segments.length; index++) {
                if (value.isArray()) {
                    value = value.path(0);
                }
                value = value.path(segments[index]);
            }
            return value.isMissingNode() || value.isNull() ? null : value;
        }

        private ArrayNode sourceContent(String name, List<String> unresolved, Set<String> resolving) {
            JsonNode definition = fields.get(name);
            if (definition == null || "group".equals(definition.path("type").asString())) {
                return null;
            }
            if (!sourceContentReady(definition, unresolved, resolving)) {
                return null;
            }
            JsonNode value = values.get(name);
            if (value != null) {
                return AttributeContentValues.encode(definition, value);
            }
            ArrayNode defaults = AttributeContentValues.defaults(definition);
            return defaults.isEmpty() ? null : defaults;
        }

        private boolean sourceContentReady(JsonNode definition, List<String> unresolved, Set<String> resolving) {
            JsonNode callback = definition.path(ATTRIBUTE_CALLBACK);
            if (!callback.isObject() || !active(callback)) {
                return true;
            }
            String name = ConfigurationAttributes.name(definition);
            if (!resolving.add(name)) {
                return false;
            }
            try {
                if (resolving.size() > MAX_CALLBACKS) {
                    throw new IllegalArgumentException(
                            "Attribute callback resolution exceeded its bounded call limit.");
                }
                ObjectNode request = request(definition, unresolved, resolving);
                return request != null && signature(definition, request).equals(latest.get(name));
            } finally {
                resolving.remove(name);
            }
        }

        private static String signature(JsonNode definition, ObjectNode request) {
            return ConfigurationAttributes.name(definition) + definition.path(ATTRIBUTE_CALLBACK) + request;
        }

        private static boolean modernFields(ConfigurationApiClient client) {
            try {
                return PlatformVersion
                        .parse(client.get("/v1/info").path("app").path("version").asString(null))
                        .map(version -> version.isAtLeast(new PlatformVersion(2, 19)))
                        .orElse(true);
            } catch (ConfigurationApiClient.ApiException unavailable) {
                return true;
            }
        }

        private static JsonNode invoke(ConfigurationApiClient client, CallbackContext context, ObjectNode request,
                boolean modernFields) {
            if (!modernFields) {
                request.remove("attributes");
            }
            return switch (context) {
                case ConnectorContext(var connectorUuid, var functionGroup, var kind) -> client
                        .post("/v1/connectors/{uuid}/{functionGroup}/{kind}/callback", request, connectorUuid,
                                functionGroup.getCode(), kind);
                case ResourceContext(var resource, var parentObjectUuid) -> client
                        .post("/v1/{resource}/{parentObjectUuid}/callback", request, resource.getCode(),
                                parentObjectUuid);
                case ConnectorInterfaceContext(var connectorUuid, var interfaceUuid) -> {
                    if (modernFields) {
                        request.put("interfaceUuid", interfaceUuid);
                    }
                    yield client.post("/v2/connectors/{uuid}/callback", request, connectorUuid);
                }
            };
        }

        private static void apply(JsonNode definition, JsonNode response, Map<String, JsonNode> fields,
                Map<String, String> parents) {
            if (!response.isArray()) {
                throw new IllegalArgumentException("Attribute callback returned an invalid schema or content array.");
            }
            String name = ConfigurationAttributes.name(definition);
            if ("group".equals(definition.path("type").asString())) {
                for (Map.Entry<String, JsonNode> entry : ConfigurationAttributes.definitions(response).entrySet()) {
                    String child = entry.getKey();
                    assertNoCycle(name, child, parents);
                    fields.put(child, entry.getValue().deepCopy());
                    parents.put(child, name);
                }
            } else {
                ((ObjectNode) fields.get(name)).set("content", response.deepCopy());
            }
        }

        private static void assertNoCycle(String parent, String child, Map<String, String> parents) {
            Set<String> visited = new HashSet<>();
            String ancestor = parent;
            while (ancestor != null && visited.add(ancestor)) {
                if (ancestor.equals(child)) {
                    throw new IllegalArgumentException("Attribute callback dependency cycle involving " + child + ".");
                }
                ancestor = parents.get(ancestor);
            }
        }
    }
}
