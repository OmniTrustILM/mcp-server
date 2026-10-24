package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.logging.enums.AuditLogOutput;
import com.otilm.api.model.core.logging.enums.Module;
import com.otilm.api.model.core.other.ResourceEvent;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** The published settings fields and their wire types, independent of the interfaces' Jackson 2 serializers. */
final class SettingsFields {
    private static final String CBOM_SYNC_PAGE_SIZE = "cbomSyncPageSize";
    private static final String CBOM_SYNC_SKIP_RETENTION_DAYS = "cbomSyncSkipRetentionDays";
    private static final String CERTIFICATES = "certificates";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String CONTENT = "content";
    private static final String CONTENT_TYPE = "contentType";
    private static final String DISABLE_LOCALHOST_USER = "disableLocalhostUser";
    private static final String EVENTS_MAPPING = "eventsMapping";
    private static final String EXTERNAL_CSR_VALIDATION_STRICT = "externalCsrValidationStrict";
    private static final String OAUTH2_PROVIDERS = "oauth2Providers";
    private static final String REQUEST_ATTRIBUTES = "requestAttributes";
    private static final String UTILS_2 = "utils";
    private static final String VERSION = "version";

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Set<String> UTILS = Set
            .of("utilsServiceUrl", "cbomRepositoryUrl", "cbomSyncOverlapSeconds", "cbomSyncSkippedRetryRuns",
                    "cbomSyncMaxIngestDocuments", CBOM_SYNC_SKIP_RETENTION_DAYS, CBOM_SYNC_PAGE_SIZE,
                    "cbomSyncAssetIngestEnabled", "cbomSyncAssetBatchSize", "cbomSyncIngestRetryAfterSeconds");
    static final Set<String> VALIDATION = Set.of("enabled", "frequency", "expiringThreshold");
    static final Set<String> REQUEST = Set.of(REQUEST_ATTRIBUTES, EXTERNAL_CSR_VALIDATION_STRICT);
    static final Set<String> REGISTRATION = Set.of("defaultIssuanceWindowDays", "maxFailedAttempts");
    static final Set<String> OAUTH = Set
            .of("issuerUrl", "clientId", CLIENT_SECRET, "authorizationUrl", "tokenUrl", "jwkSetUrl", "jwkSet", "scope",
                    "logoutUrl", "postLogoutUrl", "userInfoUrl", "audiences", "skew", "sessionMaxInactiveInterval",
                    "usernameClaim");
    static final Set<String> LOG = Set
            .of("logAllModules", "logAllResources", "loggedModules", "ignoredModules", "loggedResources",
                    "ignoredResources");
    static final Set<String> AUDIT = Set
            .of("logAllModules", "logAllResources", "loggedModules", "ignoredModules", "loggedResources",
                    "ignoredResources", "output", "verbose");
    private static final Set<String> DEFINITIONS = Set
            .of("uuid", "name", "description", VERSION, "schemaVersion", "type", CONTENT, CONTENT_TYPE, "properties",
                    "constraints", "attributeCallback", "fieldMapping", "valueSource");

    private SettingsFields() {
    }

    static ObjectNode object() {
        return JSON.createObjectNode();
    }

    static ObjectNode fields(JsonNode source, Set<String> allowed) {
        ObjectNode out = object();
        for (String key : allowed) {
            if (source != null && source.has(key)) {
                out.set(key, source.get(key).deepCopy());
            }
        }
        return out;
    }

    static ObjectNode metadata(JsonNode source, Set<String> allowed) {
        ObjectNode out = object();
        for (String key : allowed) {
            JsonNode value = source == null ? null : source.get(key);
            if (value == null) {
                continue;
            }
            if (value.isValueNode()) {
                out.set(key, value);
            } else if (value.isArray()) {
                ArrayNode items = JSON.createArrayNode();
                for (JsonNode item : value) {
                    if (item.isValueNode()) {
                        items.add(item);
                    }
                }
                out.set(key, items);
            }
        }
        return out;
    }

    static ObjectNode eventMetadata(JsonNode source) {
        ObjectNode mappings = object();
        for (ResourceEvent event : ResourceEvent.values()) {
            JsonNode values = source.path(EVENTS_MAPPING).path(event.getCode());
            if (!values.isArray()) {
                continue;
            }
            ArrayNode identifiers = JSON.createArrayNode();
            for (JsonNode id : values) {
                if (id.isString() && Uuids.valid(id.asString())) {
                    identifiers.add(id);
                }
            }
            mappings.set(event.getCode(), identifiers);
        }
        ObjectNode out = object();
        out.set(EVENTS_MAPPING, mappings);
        return out;
    }

    static ObjectNode merge(ObjectNode base, ObjectNode patch) {
        for (var property : patch.properties()) {
            String key = property.getKey();
            JsonNode value = property.getValue();
            if (value.isObject() && base.path(key).isObject()) {
                base.set(key, merge((ObjectNode) base.get(key), (ObjectNode) value));
            } else {
                base.set(key, value.deepCopy());
            }
        }
        return base;
    }

    static void validate(String section, JsonNode node) {
        requireObject(node);
        switch (section) {
            case "platform" -> validatePlatform(node);
            case UTILS_2, "cbom" -> validateUtils(section, node);
            case "validation" -> validateCertificates(node);
            case "registration" -> {
                allowed(node, REGISTRATION);
                for (JsonNode value : node) {
                    range(value, 1, Integer.MAX_VALUE, true);
                }
            }
            case REQUEST_ATTRIBUTES -> validateRequestAttributes(node);
            case "authentication" -> validateAuthentication(node);
            case "logging" -> {
                allowed(node, Set.of("auditLogs", "eventLogs"));
                for (var group : node.properties()) {
                    logging(group.getValue(), group.getKey().equals("auditLogs"));
                }
            }
            case "events" -> validateEvents(node);
            default -> throw new IllegalArgumentException("Unsupported settings section.");
        }
    }

    private static void validatePlatform(JsonNode node) {
        allowed(node, Set.of(UTILS_2, CERTIFICATES));
        if (node.has(UTILS_2)) {
            validate(UTILS_2, node.get(UTILS_2));
        }
        if (node.has(CERTIFICATES)) {
            JsonNode certificates = node.get(CERTIFICATES);
            allowed(certificates, Set.of("validation", REQUEST_ATTRIBUTES, "registration"));
            for (var entry : certificates.properties()) {
                validate(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void validateUtils(String section, JsonNode node) {
        allowed(node, UTILS);
        for (var entry : node.properties()) {
            String key = entry.getKey();
            if (section.equals("cbom") && !key.startsWith("cbomSync")) {
                invalid();
            }
            JsonNode value = entry.getValue();
            if (key.endsWith("Url")) {
                url(value);
            } else if (key.equals("cbomSyncAssetIngestEnabled")) {
                bool(value, true);
            } else {
                range(value, minimum(key), maximum(key), true);
            }
        }
    }

    private static void validateCertificates(JsonNode node) {
        allowed(node, VALIDATION);
        for (var entry : node.properties()) {
            if (entry.getKey().equals("enabled")) {
                bool(entry.getValue(), false);
            } else {
                range(entry.getValue(), 1, Integer.MAX_VALUE, true);
            }
        }
    }

    private static void validateRequestAttributes(JsonNode node) {
        allowed(node, REQUEST);
        if (node.has(EXTERNAL_CSR_VALIDATION_STRICT)) {
            bool(node.get(EXTERNAL_CSR_VALIDATION_STRICT), true);
        }
        if (node.has(REQUEST_ATTRIBUTES)) {
            definitions(node.get(REQUEST_ATTRIBUTES));
        }
    }

    private static void validateAuthentication(JsonNode node) {
        allowed(node, Set.of(DISABLE_LOCALHOST_USER, OAUTH2_PROVIDERS));
        if (node.has(DISABLE_LOCALHOST_USER)) {
            bool(node.get(DISABLE_LOCALHOST_USER), false);
        }
        if (node.has(OAUTH2_PROVIDERS)) {
            validateProviders(node.get(OAUTH2_PROVIDERS));
        }
    }

    private static void validateProviders(JsonNode providers) {
        if (!providers.isArray()) {
            invalid();
        }
        Set<String> names = new HashSet<>();
        Set<String> fields = new HashSet<>(OAUTH);
        fields.add("name");
        for (JsonNode provider : providers) {
            allowed(provider, fields);
            if (!names.add(providerName(provider.path("name").asString()))) {
                invalid();
            }
            ObjectNode config = fields(provider, OAUTH);
            validateOAuth(config);
            fresh(provider.get(CLIENT_SECRET));
            completeOAuth(config);
        }
    }

    private static void validateEvents(JsonNode node) {
        allowed(node, Set.of(EVENTS_MAPPING));
        if (!node.has(EVENTS_MAPPING)) {
            return;
        }
        JsonNode mapping = node.get(EVENTS_MAPPING);
        requireObject(mapping);
        for (var entry : mapping.properties()) {
            if (Arrays.stream(ResourceEvent.values()).noneMatch(event -> event.getCode().equals(entry.getKey()))) {
                invalid();
            }
            JsonNode identifiers = entry.getValue();
            if (!identifiers.isArray()) {
                invalid();
            }
            for (JsonNode id : identifiers) {
                ConfigurationSupport.uuid(id.asString(), "Trigger UUID");
            }
        }
    }

    static void validateOAuth(JsonNode node) {
        allowed(node, OAUTH);
        for (var entry : node.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key.equals(CLIENT_SECRET)) {
                fresh(value);
            } else if (key.endsWith("Url")) {
                url(value);
            } else if (key.equals("scope") || key.equals("audiences")) {
                stringArray(value);
            } else if (key.equals("skew") || key.equals("sessionMaxInactiveInterval")) {
                range(value, 0, Integer.MAX_VALUE, false);
            } else if (!value.isString() && !value.isNull()) {
                invalid();
            }
        }
    }

    static void completeOAuth(JsonNode node) {
        JsonNode url = node.path("jwkSetUrl");
        JsonNode encoded = node.path("jwkSet");
        if (url.isString() && !url.asString().isBlank()) {
            return;
        }
        if (!encoded.isString() || encoded.asString().isBlank()) {
            throw new IllegalArgumentException("OAuth provider requires a JWK Set URL or an encoded JWK Set.");
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(encoded.asString());
            if (bytes.length > 1024 * 1024) {
                throw new IllegalArgumentException();
            }
            JsonNode set = JSON.readTree(bytes);
            if (set == null || !set.path("keys").isArray()) {
                throw new IllegalArgumentException();
            }
            for (JsonNode key : set.get("keys")) {
                if (!Set.of("RSA", "EC", "OKP").contains(key.path("kty").asString())) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "Encoded JWK Set must be valid base64 JSON containing public keys, at most 1 MiB.");
        }
    }

    static ObjectNode display(ObjectNode node) {
        for (var entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                display((ObjectNode) value);
            } else if (entry.getKey().endsWith("Url") && value.isString()) {
                try {
                    URI uri = URI.create(value.asString());
                    if (uri.getHost() == null || uri.getScheme() == null) {
                        throw new IllegalArgumentException();
                    }
                    node
                            .put(entry.getKey(), new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                                    uri.getPath(), null, null).toASCIIString());
                } catch (Exception invalid) {
                    node.put(entry.getKey(), "[URL omitted]");
                }
            }
        }
        return node;
    }

    static String providerName(String value) {
        String name = ConfigurationSupport.required(value, "OAuth provider name");
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IllegalArgumentException(
                    "OAuth provider name must contain only letters, digits, dots, underscores or hyphens.");
        }
        return name;
    }

    static void fresh(JsonNode secret) {
        if (secret == null || !secret.isString() || secret.asString().isBlank()
                || secret.asString().matches("(?i)([*•]+|<redacted>|\\[redacted\\]|redacted|masked)")) {
            throw new IllegalArgumentException(
                    "Supply a fresh client secret; masked or missing credentials cannot replace a provider.");
        }
    }

    static void allowed(JsonNode node, Set<String> fields) {
        requireObject(node);
        for (var entry : node.properties()) {
            if (!fields.contains(entry.getKey())) {
                invalid();
            }
        }
    }

    private static void logging(JsonNode group, boolean audit) {
        allowed(group, audit ? AUDIT : LOG);
        for (var entry : group.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key.equals("output")) {
                if (!value.isString() || Arrays
                        .stream(AuditLogOutput.values())
                        .noneMatch(output -> output.name().equals(value.asString()))) {
                    invalid();
                }
            } else if (key.startsWith("logAll") || key.equals("verbose")) {
                bool(value, false);
            } else {
                loggingCodes(key, value);
            }
        }
    }

    private static void loggingCodes(String key, JsonNode values) {
        stringArray(values);
        for (JsonNode item : values) {
            boolean valid = key.endsWith("Modules")
                    ? Arrays.stream(Module.values()).anyMatch(module -> module.getCode().equals(item.asString()))
                    : Arrays.stream(Resource.values()).anyMatch(resource -> resource.getCode().equals(item.asString()));
            if (!valid) {
                invalid();
            }
        }
    }

    private static void definitions(JsonNode definitions) {
        if (!definitions.isArray()) {
            invalid();
        }
        Set<String> names = new HashSet<>();
        for (JsonNode definition : definitions) {
            allowed(definition, DEFINITIONS);
            if (definition.path(VERSION).asInt(3) != 3 || !definition.path("type").asString("data").equals("data")) {
                invalid();
            }
            String name = ConfigurationSupport.required(definition.path("name").asString(), "Request attribute name");
            if (!names.add(name)) {
                invalid();
            }
            if (!Set.of("string", "text").contains(definition.path(CONTENT_TYPE).asString())) {
                invalid();
            }
            JsonNode mapping = definition.path("fieldMapping");
            if (!mapping.isObject() || !mapping.path("fields").isArray() || mapping.path("fields").isEmpty()
                    || !mapping.path("objectType").isString()) {
                invalid();
            }
            if (definition.has(CONTENT) && !definition.get(CONTENT).isArray() && !definition.get(CONTENT).isNull()) {
                invalid();
            }
        }
    }

    static ArrayNode definitionMetadata(JsonNode definitions) {
        ArrayNode safe = JSON.createArrayNode();
        if (definitions.isArray()) {
            for (JsonNode definition : definitions) {
                safe
                        .add(metadata(definition,
                                Set.of("uuid", "name", "description", VERSION, "schemaVersion", "type", CONTENT_TYPE)));
            }
        }
        return safe;
    }

    private static int minimum(String key) {
        return Set.of(CBOM_SYNC_SKIP_RETENTION_DAYS, CBOM_SYNC_PAGE_SIZE, "cbomSyncAssetBatchSize").contains(key)
                ? 1
                : 0;
    }

    private static int maximum(String key) {
        return switch (key) {
            case "cbomSyncOverlapSeconds", "cbomSyncIngestRetryAfterSeconds" -> 86400;
            case "cbomSyncSkippedRetryRuns", CBOM_SYNC_PAGE_SIZE -> 1000;
            case CBOM_SYNC_SKIP_RETENTION_DAYS -> 3650;
            default -> 10000;
        };
    }

    private static void url(JsonNode value) {
        if (value.isNull()) {
            return;
        }
        if (!value.isString()) {
            invalid();
        }
        try {
            URI uri = URI.create(value.asString());
            if (!(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() > 65535
                    || uri.getPort() == 0) {
                invalid();
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "Settings URLs must be valid HTTP or HTTPS URLs without embedded credentials.");
        }
    }

    private static void stringArray(JsonNode value) {
        if (!value.isArray()) {
            invalid();
        }
        for (JsonNode item : value) {
            if (!item.isString()) {
                invalid();
            }
        }
    }

    private static void bool(JsonNode value, boolean nullable) {
        if (!value.isBoolean() && !(nullable && value.isNull())) {
            invalid();
        }
    }

    private static void range(JsonNode value, int min, int max, boolean nullable) {
        if (nullable && value.isNull()) {
            return;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < min || value.asInt() > max) {
            invalid();
        }
    }

    private static void requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            invalid();
        }
    }

    private static void invalid() {
        throw new IllegalArgumentException(
                "Unsupported settings field or invalid field type/value. Use the section's documented schema.");
    }
}
