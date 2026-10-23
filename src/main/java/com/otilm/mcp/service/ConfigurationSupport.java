package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Input checks and metadata-only text shared by configuration services. */
public final class ConfigurationSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] DEFAULT_FIELDS = {
            "uuid",
            "name",
            "description",
            "status",
            "state",
            "enabled",
            "kind",
            "version"};
    private static final Set<String> PRIVATE_FIELDS = Set
            .of("authattributes", "attributes", "customattributes", "keydata", "privatekey", "secret", "secrets",
                    "password", "passphrase", "clientsecret", "apikey", "accesstoken", "refreshtoken", "bearertoken",
                    "jwt", "content", "data", "file", "inputpassphrase");

    private ConfigurationSupport() {
    }

    public static ObjectNode object(String json, String label) {
        try {
            JsonNode node = JSON.readTree(json == null || json.isBlank() ? "{}" : json);
            if (node != null && node.isObject()) {
                return (ObjectNode) node;
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(label + " must be a valid JSON object.");
        }
        throw new IllegalArgumentException(label + " must be a valid JSON object.");
    }

    public static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    public static String uuid(String value, String label) {
        String candidate = required(value, label);
        if (!Uuids.valid(candidate)) {
            throw new IllegalArgumentException(label + " must be a canonical UUID.");
        }
        return candidate;
    }

    public static String describe(String title, JsonNode result, String... fields) {
        StringBuilder output = new StringBuilder();
        TextFormat.title(output, title);
        if (result == null || result.isNull() || result.isMissingNode()) {
            output.append("Request accepted.\n");
        } else if (result.isObject()) {
            appendFields(output, result, fields);
        } else if (result.isString() && Uuids.valid(result.asString())) {
            TextFormat.field(output, "", "UUID", result.asString());
        } else if (result.isNumber() || result.isBoolean()) {
            TextFormat.field(output, "", "Result", result.asString());
        } else if (result.isArray()) {
            for (JsonNode entry : result) {
                output.append(describe("Item", entry, fields));
            }
        } else {
            output.append("Request accepted.\n");
        }
        return output.toString();
    }

    private static void appendFields(StringBuilder output, JsonNode result, String[] fields) {
        for (String field : fields.length == 0 ? DEFAULT_FIELDS : fields) {
            if (!privatePath(field)) {
                JsonNode value = result;
                for (String part : field.split("\\.")) {
                    value = value.path(part);
                }
                append(output, field, value, "");
            }
        }
    }

    public static String list(String title, JsonNode result, String... fields) {
        StringBuilder output = new StringBuilder();
        TextFormat.title(output, title);
        JsonNode entries = entries(result);
        if (entries == null || entries.isEmpty()) {
            output.append("No items found.\n");
        } else {
            for (JsonNode entry : entries) {
                output.append(describe("Item", entry, fields)).append('\n');
            }
        }
        if (result != null && result.isObject()) {
            for (String key : List.of("totalItems", "pageNumber", "totalPages")) {
                append(output, key, result.path(key), "");
            }
        }
        return output.toString();
    }

    public static String run(String action, Supplier<String> body) {
        try {
            return body.get();
        } catch (IllegalArgumentException | ApiException failure) {
            return failure.getMessage();
        } catch (RuntimeException failure) {
            return "Error " + action + ": the request could not be completed.";
        }
    }

    private static JsonNode entries(JsonNode result) {
        if (result == null || result.isNull()) {
            return null;
        }
        if (result.isArray()) {
            return result;
        }
        for (String key : List.of("items", "content", "data", "records")) {
            if (result.path(key).isArray()) {
                return result.path(key);
            }
        }
        for (var property : result.properties()) {
            if (property.getValue().isArray() && !privatePath(property.getKey())) {
                return property.getValue();
            }
        }
        return null;
    }

    private static void append(StringBuilder output, String label, JsonNode value, String indent) {
        if (value == null || value.isNull() || value.isMissingNode() || privatePath(label)) {
            return;
        }
        if (value.isObject()) {
            output.append(indent).append(label).append(":\n");
            for (var property : value.properties()) {
                append(output, property.getKey(), property.getValue(), indent + "  ");
            }
        } else if (value.isArray()) {
            output.append(indent).append(label).append(":\n");
            for (JsonNode entry : value) {
                append(output, "-", entry, indent + "  ");
            }
        } else {
            TextFormat.field(output, indent, label, value.asString());
        }
    }

    private static boolean privatePath(String path) {
        for (String part : path.split("\\.")) {
            String normalized = part.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
            if (PRIVATE_FIELDS.contains(normalized) || normalized.endsWith("password")
                    || normalized.endsWith("passphrase") || normalized.endsWith("secret")) {
                return true;
            }
        }
        return false;
    }
}
