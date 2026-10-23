package com.otilm.mcp.service;

import com.otilm.api.model.connector.secrets.SecretType;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Owned typed content envelopes avoid write-only DTO serializers and keep error messages content-free. */
final class SecretContentValues {
    private static final String BASIC_AUTH = "basicAuth";
    private static final String CONTENT = "content";
    private static final String KEY_STORE = "keyStore";
    private static final String KEY_STORE_TYPE = "keyStoreType";
    private static final String PASSWORD = "password";
    private static final String USERNAME = "username";

    private SecretContentValues() {
    }

    static String type(String value) {
        String wanted = ConfigurationSupport.required(value, "Secret type");
        if (Arrays.stream(SecretType.values()).noneMatch(type -> type.getCode().equals(wanted))) {
            throw new IllegalArgumentException(
                    "Unsupported secret type. Choose basicAuth, apiKey, jwtToken, privateKey, secretKey, keyStore, keyValue or generic.");
        }
        return wanted;
    }

    static ObjectNode validate(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("A typed secret content object is required.");
        }
        String type = type(value.path("type").asString());
        validateFields(value, type);
        switch (type) {
            case BASIC_AUTH -> {
                text(value.get(USERNAME), false);
                text(value.get(PASSWORD), false);
            }
            case "keyValue" -> validateKeyValue(value);
            default -> validateContent(value, type);
        }
        return ((ObjectNode) value).deepCopy();
    }

    private static void validateKeyValue(JsonNode value) {
        if (!value.path(CONTENT).isObject()) {
            throw new IllegalArgumentException("Key-value secret content must be a JSON object.");
        }
    }

    private static void validateFields(JsonNode value, String type) {
        Set<String> fields = switch (type) {
            case BASIC_AUTH -> Set.of("type", USERNAME, PASSWORD);
            case KEY_STORE -> Set.of("type", KEY_STORE_TYPE, CONTENT, PASSWORD);
            default -> Set.of("type", CONTENT);
        };
        for (var field : value.properties()) {
            if (!fields.contains(field.getKey())) {
                throw new IllegalArgumentException("Unsupported secret content field.");
            }
        }
    }

    private static void validateContent(JsonNode value, String type) {
        text(value.get(CONTENT), false);
        if (Set.of("privateKey", "secretKey", KEY_STORE).contains(type)) {
            try {
                Base64.getDecoder().decode(value.get(CONTENT).asString());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Binary secret content must be valid base64.");
            }
        }
        if (type.equals(KEY_STORE)) {
            if (!Set.of("JKS", "PKCS12").contains(value.path(KEY_STORE_TYPE).asString())) {
                throw new IllegalArgumentException("Key store type must be JKS or PKCS12.");
            }
            text(value.get(PASSWORD), true);
        }
    }

    private static void text(JsonNode value, boolean allowEmpty) {
        if (value == null || !value.isString() || (!allowEmpty && value.asString().isBlank())) {
            throw new IllegalArgumentException("Secret content is missing required text data.");
        }
        if (value.asString().matches("(?i)([*•]+|<redacted>|\\[redacted\\]|redacted|masked)")) {
            throw new IllegalArgumentException(
                    "Secret content requires fresh values; masked replacements are refused.");
        }
    }

    static String output(String uuid, JsonNode value) {
        String type = type(value.path("type").asString());
        StringBuilder output = new StringBuilder();
        TextFormat.title(output, "Sensitive secret content [" + uuid + "]");
        TextFormat.field(output, "", "Type", type);
        if (type.equals(BASIC_AUTH)) {
            TextFormat.field(output, "", "Username", value.path(USERNAME).asString());
            TextFormat.field(output, "", "Password", value.path(PASSWORD).asString());
        } else {
            JsonNode content = value.path(CONTENT);
            TextFormat.field(output, "", "Content", content.isObject() ? content.toPrettyString() : content.asString());
            if (type.equals(KEY_STORE)) {
                TextFormat.field(output, "", "Key store type", value.path(KEY_STORE_TYPE).asString());
                TextFormat.field(output, "", "Password", value.path(PASSWORD).asString());
            }
        }
        return output.toString();
    }
}
