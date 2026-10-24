package com.otilm.mcp.service;

import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class AuthorityServiceImpl implements AuthorityService {
    private static final String UUID_ENDPOINT = "/{uuid}";
    private static final String AUTHORITY = "Authority";
    private static final String ATTRIBUTES = "attributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String INTERFACE_UUID = "interfaceUuid";

    private static final String BASE = "/v1/authorities";
    private final CertificateConfiguration support;
    private final ConfigurationApiClient client;

    public AuthorityServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        support = new CertificateConfiguration(client, versions);
    }

    @Override
    public String getAuthority(String uuid) {
        return run("reading authority",
                () -> describe(AUTHORITY, client.get(BASE + UUID_ENDPOINT, id(uuid)), CertificateConfiguration.META));
    }

    @Override
    public String getAuthorityAttributes(String connectorUuid, String interfaceUuid, String kind, String values) {
        return run("reading authority settings", () -> {
            id(connectorUuid);
            String path = schemaPath(interfaceUuid);
            JsonNode definitions = interfaceUuid == null
                    ? client.get(path, connectorUuid)
                    : client.get(path, connectorUuid, id(interfaceUuid));
            String selectedKind = kind;
            if (interfaceUuid == null && selectedKind == null) {
                for (JsonNode definition : definitions) {
                    if (definition.hasNonNull("attributeCallback")) {
                        throw new IllegalArgumentException("Authority kind is required to resolve provider callbacks.");
                    }
                }
                selectedKind = AUTHORITY;
            }
            return ConfigurationAttributes
                    .settings("Authority settings", ConfigurationAttributes
                            .resolve(client, context(connectorUuid, interfaceUuid, selectedKind), definitions, values));
        });
    }

    @Override
    public String createAuthority(String configuration) {
        return run("creating authority", () -> {
            ObjectNode body = configuration(configuration, "name", CONNECTOR_UUID, INTERFACE_UUID, "kind", ATTRIBUTES,
                    CUSTOM_ATTRIBUTES);
            text(body, "name", CONNECTOR_UUID, INTERFACE_UUID, "kind");
            required(body.path("name").asString(null), "name");
            String connector = id(body.path(CONNECTOR_UUID).asString(null));
            String iface = body.path(INTERFACE_UUID).asString(null);
            String kind = iface == null ? required(body.path("kind").asString(null), "kind") : AUTHORITY;
            String path = schemaPath(iface);
            JsonNode definitions = iface == null ? client.get(path, connector) : client.get(path, connector, id(iface));
            body
                    .set(ATTRIBUTES, ConfigurationAttributes
                            .encode(client, context(connector, iface, kind), definitions,
                                    body.path(ATTRIBUTES).isMissingNode() ? "{}" : body.path(ATTRIBUTES).toString()));
            body.set(CUSTOM_ATTRIBUTES, support.custom("authorities", null, body.path(CUSTOM_ATTRIBUTES)));
            return describe("Created authority", client.post(BASE, body), CertificateConfiguration.META);
        });
    }

    @Override
    public String updateAuthority(String uuid, String configuration) {
        return run("updating authority", () -> {
            id(uuid);
            ObjectNode changes = configuration(configuration, ATTRIBUTES, CUSTOM_ATTRIBUTES);
            JsonNode current = client.get(BASE + UUID_ENDPOINT, uuid);
            String connector = id(
                    current.path(CONNECTOR_UUID).asString(current.path("connector").path("uuid").asString(null)));
            String iface = current.path("connectorInterface").path("uuid").asString(null);
            String kind = current.path("kind").asString(AUTHORITY);
            JsonNode definitions = iface == null
                    ? client.get(schemaPath(null), connector)
                    : client.get(schemaPath(iface), connector, id(iface));
            String input = changes.has(ATTRIBUTES) ? changes.path(ATTRIBUTES).toString() : "{}";
            JsonNode resolved = ConfigurationAttributes
                    .resolveReplacement(client, context(connector, iface, kind), definitions,
                            CertificateConfiguration.array(current.path(ATTRIBUTES)), input);
            ObjectNode body = JSON.createObjectNode();
            body
                    .set(ATTRIBUTES, ConfigurationAttributes
                            .replace(resolved, CertificateConfiguration.array(current.path(ATTRIBUTES)), input));
            body
                    .set(CUSTOM_ATTRIBUTES, support
                            .custom("authorities", current.path(CUSTOM_ATTRIBUTES), changes.path(CUSTOM_ATTRIBUTES)));
            return "Updated authority " + uuid + ".\n"
                    + describe(AUTHORITY, client.put(BASE + UUID_ENDPOINT, body, uuid), CertificateConfiguration.META);
        });
    }

    @Override
    public String deleteAuthority(String uuid) {
        return run("deleting authority", () -> {
            client.delete(BASE + UUID_ENDPOINT, id(uuid));
            return "Deleted authority " + uuid + ".";
        });
    }

    @Override
    public String getAuthorityChoices(String uuid, String endEntityProfileId) {
        return run("reading authority choices", () -> {
            id(uuid);
            if (endEntityProfileId == null) {
                return list("End entity profiles", client.get(BASE + "/{uuid}/endentityprofiles", uuid), "id", "name");
            }
            required(endEntityProfileId, "End entity profile ID");
            return list("Certificate profiles",
                    client.get(BASE + "/{uuid}/endentityprofiles/{id}/certificateprofiles", uuid, endEntityProfileId),
                    "id", "name")
                    + list("Certificate authorities",
                            client.get(BASE + "/{uuid}/endentityprofiles/{id}/cas", uuid, endEntityProfileId), "id",
                            "name");
        });
    }

    private String schemaPath(String iface) {
        if (iface != null) {
            id(iface);
            support.require(IlmFeature.AUTHORITY_INTERFACES);
            return BASE + "/{connector}/attributes?interfaceUuid={interface}";
        }
        return BASE + "/{connector}/attributes";
    }

    private static ConfigurationAttributes.CallbackContext context(String connector, String iface, String kind) {
        return iface == null
                ? new ConfigurationAttributes.ConnectorContext(connector, FunctionGroupCode.AUTHORITY_PROVIDER, kind)
                : new ConfigurationAttributes.ConnectorInterfaceContext(connector, iface);
    }
}
