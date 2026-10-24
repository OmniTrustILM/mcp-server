package com.otilm.mcp.service;

import com.otilm.api.model.common.attribute.common.content.data.ProgrammingLanguageEnum;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Encodes the released attribute content variants without interface-library serializers. */
final class AttributeContentValues {
    private static final String ATTRIBUTES = "attributes";
    private static final String CODEBLOCK = "codeblock";
    private static final String CONTENT = "content";
    private static final String CONTENT_TYPE = "contentType";
    private static final String CREDENTIAL = "credential";
    private static final String LANGUAGE = "language";
    private static final String MULTI_SELECT = "multiSelect";
    private static final String PROTECTION_LEVEL = "protectionLevel";
    private static final String REFERENCE = "reference";
    private static final String RESOURCE = "resource";
    private static final String SECRET = "secret";

    private static final Set<String> TYPES = Set
            .of("string", "text", "integer", "float", "boolean", "date", "time", "datetime", SECRET, "file", CREDENTIAL,
                    CODEBLOCK, "object", RESOURCE);
    private static final Set<String> RESOURCES = Set
            .of("credentials", "certificates", "secrets", "authorities", "entities", "locations");

    private AttributeContentValues() {
    }

    /** Read-only data is fixed by the provider schema and must be repeated exactly in requests. */
    static ArrayNode fixed(JsonNode definition) {
        if (ConfigurationAttributes.property(definition, "list") || definition.hasNonNull("attributeCallback")
                || protectedValue(definition)) {
            throw invalid(definition, "has read-only content that cannot be safely reproduced");
        }
        ArrayNode content = defaults(definition);
        if (content.isEmpty()) {
            throw invalid(definition, "needs available, validated fixed read-only content");
        }
        return content;
    }

    static int version(JsonNode definition) {
        String declared = definition.path("version").asString("");
        String schema = definition.path("schemaVersion").asString("");
        int version;
        if (declared.isEmpty()) {
            version = schema.isEmpty() ? 2 : parseVersion(schema);
        } else {
            version = parseVersion(declared);
        }
        if (!schema.isEmpty() && parseVersion(schema) != version) {
            throw invalid(definition, "has inconsistent schema versions");
        }
        return version;
    }

    private static int parseVersion(String version) {
        return switch (version) {
            case "2", "v2" -> 2;
            case "3", "v3" -> 3;
            default -> throw new IllegalArgumentException("Unsupported attribute schema version.");
        };
    }

    static ArrayNode encode(JsonNode definition, JsonNode value) {
        validateType(definition);
        if (value.isNull()) {
            throw invalid(definition, "needs a value");
        }
        ArrayNode selected = ConfigurationAttributes.JSON.createArrayNode();
        boolean selection = ConfigurationAttributes.property(definition, "list")
                || ConfigurationAttributes.property(definition, MULTI_SELECT);
        if (selection && value.isArray()) {
            selected.addAll((ArrayNode) value);
        } else {
            selected.add(value);
        }
        if ((selected.isEmpty() && ConfigurationAttributes.property(definition, "required"))
                || (!ConfigurationAttributes.property(definition, MULTI_SELECT) && selected.size() > 1)) {
            throw invalid(definition, "takes one value");
        }
        ArrayNode content = ConfigurationAttributes.JSON.createArrayNode();
        for (JsonNode entry : selected) {
            content.add(content(definition, entry));
        }
        return content;
    }

    static ArrayNode defaults(JsonNode definition) {
        validateType(definition);
        JsonNode existing = definition.path(CONTENT);
        if (ConfigurationAttributes.property(definition, "list") || protectedValue(definition) || !existing.isArray()
                || existing.isEmpty() || masked(existing)) {
            return ConfigurationAttributes.JSON.createArrayNode();
        }
        for (JsonNode entry : existing) {
            if (entry.path("data").isMissingNode() || entry.path("data").isNull()
                    || (CODEBLOCK.equals(type(definition)) && !entry.path("data").path("code").isString())) {
                return ConfigurationAttributes.JSON.createArrayNode();
            }
            validateData(definition, entry.path("data"));
        }
        if (!ConfigurationAttributes.property(definition, MULTI_SELECT) && existing.size() > 1) {
            throw invalid(definition, "takes one value");
        }
        return copyContent(definition, existing);
    }

    private static ObjectNode content(JsonNode definition, JsonNode value) {
        if (ConfigurationAttributes.property(definition, "list")) {
            JsonNode offered = option(definition, value);
            if (offered != null) {
                ObjectNode result = (ObjectNode) offered.deepCopy();
                if (version(definition) == 3) {
                    result.put(CONTENT_TYPE, type(definition));
                }
                return result;
            }
            if (!ConfigurationAttributes.property(definition, "extensibleList")) {
                throw invalid(definition, "must use an offered option or reference");
            }
        }
        JsonNode data = value;
        if (CODEBLOCK.equals(type(definition)) && value.isString()) {
            ObjectNode code = ConfigurationAttributes.JSON.createObjectNode();
            code.put("code", Base64.getEncoder().encodeToString(value.asString().getBytes(StandardCharsets.UTF_8)));
            JsonNode language = definition.path(CONTENT).path(0).path("data").path(LANGUAGE);
            if (!language(language)) {
                throw invalid(definition,
                        "needs an explicit supported language and Base64 code object; no valid schema language is available");
            }
            code.put(LANGUAGE, language.asString());
            data = code;
        }
        validateData(definition, data);
        ObjectNode result = ConfigurationAttributes.JSON.createObjectNode();
        if (version(definition) == 3) {
            result.put(CONTENT_TYPE, type(definition));
        }
        result.set("data", data.deepCopy());
        return result;
    }

    private static JsonNode option(JsonNode definition, JsonNode value) {
        boolean byReference = value.isObject() && value.size() == 1 && value.path(REFERENCE).isString();
        for (JsonNode offered : definition.path(CONTENT)) {
            if (matchesOption(value, offered, byReference)) {
                if (offered.path("data").isNull() || offered.path("data").isMissingNode()) {
                    if (!offered.path(REFERENCE).isString()) {
                        throw invalid(definition, "has an incomplete offered option");
                    }
                } else {
                    validateData(definition, offered.path("data"));
                }
                return offered;
            }
        }
        if (byReference) {
            throw invalid(definition, "must use an offered option or reference");
        }
        return null;
    }

    private static boolean matchesOption(JsonNode value, JsonNode offered, boolean byReference) {
        return byReference ? value.path(REFERENCE).equals(offered.path(REFERENCE)) : value.equals(offered.path("data"));
    }

    static ArrayNode copyContent(JsonNode definition, JsonNode entries) {
        ArrayNode content = ConfigurationAttributes.JSON.createArrayNode();
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                throw invalid(definition, "has invalid content");
            }
            ObjectNode copy = (ObjectNode) entry.deepCopy();
            if (version(definition) == 3) {
                copy.put(CONTENT_TYPE, type(definition));
            }
            content.add(copy);
        }
        return content;
    }

    private static void validateType(JsonNode definition) {
        int version = version(definition);
        if (!TYPES.contains(type(definition)) || (version == 2 && RESOURCE.equals(type(definition)))
                || (version == 3 && Set.of(SECRET, CREDENTIAL).contains(type(definition)))) {
            throw invalid(definition, "has an unsupported content type for its schema version");
        }
    }

    private static void validateData(JsonNode definition, JsonNode data) {
        boolean valid = switch (type(definition)) {
            case "string", "text" -> data.isString();
            case "integer" -> data.isIntegralNumber() && data.canConvertToInt();
            case "float" -> data.isNumber();
            case "boolean" -> data.isBoolean();
            case "date", "time", "datetime" -> temporal(type(definition), data);
            case SECRET -> secret(data);
            case "file" -> file(data);
            case CREDENTIAL -> credential(data);
            case CODEBLOCK -> code(data);
            case "object" -> data.isObject() || data.isArray();
            case RESOURCE -> resource(definition, data);
            default -> false;
        };
        if (!valid || masked(data)) {
            throw invalid(definition,
                    "has an invalid " + type(definition) + " value; supply fresh, correctly typed data");
        }
    }

    private static boolean temporal(String type, JsonNode data) {
        if (!data.isString()) {
            return false;
        }
        try {
            switch (type) {
                case "date" -> LocalDate.parse(data.asString());
                case "time" -> LocalTime.parse(data.asString());
                default -> ZonedDateTime.parse(data.asString());
            }
            return true;
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }

    private static boolean secret(JsonNode data) {
        return data.isObject() && data.path(SECRET).isString()
                && Set.of("none", "").contains(data.path(PROTECTION_LEVEL).asString(""))
                && only(data, Set.of(SECRET, PROTECTION_LEVEL));
    }

    private static boolean file(JsonNode data) {
        return data.isObject() && data.path("fileName").isString() && data.path("mimeType").isString()
                && base64(data.path(CONTENT)) && only(data, Set.of("fileName", "mimeType", CONTENT));
    }

    private static boolean credential(JsonNode data) {
        if (!data.isObject() || !only(data, Set.of("uuid", "name", "kind", ATTRIBUTES))) {
            return false;
        }
        if (data.has("uuid")) {
            ConfigurationSupport.uuid(data.path("uuid").asString(), "Credential UUID");
            return !data.has(ATTRIBUTES) || data.path(ATTRIBUTES).isArray();
        }
        return data.path("name").isString() && data.path("kind").isString() && data.path(ATTRIBUTES).isArray();
    }

    private static boolean code(JsonNode data) {
        return data.isObject() && language(data.path(LANGUAGE)) && base64(data.path("code"))
                && only(data, Set.of("code", LANGUAGE));
    }

    private static boolean language(JsonNode value) {
        return value.isString() && Arrays
                .stream(ProgrammingLanguageEnum.values())
                .anyMatch(language -> language.getCode().equals(value.asString()));
    }

    private static boolean resource(JsonNode definition, JsonNode data) {
        if (!data.isObject() || !RESOURCES.contains(data.path(RESOURCE).asString(""))
                || !data.path("uuid").isString()) {
            return false;
        }
        ConfigurationSupport.uuid(data.path("uuid").asString(), "Resource UUID");
        String expected = definition.path("properties").path(RESOURCE).asString("");
        return expected.isEmpty() || expected.equals(data.path(RESOURCE).asString());
    }

    private static boolean base64(JsonNode node) {
        if (!node.isString()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(node.asString());
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean only(JsonNode data, Set<String> allowed) {
        return allowed.containsAll(data.propertyNames());
    }

    static boolean protectedValue(JsonNode definition) {
        String protection = definition.path("properties").path(PROTECTION_LEVEL).asString("none");
        return Set.of(SECRET, CREDENTIAL).contains(type(definition)) || !"none".equalsIgnoreCase(protection);
    }

    static boolean masked(JsonNode data) {
        if (data.isString()) {
            String value = data.asString();
            return value.matches("\\*{3,}") || Set.of("[REDACTED]", "<redacted>", "REDACTED").contains(value);
        }
        for (JsonNode child : data) {
            if (masked(child)) {
                return true;
            }
        }
        return false;
    }

    static JsonNode existingValue(JsonNode definition, JsonNode content) {
        if (ConfigurationAttributes.property(definition, MULTI_SELECT)) {
            ArrayNode values = ConfigurationAttributes.JSON.createArrayNode();
            content.forEach(entry -> values.add(entry.path("data")));
            return values;
        }
        return content.path(0).path("data");
    }

    private static String type(JsonNode definition) {
        return definition.path(CONTENT_TYPE).asString("");
    }

    private static IllegalArgumentException invalid(JsonNode definition, String reason) {
        return new IllegalArgumentException(
                "Attribute " + ConfigurationAttributes.name(definition) + " " + reason + ".");
    }
}
