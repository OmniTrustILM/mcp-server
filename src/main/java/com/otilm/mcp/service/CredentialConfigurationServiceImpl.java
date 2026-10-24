package com.otilm.mcp.service;

import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;
import static com.otilm.mcp.service.ConfigurationSupport.uuid;

@Service
public class CredentialConfigurationServiceImpl implements CredentialConfigurationService {
    private static final String CREDENTIAL_UUID = "Credential UUID";
    private static final String ATTRIBUTES = "attributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";

    private static final String CREDENTIAL_ENDPOINT = "/v1/credentials/{uuid}";
    private static final String[] FIELDS = {"uuid", "name", "kind", "enabled", CONNECTOR_UUID, "connectorName"};
    private final ConfigurationApiClient api;

    public CredentialConfigurationServiceImpl(ConfigurationApiClient api) {
        this.api = api;
    }

    @Override
    public String listCredentials() {
        return run("listing credentials", () -> list("Credentials", api.get("/v1/credentials"), FIELDS));
    }

    @Override
    public String getCredential(String uuid) {
        return run("getting credential",
                () -> describe("Credential", api.get(CREDENTIAL_ENDPOINT, uuid(uuid, CREDENTIAL_UUID)), FIELDS));
    }

    @Override
    public String getCredentialAttributes(String connectorUuid, String kind, String values) {
        return run("getting credential attributes", () -> {
            var context = new ConfigurationAttributes.ConnectorContext(connectorUuid,
                    FunctionGroupCode.CREDENTIAL_PROVIDER, kind);
            return ConfigurationAttributes
                    .settings("Credential attributes", ConfigurationAttributes
                            .resolve(api, context, definitions(context.connectorUuid(), context.kind()), values));
        });
    }

    @Override
    public String createCredential(String configuration) {
        return run("creating credential", () -> {
            ObjectNode body = AdministrationInput
                    .configuration(configuration, "name", "kind", CONNECTOR_UUID, ATTRIBUTES, CUSTOM_ATTRIBUTES);
            AdministrationInput.text(body, "name", "kind", CONNECTOR_UUID);
            body.put("name", required(body.path("name").asString(null), "Credential name"));
            finish(body, AdministrationInput.JSON.createObjectNode(), body.path(CONNECTOR_UUID).asString(null),
                    body.path("kind").asString(null));
            return describe("Credential created", api.post("/v1/credentials", body), FIELDS);
        });
    }

    @Override
    public String updateCredential(String uuid, String configuration) {
        return run("updating credential", () -> {
            String id = uuid(uuid, CREDENTIAL_UUID);
            ObjectNode body = AdministrationInput.configuration(configuration, ATTRIBUTES, CUSTOM_ATTRIBUTES);
            JsonNode existing = api.get(CREDENTIAL_ENDPOINT, id);
            finish(body, existing, existing.path(CONNECTOR_UUID).asString(null), existing.path("kind").asString(null));
            return describe("Credential updated", api.put(CREDENTIAL_ENDPOINT, body, id), FIELDS);
        });
    }

    @Override
    public String enableCredential(String uuid) {
        return enabled(uuid, true);
    }

    @Override
    public String disableCredential(String uuid) {
        return enabled(uuid, false);
    }

    @Override
    public String deleteCredential(String uuid) {
        return run("deleting credential", () -> {
            api.delete(CREDENTIAL_ENDPOINT, uuid(uuid, CREDENTIAL_UUID));
            return "Credential deleted.";
        });
    }

    private String enabled(String uuid, boolean enabled) {
        return run("changing credential state", () -> {
            api.patch(CREDENTIAL_ENDPOINT + (enabled ? "/enable" : "/disable"), null, uuid(uuid, CREDENTIAL_UUID));
            return "Credential " + (enabled ? "enabled." : "disabled.");
        });
    }

    private JsonNode definitions(String connectorUuid, String kind) {
        return api
                .get("/v1/connectors/{uuid}/attributes/credentialProvider/{kind}",
                        uuid(connectorUuid, "Connector UUID"), required(kind, "Credential kind"));
    }

    private static JsonNode previous(JsonNode existing, String key) {
        return existing.path(key).isArray() ? existing.path(key) : AdministrationInput.JSON.createArrayNode();
    }

    private void finish(ObjectNode body, JsonNode existing, String connectorUuid, String kind) {
        String connector = uuid(connectorUuid, "Connector UUID");
        String type = required(kind, "Credential kind");
        String values = body.has(ATTRIBUTES) ? body.path(ATTRIBUTES).toString() : "{}";
        JsonNode expanded = ConfigurationAttributes
                .resolveReplacement(
                        api, new ConfigurationAttributes.ConnectorContext(connector,
                                FunctionGroupCode.CREDENTIAL_PROVIDER, type),
                        definitions(connector, type), previous(existing, ATTRIBUTES), values);
        body.set(ATTRIBUTES, ConfigurationAttributes.replace(expanded, previous(existing, ATTRIBUTES), values));
        body
                .set(CUSTOM_ATTRIBUTES,
                        ConfigurationAttributes
                                .replace(api.get("/v1/attributes/custom/resources/credentials"),
                                        previous(existing, CUSTOM_ATTRIBUTES),
                                        body.has(CUSTOM_ATTRIBUTES) ? body.path(CUSTOM_ATTRIBUTES).toString() : "{}"));
    }
}
