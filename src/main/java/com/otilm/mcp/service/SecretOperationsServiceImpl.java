package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SecretOperationsServiceImpl implements SecretOperationsService {
    private static final String SECRET_2 = "Secret [";
    private static final String ATTRIBUTES = "attributes";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String SECRET_3 = "secret";

    private static final String SECRET = "/v1/secrets/{uuid}";
    private static final String SYNC = SECRET + "/syncVaultProfiles/{profile}";
    private static final String[] FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "type",
            "state",
            "version",
            "enabled",
            "sourceVaultProfile.uuid",
            "sourceVaultProfile.name"};
    private final ConfigurationApiClient client;
    private final VaultConfigurationRequests requests;

    public SecretOperationsServiceImpl(ConfigurationApiClient client) {
        this.client = client;
        requests = new VaultConfigurationRequests(client);
    }

    @Override
    public String createSecret(String vaultUuid, String profileUuid, String configuration) {
        return ConfigurationSupport.run("creating secret", () -> {
            String vault = VaultConfigurationRequests.id(vaultUuid);
            String profile = VaultConfigurationRequests.id(profileUuid);
            ObjectNode config = VaultConfigurationRequests
                    .input(configuration, Set.of("name", DESCRIPTION, SECRET_3, ATTRIBUTES, CUSTOM_ATTRIBUTES));
            String name = ConfigurationSupport.required(config.path("name").asString(), "Secret name");
            ObjectNode secret = SecretContentValues.validate(config.get(SECRET_3));
            requests.getProfile(vault, profile);
            var context = requests.context(requests.vault(vault));
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode().put("name", name);
            body.set(DESCRIPTION, VaultConfigurationRequests.description(config, body));
            body.set(SECRET_3, secret);
            body
                    .set(ATTRIBUTES, requests
                            .attributes(requests.secretDefinitions(vault, profile, secret.path("type").asString()),
                                    context, config, body, false));
            body.set(CUSTOM_ATTRIBUTES, requests.custom(Resource.SECRET, config, body, false));
            JsonNode result = client.post(VaultConfigurationRequests.SECRETS, body, vault, profile);
            return ConfigurationSupport
                    .describe("Secret creation accepted; approval/provider synchronization may be pending", result,
                            FIELDS);
        });
    }

    @Override
    public String updateSecret(String uuid, String vaultUuid, String configuration) {
        return ConfigurationSupport.run("updating secret", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            ObjectNode config = VaultConfigurationRequests
                    .input(configuration, Set.of(DESCRIPTION, SECRET_3, ATTRIBUTES, CUSTOM_ATTRIBUTES));
            if (config.isEmpty()) {
                throw new IllegalArgumentException("Supply secret metadata or fresh typed content to update.");
            }
            ObjectNode content = config.has(SECRET_3) ? SecretContentValues.validate(config.get(SECRET_3)) : null;
            if (content == null && config.has(ATTRIBUTES)) {
                throw new IllegalArgumentException(
                        "Provider attributes apply only when fresh secret content is supplied.");
            }
            String vault = content == null ? null : VaultConfigurationRequests.id(vaultUuid);
            JsonNode current = client.get(SECRET, id);
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode();
            body.set(DESCRIPTION, VaultConfigurationRequests.description(config, current));
            if (content == null) {
                body.set(ATTRIBUTES, VaultConfigurationRequests.JSON.createArrayNode());
            } else {
                setReplacementContent(body, config, current, content, vault);
            }
            body.set(CUSTOM_ATTRIBUTES, requests.custom(Resource.SECRET, config, current, true));
            JsonNode result = client.put(SECRET, body, id);
            String title = content == null
                    ? "Secret metadata updated [" + id + "]"
                    : "Secret update accepted [" + id
                            + "]; new version/approval/provider synchronization may be pending";
            return ConfigurationSupport.describe(title, result, FIELDS);
        });
    }

    private void setReplacementContent(ObjectNode body, ObjectNode config, JsonNode current, ObjectNode content,
            String vault) {
        writableContent(current);
        if (!content.path("type").asString().equals(current.path("type").asString())) {
            throw new IllegalArgumentException("Updating a secret cannot change its secret type.");
        }
        String profile = VaultConfigurationRequests.id(current.path("sourceVaultProfile").path("uuid").asString());
        requests.getProfile(vault, profile);
        var context = requests.context(requests.vault(vault));
        body.set(SECRET_3, content);
        body
                .set(ATTRIBUTES,
                        requests
                                .attributes(requests.secretDefinitions(vault, profile, current.path("type").asString()),
                                        context, config, current, true));
    }

    @Override
    public String enableSecret(String uuid) {
        return state(uuid, true);
    }

    @Override
    public String disableSecret(String uuid) {
        return state(uuid, false);
    }

    private String state(String uuid, boolean enabled) {
        return ConfigurationSupport.run("changing secret state", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            client.patch(SECRET + (enabled ? "/enable" : "/disable"), null, id);
            return SECRET_2 + id + "] " + (enabled ? "enabled." : "disabled.");
        });
    }

    @Override
    public String setSecretGroups(String uuid, String groupUuids) {
        return ConfigurationSupport.run("setting secret groups", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            JsonNode input;
            try {
                input = VaultConfigurationRequests.JSON.readTree(groupUuids);
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Groups must be a JSON array of canonical UUIDs.");
            }
            if (input == null || !input.isArray()) {
                throw new IllegalArgumentException("Groups must be a JSON array of canonical UUIDs.");
            }
            ArrayNode groups = VaultConfigurationRequests.JSON.createArrayNode();
            Set<String> unique = new HashSet<>();
            for (JsonNode group : input) {
                String value = VaultConfigurationRequests.id(group.asString());
                if (!unique.add(value.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("Group UUIDs must be unique.");
                }
                groups.add(value);
            }
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode();
            body.set("groupUuids", groups);
            client.patch(SECRET, body, id);
            return SECRET_2 + id + "] group membership replaced.";
        });
    }

    @Override
    public String setSecretOwner(String uuid, String ownerUuid) {
        return ConfigurationSupport.run("setting secret owner", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            if (ownerUuid == null) {
                throw new IllegalArgumentException("Owner UUID is required; use empty string to clear.");
            }
            String owner = ownerUuid.isEmpty() ? "" : VaultConfigurationRequests.id(ownerUuid);
            client.patch(SECRET, VaultConfigurationRequests.JSON.createObjectNode().put("ownerUuid", owner), id);
            return SECRET_2 + id + "] owner updated.";
        });
    }

    @Override
    public String changeSecretSourceVaultProfile(String uuid, String vaultUuid, String profileUuid, String attributes) {
        return profileAssociation(uuid, vaultUuid, profileUuid, attributes, false);
    }

    @Override
    public String addSecretSyncVaultProfile(String uuid, String vaultUuid, String profileUuid, String attributes) {
        return profileAssociation(uuid, vaultUuid, profileUuid, attributes, true);
    }

    private String profileAssociation(String uuid, String vaultUuid, String profileUuid, String attributes,
            boolean synchronization) {
        return ConfigurationSupport.run("changing secret vault profile", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            String vault = VaultConfigurationRequests.id(vaultUuid);
            String profile = VaultConfigurationRequests.id(profileUuid);
            ObjectNode config = VaultConfigurationRequests.JSON.createObjectNode();
            config.set(ATTRIBUTES, ConfigurationSupport.object(attributes, "Provider attributes"));
            JsonNode current = client.get(SECRET, id);
            String type = SecretContentValues.type(current.path("type").asString());
            writableContent(current);
            if (alreadySourceProfile(current, profile, synchronization)) {
                return SECRET_2 + id + "] already uses source vault profile [" + profile + "].";
            }
            requests.getProfile(vault, profile);
            var context = requests.context(requests.vault(vault));
            ArrayNode encoded = requests
                    .attributes(requests.secretDefinitions(vault, profile, type), context, config, config, false);
            if (synchronization) {
                client.patch(SYNC, encoded, id, profile);
            } else {
                ObjectNode body = VaultConfigurationRequests.JSON
                        .createObjectNode()
                        .put("sourceVaultProfileUuid", profile);
                body.set("secretAttributes", encoded);
                client.patch(SECRET, body, id);
            }
            return SECRET_2 + id + "] " + (synchronization ? "synchronization profile" : "source profile change") + " ["
                    + profile + "] accepted; approval/provider synchronization may be pending.";
        });
    }

    private static boolean alreadySourceProfile(JsonNode current, String profile, boolean synchronization) {
        String source = current.path("sourceVaultProfile").path("uuid").asString();
        if (profile.equalsIgnoreCase(source)) {
            if (synchronization) {
                throw new IllegalArgumentException(
                        "The source vault profile cannot also be a synchronization profile.");
            }
            return true;
        }
        if (synchronization) {
            requireUnlinkedSyncProfile(current, profile);
        }
        return false;
    }

    private static void requireUnlinkedSyncProfile(JsonNode current, String profile) {
        for (JsonNode linked : current.path("syncVaultProfiles")) {
            if (profile.equalsIgnoreCase(linked.path("uuid").asString())) {
                throw new IllegalArgumentException("Vault profile is already associated for synchronization.");
            }
        }
    }

    private static void writableContent(JsonNode secret) {
        String state = secret.path("state").asString();
        if (Set.of("failed", "pendingApproval", "rejected").contains(state)) {
            throw new IllegalArgumentException("Secret state " + state
                    + " prevents content/profile changes; resolve its pending or failed operation first.");
        }
    }

    @Override
    public String removeSecretSyncVaultProfile(String uuid, String profileUuid) {
        return removeSync(uuid, profileUuid, false);
    }

    @Override
    public String removeSecretSyncVaultProfileContent(String uuid, String profileUuid) {
        return removeSync(uuid, profileUuid, true);
    }

    private String removeSync(String uuid, String profileUuid, boolean deleteContent) {
        return ConfigurationSupport.run("removing secret synchronization profile", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            String profile = VaultConfigurationRequests.id(profileUuid);
            client.delete(SYNC + "?deleteInVault=" + deleteContent, id, profile);
            return SECRET_2 + id + "] synchronization profile [" + profile + "] removal accepted; "
                    + (deleteContent ? "provider content deletion may be pending." : "provider content retained.");
        });
    }

    @Override
    public String deleteSecret(String uuid) {
        return delete(uuid, false);
    }

    @Override
    public String deleteSecretFromVaults(String uuid) {
        return delete(uuid, true);
    }

    private String delete(String uuid, boolean deleteContent) {
        return ConfigurationSupport.run("deleting secret", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            client.delete(SECRET + "?deleteInVaults=" + deleteContent, id);
            return SECRET_2 + id + "] deletion accepted; "
                    + (deleteContent
                            ? "provider vault content deletion may be pending."
                            : "provider vault content retained.");
        });
    }

    @Override
    public String getSecretContent(String uuid) {
        return ConfigurationSupport.run("retrieving sensitive secret content", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            return SecretContentValues.output(id, client.get(SECRET + "/content", id));
        });
    }
}
