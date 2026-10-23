package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Structural checks for cryptographic configuration; messages never contain supplied values. */
final class CryptoInput {
    private static final String GROUP_UUIDS = "groupUuids";

    private static final Set<String> USAGES = Set.of("sign", "verify", "encrypt", "decrypt", "wrap", "unwrap");

    private CryptoInput() {
    }

    static ObjectNode configuration(String json, String... fields) {
        ObjectNode result = ConfigurationSupport.object(json, "Configuration");
        if (!Set.of(fields).containsAll(result.propertyNames())) {
            throw new IllegalArgumentException("Unknown configuration field; use the documented fields.");
        }
        return result;
    }

    static String uuid(String value) {
        return ConfigurationSupport.uuid(value, "UUID");
    }

    static String type(String type) {
        if (!Set.of("keyPair", "secret").contains(type == null ? "" : type)) {
            throw new IllegalArgumentException("Key type must be keyPair or secret.");
        }
        return type;
    }

    static void text(ObjectNode body, String... names) {
        for (String name : names) {
            if (body.has(name) && !body.path(name).isNull() && !body.path(name).isString()) {
                throw new IllegalArgumentException(name + " must be text.");
            }
        }
    }

    static void bool(ObjectNode body, String... names) {
        for (String name : names) {
            if (body.has(name) && !body.path(name).isBoolean()) {
                throw new IllegalArgumentException(name + " must be true or false.");
            }
        }
    }

    static String values(JsonNode body, String field) {
        if (!body.has(field)) {
            return "{}";
        }
        if (!body.path(field).isObject()) {
            throw new IllegalArgumentException(field + " must be an object of attribute names and values.");
        }
        return body.path(field).toString();
    }

    static ArrayNode strings(String json, boolean uuids) {
        JsonNode node;
        try {
            node = ConfigurationAttributes.JSON.readTree(json == null || json.isBlank() ? "[]" : json);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Expected a valid JSON array.");
        }
        return strings(node, uuids);
    }

    static ArrayNode strings(JsonNode node, boolean uuids) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Expected an array of strings.");
        }
        ArrayNode result = ConfigurationAttributes.JSON.createArrayNode();
        Set<String> unique = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.isString() || !unique.add(value.asString())) {
                throw new IllegalArgumentException("Array values must be unique strings.");
            }
            result.add(uuids ? uuid(value.asString()) : value.asString());
        }
        return result;
    }

    static ArrayNode usages(String json) {
        ArrayNode values = strings(json, false);
        for (JsonNode value : values) {
            if (!USAGES.contains(value.asString())) {
                throw new IllegalArgumentException("Key usage must be sign, verify, encrypt, decrypt, wrap or unwrap.");
            }
        }
        return values;
    }

    static void groups(ObjectNode body) {
        if (body.has(GROUP_UUIDS)) {
            body.set(GROUP_UUIDS, strings(body.path(GROUP_UUIDS), true));
        }
    }

    static ArrayNode custom(ConfigurationApiClient client, String resource, JsonNode existing, ObjectNode body) {
        JsonNode definitions = client.get("/v1/attributes/custom/resources/{resource}", resource);
        JsonNode previous = existing.path("customAttributes");
        return ConfigurationAttributes
                .replace(definitions, previous.isArray() ? previous : ConfigurationAttributes.JSON.createArrayNode(),
                        values(body, "customAttributes"));
    }

    static void file(ObjectNode body) {
        JsonNode file = body.path("file");
        if (!file.isString()) {
            throw new IllegalArgumentException("A base64 file is required.");
        }
        if (file.asString().length() > 4 * ((5 * 1024 * 1024 + 2) / 3)) {
            throw new IllegalArgumentException("File must not exceed 5 MiB decoded.");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(file.asString());
            if (decoded.length == 0 || decoded.length > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("File must contain data and must not exceed 5 MiB decoded.");
            }
        } catch (IllegalArgumentException invalid) {
            if (invalid.getMessage().contains("5 MiB")) {
                throw invalid;
            }
            throw new IllegalArgumentException("File must be valid base64.");
        }
    }

    static void preserve(ObjectNode body, JsonNode existing, String... names) {
        for (String name : names) {
            if (!body.has(name) && existing.has(name)) {
                body.set(name, existing.path(name));
            }
        }
    }
}
