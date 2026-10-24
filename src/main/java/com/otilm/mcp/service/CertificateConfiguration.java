package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Base64;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.ConfigurationSupport.required;

/** Shared schema and input handling for certificate provisioning. */
final class CertificateConfiguration {
    static final String RA = "/v1/authorities/{authority}/raProfiles/{profile}";
    static final String OPS = "/v2/operations/authorities/{authority}/raProfiles/{profile}";
    static final String[] META = {
            "uuid",
            "name",
            "description",
            "enabled",
            "status",
            "state",
            "fingerprint",
            "authorityInstanceUuid",
            "authorityInstanceName",
            "connectorUuid",
            "connectorName",
            "kind"};
    final ConfigurationApiClient client;
    final PlatformVersionCheck versions;

    CertificateConfiguration(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    void require(IlmFeature feature) {
        versions.unsupported(feature).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    static ArrayNode array(JsonNode value) {
        return value != null && value.isArray() ? (ArrayNode) value : JSON.createArrayNode();
    }

    ArrayNode attributes(String path, ConfigurationAttributes.CallbackContext context, JsonNode existing,
            JsonNode values, Object... coordinates) {
        String input = values == null || values.isMissingNode() ? "{}" : values.toString();
        JsonNode definitions = client.get(path, coordinates);
        JsonNode resolved = ConfigurationAttributes
                .resolveReplacement(client, context, definitions, array(existing), input);
        return ConfigurationAttributes.replace(resolved, array(existing), input);
    }

    JsonNode resolveOperation(String authority, String profile, JsonNode definitions, String values) {
        return resolveOperation(authority, profile, definitions, null, values);
    }

    private JsonNode resolveOperation(String authority, String profile, JsonNode definitions, JsonNode existing,
            String values) {
        ConfigurationAttributes.CallbackContext context = new ConfigurationAttributes.ResourceContext(
                Resource.CERTIFICATE, profile);
        boolean callbacks = false;
        for (JsonNode definition : definitions) {
            if (definition.hasNonNull("attributeCallback")) {
                callbacks = true;
            }
        }
        if (callbacks) {
            JsonNode instance = client.get("/v1/authorities/{authority}", authority);
            if (!instance.path("connectorInterface").hasNonNull("uuid")) {
                String connector = id(instance
                        .path("connectorUuid")
                        .asString(instance.path("connector").path("uuid").asString(null)));
                context = new ConfigurationAttributes.ConnectorContext(connector, FunctionGroupCode.AUTHORITY_PROVIDER,
                        required(instance.path("kind").asString(null), "Authority kind"));
            }
        }
        return existing == null
                ? ConfigurationAttributes.resolve(client, context, definitions, values)
                : ConfigurationAttributes.resolveReplacement(client, context, definitions, array(existing), values);
    }

    ArrayNode operationAttributes(String authority, String profile, String path, String operation, JsonNode existing,
            JsonNode values) {
        String input = values == null || values.isMissingNode() ? "{}" : values.toString();
        JsonNode definitions = client.get(path + "/attributes/{operation}", authority, profile, operation);
        return ConfigurationAttributes
                .replace(resolveOperation(authority, profile, definitions, existing, input), array(existing), input);
    }

    ArrayNode custom(String resource, JsonNode existing, JsonNode values) {
        return ConfigurationAttributes
                .replace(client.get("/v1/attributes/custom/resources/{resource}", resource), array(existing),
                        values == null || values.isMissingNode() ? "{}" : values.toString());
    }

    static String protocol(String protocol) {
        if (!Set.of("acme", "scep", "cmp").contains(protocol)) {
            throw new IllegalArgumentException("Enrollment protocol must be acme, scep or cmp.");
        }
        return protocol;
    }

    static void uuids(ObjectNode input, String... fields) {
        for (String field : fields) {
            if (input.hasNonNull(field)) {
                id(input.path(field).asString(null));
            }
        }
    }

    static void positive(JsonNode input, String... fields) {
        for (String field : fields) {
            if (input.hasNonNull(field) && (!input.path(field).isInt() || input.path(field).asInt() < 1)) {
                throw new IllegalArgumentException(field + " must be a positive integer.");
            }
        }
    }

    static void file(JsonNode value) {
        if (!value.isString() || value.asString().length() > 6990508) {
            throw new IllegalArgumentException("file must be base64, at most 5 MiB decoded.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.asString());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("file must be valid base64.");
        }
        boolean valid = bytes.length > 0 && bytes.length <= 5 * 1024 * 1024;
        java.util.Arrays.fill(bytes, (byte) 0);
        if (!valid) {
            throw new IllegalArgumentException("file must contain between 1 byte and 5 MiB.");
        }
    }
}
