package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class KeyManagementServiceImpl implements KeyManagementService {
    private static final String ATTRIBUTES = "attributes";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DELETE = "delete";
    private static final String DESCRIPTION = "description";
    private static final String ENABLED = "enabled";
    private static final String EXPORT = "export";
    private static final String EXPORTABLE = "exportable";
    private static final String GROUP_UUIDS = "groupUuids";
    private static final String IMPORT_ATTRIBUTES = "importAttributes";
    private static final String OWNER_UUID = "ownerUuid";
    private static final String TOKEN_PROFILE_UUID = "tokenProfileUuid";

    private static final String KEY = "/v1/keys/{uuid}";
    private static final String ITEM = KEY + "/items/{itemUuid}";
    private static final String PROFILE = "/v1/tokens/{tokenUuid}/tokenProfiles/{profileUuid}";
    private static final String CREATE = PROFILE + "/keys/{type}";
    private static final String[] FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "state",
            ENABLED,
            EXPORTABLE,
            "keyAlgorithm",
            "type",
            "format",
            "length",
            "usage",
            "tokenInstanceUuid",
            TOKEN_PROFILE_UUID};
    private final ConfigurationApiClient client;
    private final PlatformVersionCheck versions;

    public KeyManagementServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    @Override
    public String listKeyPairs(String tokenProfileUuid) {
        return ConfigurationSupport
                .run("listing key pairs",
                        () -> ConfigurationSupport
                                .list("Key pairs",
                                        tokenProfileUuid == null || tokenProfileUuid.isBlank()
                                                ? client.get("/v1/keys/pairs")
                                                : client
                                                        .get("/v1/keys/pairs?tokenProfileUuid={profileUuid}",
                                                                CryptoInput.uuid(tokenProfileUuid)),
                                        FIELDS));
    }

    @Override
    public String getKeyItem(String uuid, String itemUuid) {
        return ConfigurationSupport
                .run("retrieving key item",
                        () -> ConfigurationSupport
                                .describe("Key item",
                                        client.get(ITEM, CryptoInput.uuid(uuid), CryptoInput.uuid(itemUuid)), FIELDS));
    }

    @Override
    public String getKeyHistory(String uuid, String itemUuid) {
        return ConfigurationSupport
                .run("retrieving key history", () -> ConfigurationSupport
                        .list("Key item history",
                                client.get(ITEM + "/history", CryptoInput.uuid(uuid), CryptoInput.uuid(itemUuid)),
                                "uuid", "event", "status", "created", "time", "timestamp"));
    }

    @Override
    public String getKeyGenerationAttributes(String tokenUuid, String profileUuid, String type, String values) {
        return ConfigurationSupport
                .run("retrieving key generation settings",
                        () -> settings(tokenUuid, profileUuid, type, "/attributes", values));
    }

    @Override
    public String getKeyImportAttributes(String tokenUuid, String profileUuid, String type, String values) {
        return transfer("retrieving key import settings",
                () -> settings(tokenUuid, profileUuid, type, "/import/attributes", values));
    }

    private String settings(String tokenUuid, String profileUuid, String type, String suffix, String values) {
        JsonNode definitions = client
                .get(CREATE + suffix, CryptoInput.uuid(tokenUuid), CryptoInput.uuid(profileUuid),
                        CryptoInput.type(type));
        return ConfigurationAttributes
                .settings("Key settings", ConfigurationAttributes
                        .resolve(client,
                                new ConfigurationAttributes.ResourceContext(Resource.CRYPTOGRAPHIC_KEY, profileUuid),
                                definitions, values));
    }

    @Override
    public String getKeyExportAttributes(String uuid, String itemUuid, String values) {
        return transfer("retrieving key export settings", () -> ConfigurationAttributes
                .settings("Key export settings", exportDefinitions(uuid, itemUuid, values)));
    }

    private JsonNode exportDefinitions(String uuid, String itemUuid, String values) {
        JsonNode key = client.get(KEY, CryptoInput.uuid(uuid));
        JsonNode definitions = client.get(ITEM + "/export/attributes", uuid, CryptoInput.uuid(itemUuid));
        return ConfigurationAttributes
                .resolve(client, new ConfigurationAttributes.ResourceContext(Resource.CRYPTOGRAPHIC_KEY,
                        CryptoInput.uuid(key.path(TOKEN_PROFILE_UUID).asString())), definitions, values);
    }

    @Override
    public String generateKey(String tokenUuid, String profileUuid, String type, String configuration) {
        return ConfigurationSupport.run("generating key", () -> {
            CryptoInput.uuid(tokenUuid);
            CryptoInput.uuid(profileUuid);
            CryptoInput.type(type);
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", DESCRIPTION, GROUP_UUIDS, ATTRIBUTES, CUSTOM_ATTRIBUTES,
                            ENABLED, EXPORTABLE);
            metadata(body);
            if (body.path(EXPORTABLE).asBoolean(false)) {
                requireTransfer();
                capability(tokenUuid, profileUuid, EXPORT, type, null);
            } else {
                body.remove(EXPORTABLE);
            }
            JsonNode definitions = client.get(CREATE + "/attributes", tokenUuid, profileUuid, type);
            body
                    .set(ATTRIBUTES, ConfigurationAttributes
                            .encode(client, new ConfigurationAttributes.ResourceContext(Resource.CRYPTOGRAPHIC_KEY,
                                    profileUuid), definitions, CryptoInput.values(body, ATTRIBUTES)));
            body
                    .set(CUSTOM_ATTRIBUTES,
                            CryptoInput.custom(client, "keys", ConfigurationAttributes.JSON.createObjectNode(), body));
            return ConfigurationSupport
                    .describe("Key generation result", client.post(CREATE, body, tokenUuid, profileUuid, type), FIELDS);
        });
    }

    @Override
    public String importKey(String tokenUuid, String profileUuid, String type, String configuration) {
        return transfer("importing key", () -> {
            CryptoInput.uuid(tokenUuid);
            CryptoInput.uuid(profileUuid);
            CryptoInput.type(type);
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", DESCRIPTION, "file", "inputPassphrase", EXPORTABLE,
                            IMPORT_ATTRIBUTES, CUSTOM_ATTRIBUTES, GROUP_UUIDS);
            metadata(body);
            CryptoInput.text(body, "inputPassphrase");
            CryptoInput.file(body);
            capability(tokenUuid, profileUuid, "import", type, null);
            if (body.path(EXPORTABLE).asBoolean(false)) {
                capability(tokenUuid, profileUuid, EXPORT, type, null);
            }
            JsonNode definitions = client.get(CREATE + "/import/attributes", tokenUuid, profileUuid, type);
            body
                    .set(IMPORT_ATTRIBUTES,
                            ConfigurationAttributes
                                    .encode(client,
                                            new ConfigurationAttributes.ResourceContext(Resource.CRYPTOGRAPHIC_KEY,
                                                    profileUuid),
                                            definitions, CryptoInput.values(body, IMPORT_ATTRIBUTES)));
            body
                    .set(CUSTOM_ATTRIBUTES,
                            CryptoInput.custom(client, "keys", ConfigurationAttributes.JSON.createObjectNode(), body));
            return ConfigurationSupport
                    .describe("Key import result", client.post(CREATE + "/import", body, tokenUuid, profileUuid, type),
                            FIELDS);
        });
    }

    private void metadata(ObjectNode body) {
        CryptoInput.text(body, "name", DESCRIPTION);
        body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Key name"));
        CryptoInput.bool(body, ENABLED, EXPORTABLE);
        CryptoInput.groups(body);
    }

    @Override
    public String exportKey(String uuid, String itemUuid, String passphrase, String attributes) {
        return transfer("exporting key", () -> {
            validatePassphrase(passphrase);
            JsonNode key = client.get(KEY, CryptoInput.uuid(uuid));
            JsonNode item = client.get(ITEM, uuid, CryptoInput.uuid(itemUuid));
            if (!item.path(ENABLED).asBoolean(false) || !"active".equals(item.path("state").asString())) {
                throw new IllegalArgumentException("Key item must be active and enabled before export.");
            }
            if (!item.path(EXPORTABLE).asBoolean(false)) {
                throw new IllegalArgumentException("Key item is not exportable; this cannot be enabled later.");
            }
            String token = CryptoInput.uuid(key.path("tokenInstanceUuid").asString());
            String profile = CryptoInput.uuid(key.path(TOKEN_PROFILE_UUID).asString());
            String itemType = item.path("type").asString();
            if (!Set.of("Private", "Secret").contains(itemType)) {
                throw new IllegalArgumentException("Only private or secret key items can be exported.");
            }
            String type = "Secret".equals(itemType) ? "secret" : "keyPair";
            capability(token, profile, EXPORT, type, item.path("keyAlgorithm").asString());
            ObjectNode body = ConfigurationAttributes.JSON.createObjectNode();
            body.put("passphrase", passphrase);
            JsonNode definitions = client.get(ITEM + "/export/attributes", uuid, itemUuid);
            body
                    .set("exportAttributes", ConfigurationAttributes
                            .encode(client,
                                    new ConfigurationAttributes.ResourceContext(Resource.CRYPTOGRAPHIC_KEY, profile),
                                    definitions, attributes));
            ConfigurationApiClient.BinaryContent file = client.download(ITEM + "/export", body, uuid, itemUuid);
            String pem = new String(file.data(), StandardCharsets.UTF_8);
            if (!pem.startsWith("-----BEGIN ENCRYPTED PRIVATE KEY-----")
                    || !pem.contains("-----END ENCRYPTED PRIVATE KEY-----")) {
                throw new IllegalArgumentException("The provider did not return encrypted PKCS#8 PEM.");
            }
            return "Exported key item [" + itemUuid + "]\nFilename: " + file.filename() + "\nMedia type: "
                    + file.contentType() + "\nFormat: encrypted PKCS#8 PEM\n" + pem;
        });
    }

    private static void validatePassphrase(String passphrase) {
        if (passphrase == null || passphrase.isBlank() || passphrase.codePointCount(0, passphrase.length()) < 12) {
            throw new IllegalArgumentException("Export passphrase must contain at least 12 Unicode code points.");
        }
        if (!Normalizer.isNormalized(passphrase, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException("Export passphrase must already be in NFC normalization form.");
        }
    }

    private void capability(String token, String profile, String direction, String type, String algorithm) {
        JsonNode offered = client.get(PROFILE, token, profile).path("keyTransfer");
        if (!offered.path(direction + "Available").asBoolean(false)) {
            throw new IllegalArgumentException("Token profile does not offer key " + direction + ".");
        }
        JsonNode types = offered.path(direction + "ableKeyTypes");
        if (!types.isMissingNode() && !types.isNull()) {
            JsonNode algorithms = types.path(type);
            if (!algorithms.isArray() || algorithms.isEmpty()) {
                throw new IllegalArgumentException("Token profile does not offer this key type for " + direction + ".");
            }
            if (algorithm != null) {
                boolean found = false;
                for (JsonNode candidate : algorithms) {
                    found |= algorithm.equals(candidate.asString());
                }
                if (!found) {
                    throw new IllegalArgumentException(
                            "Token profile does not offer this key algorithm for " + direction + ".");
                }
            }
        }
    }

    @Override
    public String updateKey(String uuid, String configuration) {
        return ConfigurationSupport.run("updating key", () -> {
            CryptoInput.uuid(uuid);
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", DESCRIPTION, TOKEN_PROFILE_UUID, OWNER_UUID, GROUP_UUIDS,
                            CUSTOM_ATTRIBUTES);
            JsonNode existing = client.get(KEY, uuid);
            CryptoInput.preserve(body, existing, "name", DESCRIPTION, TOKEN_PROFILE_UUID, OWNER_UUID);
            CryptoInput.text(body, "name", DESCRIPTION, TOKEN_PROFILE_UUID, OWNER_UUID);
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Key name"));
            for (String field : new String[]{TOKEN_PROFILE_UUID, OWNER_UUID}) {
                if (body.hasNonNull(field)) {
                    CryptoInput.uuid(body.path(field).asString());
                }
            }
            if (!body.has(GROUP_UUIDS)) {
                ArrayNode groups = body.putArray(GROUP_UUIDS);
                existing.path("groups").forEach(group -> groups.add(CryptoInput.uuid(group.path("uuid").asString())));
            }
            CryptoInput.groups(body);
            body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "keys", existing, body));
            return ConfigurationSupport.describe("Key updated", client.put(KEY, body, uuid), FIELDS);
        });
    }

    @Override
    public String renameKeyItem(String uuid, String itemUuid, String name) {
        return ConfigurationSupport.run("renaming key item", () -> {
            ObjectNode body = ConfigurationAttributes.JSON
                    .createObjectNode()
                    .put("name", ConfigurationSupport.required(name, "Item name"));
            client.patch(ITEM, body, CryptoInput.uuid(uuid), CryptoInput.uuid(itemUuid));
            return "Key item rename accepted [" + itemUuid + "].";
        });
    }

    @Override
    public String enableKey(String uuid, String itemUuids) {
        return lifecycle(uuid, itemUuids, "enable");
    }

    @Override
    public String disableKey(String uuid, String itemUuids) {
        return lifecycle(uuid, itemUuids, "disable");
    }

    @Override
    public String destroyKey(String uuid, String itemUuids) {
        return lifecycle(uuid, itemUuids, "destroy");
    }

    @Override
    public String deleteKey(String uuid, String itemUuids) {
        return lifecycle(uuid, itemUuids, DELETE);
    }

    private String lifecycle(String uuid, String itemUuids, String operation) {
        return ConfigurationSupport.run(operation + " key", () -> {
            ArrayNode selected = selected(uuid, itemUuids);
            if (DELETE.equals(operation)) {
                client.deleteBody(KEY, selected, uuid);
            } else {
                client.patch(KEY + "/" + operation, selected, uuid);
            }
            return "Key " + operation + " request accepted [" + uuid + "]. "
                    + (Set.of(DELETE, "destroy").contains(operation)
                            ? "The operation may remove key material at the provider; this is irreversible."
                            : "Selected item state is controlled by ILM.");
        });
    }

    private ArrayNode selected(String uuid, String itemUuids) {
        ArrayNode selected = CryptoInput.strings(itemUuids, true);
        JsonNode key = client.get(KEY, CryptoInput.uuid(uuid));
        Set<String> known = new java.util.HashSet<>();
        key.path("items").forEach(item -> known.add(item.path("uuid").asString()));
        for (JsonNode item : selected) {
            if (!known.contains(item.asString())) {
                throw new IllegalArgumentException("Selected key item does not belong to this key.");
            }
        }
        return selected;
    }

    @Override
    public String updateKeyUsages(String uuid, String usages, String itemUuids) {
        return ConfigurationSupport.run("updating key usages", () -> {
            ObjectNode body = ConfigurationAttributes.JSON.createObjectNode();
            body.set("usage", CryptoInput.usages(usages));
            body.set("uuids", selected(uuid, itemUuids));
            client.put(KEY + "/usages", body, uuid);
            return "Key usage replacement accepted [" + uuid + "].";
        });
    }

    @Override
    public String compromiseKey(String uuid, String reason, String itemUuids) {
        return ConfigurationSupport.run("marking key compromised", () -> {
            if (!Set
                    .of("disclosure", "modification", "substitution", "use_of_sensitive_data")
                    .contains(reason == null ? "" : reason)) {
                throw new IllegalArgumentException("A supported compromise reason is required.");
            }
            ObjectNode body = ConfigurationAttributes.JSON.createObjectNode().put("reason", reason);
            body.set("uuids", selected(uuid, itemUuids));
            client.patch(KEY + "/compromise", body, uuid);
            return "Key compromise request accepted [" + uuid + "].";
        });
    }

    @Override
    public String disableKeyExport(String uuid, String itemUuid) {
        return transfer("disabling key export",
                () -> ConfigurationSupport
                        .describe("Key export disabled; irreversible",
                                client
                                        .patch(ITEM + "/export/disable", null, CryptoInput.uuid(uuid),
                                                CryptoInput.uuid(itemUuid)),
                                FIELDS));
    }

    private void requireTransfer() {
        versions.unsupported(IlmFeature.KEY_TRANSFER).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private String transfer(String action, Supplier<String> body) {
        return versions.unsupported(IlmFeature.KEY_TRANSFER).orElseGet(() -> ConfigurationSupport.run(action, body));
    }
}
