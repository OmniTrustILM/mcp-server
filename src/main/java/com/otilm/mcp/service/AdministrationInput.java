package com.otilm.mcp.service;

import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Validates the structured input used by administration and approval tools. */
final class AdministrationInput {
    static final JsonMapper JSON = JsonMapper.builder().build();

    private AdministrationInput() {
    }

    static ObjectNode configuration(String json, String... fields) {
        ObjectNode result = ConfigurationSupport.object(json, "Configuration");
        allow(result, fields);
        return result;
    }

    static void allow(JsonNode value, String... fields) {
        Set<String> allowed = Set.of(fields);
        value.properties().forEach(entry -> {
            if (!allowed.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown configuration field; use the documented fields.");
            }
        });
    }

    static void text(JsonNode value, String... fields) {
        for (String field : fields) {
            if (value.hasNonNull(field) && !value.path(field).isString()) {
                throw new IllegalArgumentException(field + " must be text or null.");
            }
        }
    }

    static void bool(JsonNode value, String field) {
        if (value.has(field) && !value.path(field).isBoolean()) {
            throw new IllegalArgumentException(field + " must be true or false.");
        }
    }

    static ArrayNode strings(JsonNode values, String label, boolean uuid) {
        if (!values.isArray()) {
            throw new IllegalArgumentException(label + " must be a JSON array.");
        }
        ArrayNode result = JSON.createArrayNode();
        Set<String> unique = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isString()) {
                throw new IllegalArgumentException(label + " must contain strings.");
            }
            String checked = uuid
                    ? ConfigurationSupport.uuid(value.asString(), label + " UUID")
                    : ConfigurationSupport.required(value.asString(), label);
            if (!unique.add(checked)) {
                throw new IllegalArgumentException(label + " must not contain duplicates.");
            }
            result.add(checked);
        }
        return result;
    }

    static ArrayNode uuids(String values) {
        try {
            return strings(JSON.readTree(values), "UUID list", true);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("UUID list must be a JSON array of UUID strings.");
        }
    }

    static ObjectNode select(JsonNode source, String... fields) {
        ObjectNode result = JSON.createObjectNode();
        for (String field : fields) {
            if (source.has(field)) {
                result.set(field, source.get(field).deepCopy());
            }
        }
        return result;
    }

    static void replaceIntent(boolean replace) {
        if (!replace) {
            throw new IllegalArgumentException("Set replace=true to explicitly replace the complete list.");
        }
    }

    static String id(String value) {
        return ConfigurationSupport.uuid(value, "Object UUID");
    }
}
