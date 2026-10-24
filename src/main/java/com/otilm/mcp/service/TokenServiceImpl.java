package com.otilm.mcp.service;

import com.otilm.api.model.client.cryptography.key.KeyRequestType;
import com.otilm.api.model.common.enums.cryptography.KeyAlgorithm;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TokenServiceImpl implements TokenService {
    private static final String ATTRIBUTES = "attributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String ENABLED = "enabled";
    private static final String USAGE = "usage";
    private static final String USAGES = "usages";
    private static final Set<String> KEY_ALGORITHM_CODES = Arrays
            .stream(KeyAlgorithm.values())
            .map(KeyAlgorithm::getCode)
            .collect(Collectors.toUnmodifiableSet());

    private static final String TOKEN = "/v1/tokens/{uuid}";
    private static final String PROFILE = "/v1/tokens/{tokenUuid}/tokenProfiles/{profileUuid}";
    private static final String PROFILE_ATTRIBUTES = "/v1/tokens/{uuid}/tokenProfiles/attributes";
    private static final String ASSOCIATION = "/v2/complianceProfiles/{complianceUuid}/associations/tokenProfiles/{profileUuid}";
    private static final String[] TOKEN_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            CONNECTOR_UUID,
            "connectorName",
            "kind",
            "status.status",
            "tokenProfiles"};
    private static final String[] PROFILE_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "tokenInstanceUuid",
            "tokenInstanceName",
            "tokenInstanceStatus",
            ENABLED,
            USAGES};
    private final ConfigurationApiClient client;
    private final PlatformVersionCheck versions;

    public TokenServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    @Override
    public String getTokenInstance(String uuid) {
        return ConfigurationSupport
                .run("retrieving token instance", () -> ConfigurationSupport
                        .describe("Token instance", client.get(TOKEN, CryptoInput.uuid(uuid)), TOKEN_FIELDS));
    }

    @Override
    public String getTokenInstanceAttributes(String connectorUuid, String kind, String values) {
        return ConfigurationSupport.run("retrieving token instance settings", () -> {
            if (kind == null || kind.isBlank()) {
                String unsupported = versions.unsupported(IlmFeature.TOKEN_PROVIDER_ATTRIBUTES).orElse(null);
                if (unsupported != null) {
                    return unsupported;
                }
            }
            JsonNode definitions = instanceDefinitions(connectorUuid, kind);
            return ConfigurationAttributes
                    .settings("Token instance settings", resolveInstance(connectorUuid, kind, definitions, values));
        });
    }

    private JsonNode instanceDefinitions(String connectorUuid, String kind) {
        CryptoInput.uuid(connectorUuid);
        if (kind == null || kind.isBlank()) {
            requireModern();
            return client.get("/v1/tokens/{connectorUuid}/attributes", connectorUuid);
        }
        return client.get("/v1/connectors/{connectorUuid}/attributes/cryptographyProvider/{kind}", connectorUuid, kind);
    }

    private JsonNode resolveInstance(String connectorUuid, String kind, JsonNode definitions, String values) {
        return resolveInstance(connectorUuid, kind, definitions, values, null);
    }

    private JsonNode resolveInstance(String connectorUuid, String kind, JsonNode definitions, String values,
            JsonNode existing) {
        boolean callbacks = false;
        for (JsonNode definition : definitions) {
            callbacks |= definition.path("attributeCallback").isObject();
        }
        if (!callbacks) {
            return definitions;
        }
        ConfigurationAttributes.CallbackContext context;
        if (kind != null && !kind.isBlank()) {
            context = new ConfigurationAttributes.ConnectorContext(connectorUuid,
                    FunctionGroupCode.CRYPTOGRAPHY_PROVIDER, kind);
        } else {
            JsonNode connector = client.get("/v2/connectors/{uuid}", CryptoInput.uuid(connectorUuid));
            String interfaceUuid = null;
            for (JsonNode provider : connector.path("interfaces")) {
                if ("cryptography".equals(provider.path("code").asString())) {
                    if (interfaceUuid != null) {
                        throw new IllegalArgumentException("Connector has ambiguous cryptography interfaces.");
                    }
                    interfaceUuid = provider.path("uuid").asString(null);
                }
            }
            context = new ConfigurationAttributes.ConnectorInterfaceContext(connectorUuid, interfaceUuid);
        }
        return existing == null
                ? ConfigurationAttributes.resolve(client, context, definitions, values)
                : ConfigurationAttributes.resolveReplacement(client, context, definitions, existing, values);
    }

    private void validateConnector(String connectorUuid, String kind) {
        JsonNode connector = client.get("/v1/connectors/{uuid}", CryptoInput.uuid(connectorUuid));
        if (kind == null || kind.isBlank()) {
            requireModern();
            return;
        }
        boolean offered = false;
        for (JsonNode group : connector.path("functionGroups")) {
            if ("cryptographyProvider".equals(group.path("functionGroupCode").asString())) {
                for (JsonNode candidate : group.path("kinds")) {
                    offered |= kind.equals(candidate.asString());
                }
            }
        }
        if (!offered) {
            throw new IllegalArgumentException("Connector does not offer the requested cryptography kind.");
        }
    }

    @Override
    public String createTokenInstance(String configuration) {
        return ConfigurationSupport.run("creating token instance", () -> {
            ObjectNode body = instanceBody(configuration);
            finishInstance(body, ConfigurationAttributes.JSON.createObjectNode());
            return ConfigurationSupport
                    .describe("Token instance created", client.post("/v1/tokens", body), TOKEN_FIELDS);
        });
    }

    @Override
    public String updateTokenInstance(String uuid, String configuration) {
        return ConfigurationSupport.run("updating token instance", () -> {
            ObjectNode body = instanceBody(configuration);
            JsonNode existing = client.get(TOKEN, CryptoInput.uuid(uuid));
            CryptoInput.preserve(body, existing, "name", DESCRIPTION, CONNECTOR_UUID, "kind");
            finishInstance(body, existing);
            return ConfigurationSupport.describe("Token instance updated", client.put(TOKEN, body, uuid), TOKEN_FIELDS);
        });
    }

    private ObjectNode instanceBody(String configuration) {
        ObjectNode body = CryptoInput
                .configuration(configuration, "name", DESCRIPTION, CONNECTOR_UUID, "kind", ATTRIBUTES,
                        CUSTOM_ATTRIBUTES);
        CryptoInput.text(body, "name", DESCRIPTION, CONNECTOR_UUID, "kind");
        return body;
    }

    private void finishInstance(ObjectNode body, JsonNode existing) {
        body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Token name"));
        String connector = CryptoInput.uuid(body.path(CONNECTOR_UUID).asString(null));
        String kind = body.path("kind").asString(null);
        validateConnector(connector, kind);
        JsonNode definitions = instanceDefinitions(connector, kind);
        JsonNode expanded = resolveInstance(connector, kind, definitions, CryptoInput.values(body, ATTRIBUTES),
                previous(existing, ATTRIBUTES));
        body
                .set(ATTRIBUTES, ConfigurationAttributes
                        .replace(expanded, previous(existing, ATTRIBUTES), CryptoInput.values(body, ATTRIBUTES)));
        body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "tokens", existing, body));
    }

    @Override
    public String deleteTokenInstance(String uuid) {
        return ConfigurationSupport.run("deleting token instance", () -> {
            client.delete(TOKEN, CryptoInput.uuid(uuid));
            return "Deleted token instance [" + uuid + "]. Provider dependencies may prevent deletion.";
        });
    }

    @Override
    public String getTokenActivationAttributes(String uuid, String values) {
        return ConfigurationSupport
                .run("retrieving token activation settings", () -> ConfigurationAttributes
                        .settings("Token activation settings", activationDefinitions(uuid, values)));
    }

    private JsonNode activationDefinitions(String uuid, String values) {
        JsonNode definitions = client.get(TOKEN + "/activate/attributes", CryptoInput.uuid(uuid));
        JsonNode token = client.get(TOKEN, uuid);
        return resolveInstance(token.path(CONNECTOR_UUID).asString(), token.path("kind").asString(null), definitions,
                values);
    }

    @Override
    public String activateTokenInstance(String uuid, String attributes) {
        return ConfigurationSupport.run("activating token instance", () -> {
            ArrayNode body = ConfigurationAttributes.encode(activationDefinitions(uuid, attributes), attributes);
            client.patch(TOKEN + "/activate", body, uuid);
            return "Token activation request accepted [" + uuid + "]; refresh_token_status reports provider state.";
        });
    }

    @Override
    public String deactivateTokenInstance(String uuid) {
        return tokenOperation(uuid, "/deactivate", "deactivation");
    }

    @Override
    public String syncTokenKeys(String uuid) {
        return tokenOperation(uuid, "/sync", "key synchronization");
    }

    private String tokenOperation(String uuid, String suffix, String operation) {
        return ConfigurationSupport.run(operation, () -> {
            client.patch(TOKEN + suffix, null, CryptoInput.uuid(uuid));
            return "Token " + operation + " request accepted [" + uuid + "].";
        });
    }

    @Override
    public String refreshTokenStatus(String uuid) {
        return ConfigurationSupport
                .run("refreshing token status",
                        () -> ConfigurationSupport
                                .describe("Refreshed token status", client.patch(TOKEN, null, CryptoInput.uuid(uuid)),
                                        TOKEN_FIELDS));
    }

    @Override
    public String listTokenProfiles() {
        return ConfigurationSupport
                .run("listing token profiles", () -> ConfigurationSupport
                        .list("Token profiles", client.get("/v1/tokenProfiles"), PROFILE_FIELDS));
    }

    @Override
    public String getTokenProfile(String tokenUuid, String profileUuid) {
        return ConfigurationSupport
                .run("retrieving token profile",
                        () -> ConfigurationSupport
                                .describe("Token profile",
                                        client.get(PROFILE, CryptoInput.uuid(tokenUuid), CryptoInput.uuid(profileUuid)),
                                        PROFILE_FIELDS));
    }

    @Override
    public String getTokenProfileAttributes(String tokenUuid, String values) {
        return ConfigurationSupport
                .run("retrieving token profile settings", () -> ConfigurationAttributes
                        .settings("Token profile settings", profileDefinitions(tokenUuid, values)));
    }

    private JsonNode profileDefinitions(String tokenUuid, String values) {
        JsonNode definitions = client.get(PROFILE_ATTRIBUTES, CryptoInput.uuid(tokenUuid));
        return ConfigurationAttributes.resolve(client, profileCallbackContext(tokenUuid), definitions, values);
    }

    private ConfigurationAttributes.CallbackContext profileCallbackContext(String tokenUuid) {
        if (versions.unsupported(IlmFeature.TOKEN_PROFILE_CALLBACKS).isPresent()) {
            JsonNode token = client.get(TOKEN, CryptoInput.uuid(tokenUuid));
            String connector = CryptoInput.uuid(token.path(CONNECTOR_UUID).asString(null));
            String kind = ConfigurationSupport.required(token.path("kind").asString(null), "Token kind");
            return new ConfigurationAttributes.ConnectorContext(connector, FunctionGroupCode.CRYPTOGRAPHY_PROVIDER,
                    kind);
        }
        return new ConfigurationAttributes.ResourceContext(Resource.TOKEN_PROFILE, tokenUuid);
    }

    @Override
    public String createTokenProfile(String tokenUuid, String configuration) {
        return ConfigurationSupport.run("creating token profile", () -> {
            CryptoInput.uuid(tokenUuid);
            ObjectNode body = profileBody(configuration, true);
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Profile name"));
            finishProfile(body, ConfigurationAttributes.JSON.createObjectNode(), tokenUuid);
            return ConfigurationSupport
                    .describe("Token profile created", client.post("/v1/tokens/{uuid}/tokenProfiles", body, tokenUuid),
                            PROFILE_FIELDS);
        });
    }

    @Override
    public String updateTokenProfile(String tokenUuid, String profileUuid, String configuration) {
        return ConfigurationSupport.run("updating token profile", () -> {
            CryptoInput.uuid(tokenUuid);
            CryptoInput.uuid(profileUuid);
            ObjectNode body = profileBody(configuration, false);
            JsonNode existing = client.get(PROFILE, tokenUuid, profileUuid);
            CryptoInput.preserve(body, existing, DESCRIPTION, ENABLED);
            if (!body.has(USAGE) && existing.path(USAGES).isArray()) {
                body.set(USAGE, existing.path(USAGES));
            }
            finishProfile(body, existing, tokenUuid);
            return ConfigurationSupport
                    .describe("Token profile updated", client.put(PROFILE, body, tokenUuid, profileUuid),
                            PROFILE_FIELDS);
        });
    }

    private ObjectNode profileBody(String configuration, boolean create) {
        ObjectNode body = create
                ? CryptoInput
                        .configuration(configuration, "name", DESCRIPTION, ENABLED, USAGE, ATTRIBUTES,
                                CUSTOM_ATTRIBUTES)
                : CryptoInput.configuration(configuration, DESCRIPTION, ENABLED, USAGE, ATTRIBUTES, CUSTOM_ATTRIBUTES);
        CryptoInput.text(body, "name", DESCRIPTION);
        CryptoInput.bool(body, ENABLED);
        if (body.has(USAGE)) {
            body.set(USAGE, CryptoInput.usages(body.path(USAGE).toString()));
        }
        return body;
    }

    private void finishProfile(ObjectNode body, JsonNode existing, String tokenUuid) {
        JsonNode definitions = client.get(PROFILE_ATTRIBUTES, tokenUuid);
        String values = CryptoInput.values(body, ATTRIBUTES);
        JsonNode expanded = ConfigurationAttributes
                .resolveReplacement(client, profileCallbackContext(tokenUuid), definitions,
                        previous(existing, ATTRIBUTES), values);
        body.set(ATTRIBUTES, ConfigurationAttributes.replace(expanded, previous(existing, ATTRIBUTES), values));
        body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "tokenProfiles", existing, body));
    }

    @Override
    public String deleteTokenProfile(String profileUuid) {
        return ConfigurationSupport.run("deleting token profile", () -> {
            client.delete("/v1/tokenProfiles/{uuid}", CryptoInput.uuid(profileUuid));
            return "Deleted token profile [" + profileUuid + "].";
        });
    }

    @Override
    public String enableTokenProfile(String tokenUuid, String profileUuid) {
        return profileOperation(tokenUuid, profileUuid, "enable");
    }

    @Override
    public String disableTokenProfile(String tokenUuid, String profileUuid) {
        return profileOperation(tokenUuid, profileUuid, "disable");
    }

    private String profileOperation(String tokenUuid, String profileUuid, String operation) {
        return ConfigurationSupport.run(operation + " token profile", () -> {
            client.patch(PROFILE + "/" + operation, null, CryptoInput.uuid(tokenUuid), CryptoInput.uuid(profileUuid));
            return "Token profile " + operation + " request accepted [" + profileUuid + "].";
        });
    }

    @Override
    public String updateTokenProfileUsages(String tokenUuid, String profileUuid, String usages) {
        return ConfigurationSupport.run("updating token profile usages", () -> {
            ObjectNode body = ConfigurationAttributes.JSON.createObjectNode();
            body.set(USAGE, CryptoInput.usages(usages));
            client.put(PROFILE + "/usages", body, CryptoInput.uuid(tokenUuid), CryptoInput.uuid(profileUuid));
            return "Token profile usage replacement accepted [" + profileUuid + "].";
        });
    }

    @Override
    public String getSupportedKeyTypes(String tokenUuid, String profileUuid) {
        return modern("retrieving supported key types", () -> {
            JsonNode types = client
                    .get(PROFILE + "/keys/types", CryptoInput.uuid(tokenUuid), CryptoInput.uuid(profileUuid));
            StringBuilder output = new StringBuilder("Supported key request types:\n");
            for (JsonNode type : types) {
                output.append("- ").append(CryptoInput.type(type.asString())).append('\n');
            }
            return output.toString();
        });
    }

    @Override
    public String getTokenProfileCapabilities(String tokenUuid, String profileUuid) {
        return modern("retrieving token profile capabilities",
                () -> capabilities(client.get(PROFILE, CryptoInput.uuid(tokenUuid), CryptoInput.uuid(profileUuid))));
    }

    private static String capabilities(JsonNode profile) {
        StringBuilder output = new StringBuilder(ConfigurationSupport
                .describe("Token profile capabilities", profile, "uuid", "name", USAGES, "keyTransfer.importAvailable",
                        "keyTransfer.exportAvailable"));
        appendKeyTypes(output, profile, "importableKeyTypes");
        appendKeyTypes(output, profile, "exportableKeyTypes");
        return output.toString();
    }

    private static void appendKeyTypes(StringBuilder output, JsonNode profile, String direction) {
        JsonNode types = profile.path("keyTransfer").path(direction);
        for (KeyRequestType type : KeyRequestType.values()) {
            JsonNode supported = types.path(type.getCode());
            if (supported.isArray()) {
                String algorithms = StreamSupport
                        .stream(supported.spliterator(), false)
                        .filter(JsonNode::isString)
                        .map(JsonNode::asString)
                        .filter(KEY_ALGORITHM_CODES::contains)
                        .distinct()
                        .collect(Collectors.joining(", "));
                TextFormat.field(output, "", "keyTransfer." + direction + "." + type.getCode(), algorithms);
            }
        }
    }

    @Override
    public String getTokenProfileCompliance(String profileUuid) {
        return ConfigurationSupport
                .run("retrieving token profile compliance associations",
                        () -> ConfigurationSupport
                                .list("Token profile compliance profiles",
                                        client
                                                .get("/v2/complianceProfiles/associations/tokenProfiles/{uuid}",
                                                        CryptoInput.uuid(profileUuid)),
                                        "uuid", "name", DESCRIPTION));
    }

    @Override
    public String associateTokenProfileCompliance(String profileUuid, String complianceUuid) {
        return association(profileUuid, complianceUuid, false);
    }

    @Override
    public String detachTokenProfileCompliance(String profileUuid, String complianceUuid) {
        return association(profileUuid, complianceUuid, true);
    }

    private String association(String profileUuid, String complianceUuid, boolean remove) {
        return ConfigurationSupport.run("changing token profile compliance association", () -> {
            CryptoInput.uuid(profileUuid);
            CryptoInput.uuid(complianceUuid);
            if (remove) {
                client.delete(ASSOCIATION, complianceUuid, profileUuid);
            } else {
                client.patch(ASSOCIATION, null, complianceUuid, profileUuid);
            }
            return "Token profile compliance association change accepted [" + profileUuid + "].";
        });
    }

    private JsonNode previous(JsonNode existing, String field) {
        return existing.path(field).isArray() ? existing.path(field) : ConfigurationAttributes.JSON.createArrayNode();
    }

    private void requireModern() {
        versions.unsupported(IlmFeature.TOKEN_PROVIDER_ATTRIBUTES).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private String modern(String action, Supplier<String> body) {
        return versions
                .unsupported(IlmFeature.TOKEN_PROVIDER_ATTRIBUTES)
                .orElseGet(() -> ConfigurationSupport.run(action, body));
    }
}
