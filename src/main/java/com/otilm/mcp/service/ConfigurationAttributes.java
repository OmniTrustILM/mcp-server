package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Schema-driven request attributes and metadata-only provider settings. */
public final class ConfigurationAttributes {
    private static final String ATTRIBUTES = "Attributes";
    private static final String CONTENT = "content";
    private static final String CONTENT_TYPE = "contentType";
    private static final String MULTI_SELECT = "multiSelect";
    private static final String PROPERTIES = "properties";
    private static final String READ_ONLY = "readOnly";
    private static final String REFERENCE = "reference";
    private static final String REQUIRED = "required";

    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> EDITABLE = Set.of("data", "custom");
    private static final Set<String> DISPLAYABLE = Set
            .of("string", "text", "integer", "float", "boolean", "date", "time", "datetime");

    private ConfigurationAttributes() {
    }

    /** Callback coordinates selected by domain services, never an API path. */
    public sealed interface CallbackContext permits ConnectorContext, ResourceContext, ConnectorInterfaceContext {
    }

    public record ConnectorContext(String connectorUuid, FunctionGroupCode functionGroup,
            String kind) implements CallbackContext {
        public ConnectorContext {
            connectorUuid = ConfigurationSupport.uuid(connectorUuid, "Connector UUID");
            if (functionGroup == null) {
                throw new IllegalArgumentException("Callback function group is required.");
            }
            kind = ConfigurationSupport.required(kind, "Connector kind");
        }
    }

    public record ResourceContext(Resource resource, String parentObjectUuid) implements CallbackContext {
        public ResourceContext {
            if (resource == null) {
                throw new IllegalArgumentException("Callback resource is required.");
            }
            parentObjectUuid = ConfigurationSupport.uuid(parentObjectUuid, "Parent object UUID");
        }
    }

    public record ConnectorInterfaceContext(String connectorUuid, String interfaceUuid) implements CallbackContext {
        public ConnectorInterfaceContext {
            connectorUuid = ConfigurationSupport.uuid(connectorUuid, "Connector UUID");
            interfaceUuid = ConfigurationSupport.uuid(interfaceUuid, "Interface UUID");
        }
    }

    public static ArrayNode encode(JsonNode definitions, String valuesJson) {
        ObjectNode values = ConfigurationSupport.object(valuesJson, ATTRIBUTES);
        Map<String, JsonNode> fields = definitions(definitions);
        validateNames(fields, values);
        ArrayNode encoded = JSON.createArrayNode();
        for (JsonNode definition : fields.values()) {
            if (!editable(definition)) {
                continue;
            }
            JsonNode value = values.get(name(definition));
            ArrayNode content = encodedContent(definition, value);
            if (content.isEmpty()) {
                if (property(definition, REQUIRED)) {
                    throw new IllegalArgumentException("Attribute " + name(definition) + " requires a fresh value.");
                }
            } else {
                encoded.add(request(definition, content));
            }
        }
        return encoded;
    }

    private static ArrayNode encodedContent(JsonNode definition, JsonNode value) {
        if (property(definition, READ_ONLY)) {
            return AttributeContentValues.fixed(definition);
        }
        return value == null
                ? AttributeContentValues.defaults(definition)
                : AttributeContentValues.encode(definition, value);
    }

    public static ArrayNode resolve(ConfigurationApiClient client, CallbackContext context, JsonNode definitions,
            String valuesJson) {
        return AttributeCallbacks
                .resolve(client, context, definitions, ConfigurationSupport.object(valuesJson, ATTRIBUTES));
    }

    public static ArrayNode encode(ConfigurationApiClient client, CallbackContext context, JsonNode definitions,
            String valuesJson) {
        return encode(resolve(client, context, definitions, valuesJson), valuesJson);
    }

    /** Preserves readable existing content for complete replacements; protected or masked values need fresh input. */
    public static ArrayNode replace(JsonNode definitions, JsonNode existing, String valuesJson) {
        ObjectNode supplied = ConfigurationSupport.object(valuesJson, ATTRIBUTES);
        ObjectNode merged = supplied.deepCopy();
        Map<String, JsonNode> fields = definitions(definitions);
        Map<String, JsonNode> previous = definitions(existing);
        for (Map.Entry<String, JsonNode> entry : previous.entrySet()) {
            String name = entry.getKey();
            preserveExistingValue(name, fields.get(name), entry.getValue(), supplied, merged);
        }
        ArrayNode encoded = encode(definitions, merged.toString());
        for (JsonNode attribute : encoded) {
            String name = name(attribute);
            JsonNode old = previous.get(name);
            if (!property(fields.get(name), READ_ONLY) && !property(fields.get(name), "list") && !supplied.has(name)
                    && old != null && old.path(CONTENT).isArray() && !old.path(CONTENT).isEmpty()) {
                ((ObjectNode) attribute)
                        .set(CONTENT, AttributeContentValues.copyContent(fields.get(name), old.path(CONTENT)));
            }
        }
        return encoded;
    }

    private static void preserveExistingValue(String name, JsonNode definition, JsonNode existing, ObjectNode supplied,
            ObjectNode merged) {
        if (definition == null) {
            throw new IllegalArgumentException("Existing attribute " + name + " has no schema; replacement is unsafe.");
        }
        if (supplied.has(name) || property(definition, READ_ONLY)) {
            return;
        }
        JsonNode content = existing.path(CONTENT);
        if (AttributeContentValues.protectedValue(definition)) {
            throw new IllegalArgumentException("Attribute " + name + " needs a fresh value for replacement.");
        }
        if (!content.isArray() || content.isEmpty()) {
            return;
        }
        if (AttributeContentValues.masked(content)) {
            throw new IllegalArgumentException("Attribute " + name + " needs a fresh value for replacement.");
        }
        merged.set(name, existingSelection(definition, content));
    }

    public static String settings(String title, JsonNode definitions) {
        StringBuilder output = new StringBuilder();
        TextFormat.title(output, title);
        for (JsonNode field : definitions(definitions).values()) {
            output.append("- ").append(name(field));
            String label = field.path(PROPERTIES).path("label").asString("");
            if (!label.isBlank()) {
                output.append(" | ").append(label);
            }
            output
                    .append(" (")
                    .append(field.path(CONTENT_TYPE).asString(field.path("type").asString("data")))
                    .append(", version ")
                    .append(AttributeContentValues.version(field));
            appendProperty(output, field, REQUIRED, REQUIRED);
            appendProperty(output, field, READ_ONLY, "read-only");
            appendProperty(output, field, "list", "list");
            appendProperty(output, field, MULTI_SELECT, "multiple values");
            output.append(')');
            appendSafeContent(output, field);
            JsonNode callback = field.path("attributeCallback");
            if (callback.isObject()) {
                output.append("; callback resolves provider choices");
                for (JsonNode mapping : callback.path("mappings")) {
                    if (mapping.path("from").isString()) {
                        output.append("; needs ").append(mapping.path("from").asString());
                    }
                }
                for (JsonNode dependency : callback.path("dependsOn")) {
                    output.append("; needs ").append(dependency.asString());
                }
            }
            output.append('\n');
        }
        return output.toString();
    }

    private static void appendSafeContent(StringBuilder output, JsonNode field) {
        if (AttributeContentValues.protectedValue(field)
                || !DISPLAYABLE.contains(field.path(CONTENT_TYPE).asString(""))) {
            return;
        }
        boolean first = true;
        for (JsonNode entry : field.path(CONTENT)) {
            JsonNode data = entry.path("data");
            if (data.isValueNode() && !data.isNull() && !AttributeContentValues.masked(data)) {
                output.append(contentLabel(field, first)).append(data.asString());
                first = false;
            }
        }
    }

    private static String contentLabel(JsonNode field, boolean first) {
        if (!first) {
            return ", ";
        }
        return property(field, "list") ? " — Choices: " : " — Default: ";
    }

    private static void appendProperty(StringBuilder output, JsonNode field, String property, String label) {
        if (property(field, property)) {
            output.append(", ").append(label);
        }
    }

    static Map<String, JsonNode> definitions(JsonNode definitions) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        if (definitions == null || definitions.isNull()) {
            return fields;
        }
        if (!definitions.isArray()) {
            throw new IllegalArgumentException("Attribute definitions must be an array.");
        }
        for (JsonNode definition : definitions) {
            String name = name(definition);
            if (!definition.isObject() || name.isBlank() || fields.putIfAbsent(name, definition) != null) {
                throw new IllegalArgumentException("Attribute definitions need unique names.");
            }
        }
        return fields;
    }

    private static void validateNames(Map<String, JsonNode> definitions, ObjectNode values) {
        for (String name : values.propertyNames()) {
            JsonNode definition = definitions.get(name);
            if (definition == null || !editable(definition)) {
                throw new IllegalArgumentException("Unknown attribute: " + name + ".");
            }
            if (property(definition, READ_ONLY)) {
                throw new IllegalArgumentException("Attribute " + name + " is read-only.");
            }
        }
    }

    static ObjectNode request(JsonNode definition, ArrayNode content) {
        ObjectNode request = JSON.createObjectNode();
        if (definition.path("uuid").isString()) {
            request.put("uuid", ConfigurationSupport.uuid(definition.path("uuid").asString(), "Attribute UUID"));
        }
        request.put("name", name(definition));
        request.put("version", "v" + AttributeContentValues.version(definition));
        request.put(CONTENT_TYPE, definition.path(CONTENT_TYPE).asString());
        request.set(CONTENT, content);
        return request;
    }

    static boolean property(JsonNode definition, String property) {
        return definition.path(PROPERTIES).path(property).asBoolean(false);
    }

    static String name(JsonNode definition) {
        return definition.path("name").asString("");
    }

    private static boolean editable(JsonNode definition) {
        return EDITABLE.contains(definition.path("type").asString("data"));
    }

    /** Resolves replacement callbacks using safe existing readable values without replaying protected content. */
    public static ArrayNode resolveReplacement(ConfigurationApiClient client, CallbackContext context,
            JsonNode definitions, JsonNode existing, String valuesJson) {
        ObjectNode supplied = ConfigurationSupport.object(valuesJson, ATTRIBUTES);
        ObjectNode callbackValues = supplied.deepCopy();
        Map<String, JsonNode> previous = definitions(existing);
        for (Map.Entry<String, JsonNode> entry : definitions(definitions).entrySet()) {
            String name = entry.getKey();
            JsonNode definition = entry.getValue();
            seedCallbackValue(name, definition, previous.get(name), supplied, callbackValues);
        }
        return resolve(client, context, definitions, callbackValues.toString());
    }

    private static void seedCallbackValue(String name, JsonNode definition, JsonNode previous, ObjectNode supplied,
            ObjectNode callbackValues) {
        if (property(definition, READ_ONLY)) {
            AttributeContentValues.fixed(definition);
            return;
        }
        if (supplied.has(name) || previous == null) {
            return;
        }
        JsonNode content = previous.path(CONTENT);
        if (AttributeContentValues.protectedValue(definition) || AttributeContentValues.masked(content)) {
            throw new IllegalArgumentException("Attribute " + name + " needs a fresh value for replacement.");
        }
        if (content.isArray() && !content.isEmpty()) {
            JsonNode selection = existingSelection(definition, content);
            if (!hasContentCallback(definition)) {
                AttributeContentValues.encode(definition, selection);
            }
            callbackValues.set(name, selection);
        }
    }

    private static JsonNode existingSelection(JsonNode definition, JsonNode content) {
        if (!property(definition, MULTI_SELECT) && content.size() > 1) {
            throw new IllegalArgumentException("Attribute " + name(definition) + " takes one value.");
        }
        JsonNode data = AttributeContentValues.existingValue(definition, content);
        if (!property(definition, "list")) {
            AttributeContentValues.encode(definition, data);
            return data;
        }
        // Callback choices may be stale or absent; validate the stored datum shape independently.
        ObjectNode scalarSchema = ((ObjectNode) definition).deepCopy();
        ((ObjectNode) scalarSchema.path(PROPERTIES)).put("list", false);
        AttributeContentValues.encode(scalarSchema, data);
        ArrayNode selections = JSON.createArrayNode();
        for (JsonNode item : content) {
            JsonNode reference = item.path(REFERENCE);
            selections
                    .add(reference.isString()
                            ? JSON.createObjectNode().put(REFERENCE, reference.asString())
                            : item.path("data"));
        }
        return property(definition, MULTI_SELECT) ? selections : selections.get(0);
    }

    private static boolean hasContentCallback(JsonNode definition) {
        JsonNode callback = definition.path("attributeCallback");
        return property(definition, "list")
                && (callback.path("dependsOn").isArray() || callback.path("callbackContext").isString());
    }

}
