package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class VaultConfigurationServiceImpl implements VaultConfigurationService {
    private static final String ATTRIBUTES = "attributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String INTERFACE_UUID = "interfaceUuid";

    private static final String[] VAULT_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "connector.uuid",
            "connector.name",
            "connectorInterface.uuid",
            "connectorInterface.code",
            "connectorInterface.version"};
    private static final String[] PROFILE_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "enabled",
            "vaultInstance.uuid",
            "vaultInstance.name"};
    private final ConfigurationApiClient client;
    private final VaultConfigurationRequests requests;

    public VaultConfigurationServiceImpl(ConfigurationApiClient client) {
        this.client = client;
        this.requests = new VaultConfigurationRequests(client);
    }

    @Override
    public String getVaultInstance(String uuid) {
        return ConfigurationSupport
                .run("retrieving vault instance",
                        () -> ConfigurationSupport.describe("Vault instance", requests.vault(uuid), VAULT_FIELDS));
    }

    @Override
    public String getVaultAttributes(String connectorUuid, String interfaceUuid, String values) {
        return ConfigurationSupport.run("retrieving vault attributes", () -> {
            var context = requests.connector(connectorUuid, interfaceUuid);
            return requests.schema("Vault", requests.vaultDefinitions(connectorUuid), context, values, Resource.VAULT);
        });
    }

    @Override
    public String getVaultProfile(String vaultUuid, String profileUuid) {
        return ConfigurationSupport
                .run("retrieving vault profile", () -> ConfigurationSupport
                        .describe("Vault profile", requests.getProfile(vaultUuid, profileUuid), PROFILE_FIELDS));
    }

    @Override
    public String getVaultProfileAttributes(String vaultUuid, String values) {
        return ConfigurationSupport.run("retrieving vault profile attributes", () -> {
            var context = requests.context(requests.vault(vaultUuid));
            return requests
                    .schema("Vault profile", requests.profileDefinitions(vaultUuid), context, values,
                            Resource.VAULT_PROFILE);
        });
    }

    @Override
    public String getSecretAttributes(String vaultUuid, String profileUuid, String secretType, String values) {
        return ConfigurationSupport.run("retrieving secret attributes", () -> {
            String type = SecretContentValues.type(secretType);
            requests.getProfile(vaultUuid, profileUuid);
            var context = requests.context(requests.vault(vaultUuid));
            return requests
                    .schema("Secret", requests.secretDefinitions(vaultUuid, profileUuid, type), context, values,
                            Resource.SECRET);
        });
    }

    @Override
    public String createVaultInstance(String configuration) {
        return ConfigurationSupport.run("creating vault instance", () -> {
            ObjectNode config = VaultConfigurationRequests
                    .input(configuration,
                            Set.of("name", DESCRIPTION, CONNECTOR_UUID, INTERFACE_UUID, ATTRIBUTES, CUSTOM_ATTRIBUTES));
            String name = ConfigurationSupport.required(config.path("name").asString(), "Vault name");
            String connector = VaultConfigurationRequests.id(config.path(CONNECTOR_UUID).asString());
            String iface = VaultConfigurationRequests.id(config.path(INTERFACE_UUID).asString());
            var context = requests.connector(connector, iface);
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode();
            body.put("name", name).put(CONNECTOR_UUID, connector).put(INTERFACE_UUID, iface);
            body.set(DESCRIPTION, VaultConfigurationRequests.description(config, body));
            body
                    .set(ATTRIBUTES,
                            requests.attributes(requests.vaultDefinitions(connector), context, config, body, false));
            body.set(CUSTOM_ATTRIBUTES, requests.custom(Resource.VAULT, config, body, false));
            return ConfigurationSupport
                    .describe("Created vault instance", client.post(VaultConfigurationRequests.VAULTS, body),
                            VAULT_FIELDS);
        });
    }

    @Override
    public String updateVaultInstance(String uuid, String configuration) {
        return ConfigurationSupport.run("updating vault instance", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            ObjectNode config = VaultConfigurationRequests
                    .input(configuration, VaultConfigurationRequests.UPDATE_FIELDS);
            if (config.isEmpty()) {
                throw new IllegalArgumentException("Supply at least one vault setting to update.");
            }
            JsonNode current = requests.vault(id);
            var context = requests.context(current);
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode();
            body.set(DESCRIPTION, VaultConfigurationRequests.description(config, current));
            body
                    .set(ATTRIBUTES,
                            requests
                                    .attributes(requests.vaultDefinitions(context.connectorUuid()), context, config,
                                            current, true));
            body.set(CUSTOM_ATTRIBUTES, requests.custom(Resource.VAULT, config, current, true));
            return ConfigurationSupport
                    .describe("Updated vault instance [" + id + "]",
                            client.put(VaultConfigurationRequests.VAULTS + "/{uuid}", body, id), VAULT_FIELDS);
        });
    }

    @Override
    public String deleteVaultInstance(String uuid) {
        return ConfigurationSupport.run("deleting vault instance", () -> {
            String id = VaultConfigurationRequests.id(uuid);
            client.delete(VaultConfigurationRequests.VAULTS + "/{uuid}", id);
            return "Vault instance [" + id + "] deleted.";
        });
    }

    @Override
    public String createVaultProfile(String vaultUuid, String configuration) {
        return ConfigurationSupport.run("creating vault profile", () -> {
            String vault = VaultConfigurationRequests.id(vaultUuid);
            ObjectNode config = VaultConfigurationRequests
                    .input(configuration, Set.of("name", DESCRIPTION, ATTRIBUTES, CUSTOM_ATTRIBUTES));
            String name = ConfigurationSupport.required(config.path("name").asString(), "Vault profile name");
            var context = requests.context(requests.vault(vault));
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode().put("name", name);
            body.set(DESCRIPTION, VaultConfigurationRequests.description(config, body));
            body.set(ATTRIBUTES, requests.attributes(requests.profileDefinitions(vault), context, config, body, false));
            body.set(CUSTOM_ATTRIBUTES, requests.custom(Resource.VAULT_PROFILE, config, body, false));
            return ConfigurationSupport
                    .describe("Created vault profile", client.post(VaultConfigurationRequests.PROFILES, body, vault),
                            PROFILE_FIELDS);
        });
    }

    @Override
    public String updateVaultProfile(String vaultUuid, String profileUuid, String configuration) {
        return ConfigurationSupport.run("updating vault profile", () -> {
            String vault = VaultConfigurationRequests.id(vaultUuid);
            String profile = VaultConfigurationRequests.id(profileUuid);
            ObjectNode config = VaultConfigurationRequests
                    .input(configuration, VaultConfigurationRequests.UPDATE_FIELDS);
            if (config.isEmpty()) {
                throw new IllegalArgumentException("Supply at least one vault profile setting to update.");
            }
            JsonNode current = requests.getProfile(vault, profile);
            var context = requests.context(requests.vault(vault));
            ObjectNode body = VaultConfigurationRequests.JSON.createObjectNode();
            body.set(DESCRIPTION, VaultConfigurationRequests.description(config, current));
            body
                    .set(ATTRIBUTES,
                            requests.attributes(requests.profileDefinitions(vault), context, config, current, true));
            body.set(CUSTOM_ATTRIBUTES, requests.custom(Resource.VAULT_PROFILE, config, current, true));
            return ConfigurationSupport
                    .describe("Updated vault profile [" + profile + "]",
                            client.put(VaultConfigurationRequests.PROFILE, body, vault, profile), PROFILE_FIELDS);
        });
    }

    @Override
    public String enableVaultProfile(String vaultUuid, String profileUuid) {
        return profileState(vaultUuid, profileUuid, true);
    }

    @Override
    public String disableVaultProfile(String vaultUuid, String profileUuid) {
        return profileState(vaultUuid, profileUuid, false);
    }

    private String profileState(String vaultUuid, String profileUuid, boolean enabled) {
        return ConfigurationSupport.run("changing vault profile state", () -> {
            String vault = VaultConfigurationRequests.id(vaultUuid);
            String profile = VaultConfigurationRequests.id(profileUuid);
            client.patch(VaultConfigurationRequests.PROFILE + (enabled ? "/enable" : "/disable"), null, vault, profile);
            return "Vault profile [" + profile + "] " + (enabled ? "enabled." : "disabled.");
        });
    }

    @Override
    public String deleteVaultProfile(String vaultUuid, String profileUuid) {
        return ConfigurationSupport.run("deleting vault profile", () -> {
            String vault = VaultConfigurationRequests.id(vaultUuid);
            String profile = VaultConfigurationRequests.id(profileUuid);
            client.delete(VaultConfigurationRequests.PROFILE, vault, profile);
            return "Vault profile [" + profile + "] deleted.";
        });
    }
}
