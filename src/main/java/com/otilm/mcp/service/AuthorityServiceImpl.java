package com.otilm.mcp.service;

import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.StringJoiner;
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
    private static final String AUTHORITY_INTERFACE = "authority";
    private static final String CALLBACK_FREE_KIND = "Authority";
    private static final String ATTRIBUTES = "attributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String INTERFACE_UUID = "interfaceUuid";

    private static final String BASE = "/v1/authorities";
    private static final String KIND_SCHEMA = "/v1/connectors/{connector}/attributes/authorityProvider/{kind}";
    private static final String INTERFACE_SCHEMA = BASE + "/{connector}/attributes?interfaceUuid={interface}";
    private static final String SOLE_INTERFACE_SCHEMA = BASE + "/{connector}/attributes";
    private static final String V3 = "v3";
    private static final String PROTECTION_LEVEL = "protectionLevel";
    private static final String VALUE_OMITTED = "value omitted";
    private final CertificateConfiguration support;
    private final ConfigurationApiClient client;

    public AuthorityServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        support = new CertificateConfiguration(client, versions);
    }

    @Override
    public String getAuthority(String uuid) {
        return run("reading authority", () -> {
            JsonNode authority = client.get(BASE + UUID_ENDPOINT, id(uuid));
            return describe(AUTHORITY, authority, CertificateConfiguration.META) + metadata(authority.path("metadata"));
        });
    }

    private static String metadata(JsonNode groups) {
        StringBuilder output = new StringBuilder();
        for (JsonNode group : groups) {
            for (JsonNode item : group.path("items")) {
                if (output.isEmpty()) {
                    output.append("metadata:\n");
                }
                TextFormat
                        .field(output, "  ",
                                item.path("label").asString("") + " [" + item.path("name").asString("") + "]",
                                metadataValue(item));
            }
        }
        return output.toString();
    }

    /**
     * Core's metadata response carries no protection level: it decrypts encrypted content for authorized callers and
     * returns it as plain data. A value is shown only when it is a plain scalar type and no protection marker, which a
     * future Core may add, says anything but none. Every other shape is omitted.
     */
    private static String metadataValue(JsonNode item) {
        if (protectionMarked(item) || AttributeContentValues.protectedValue(item)
                || !ConfigurationAttributes.DISPLAYABLE.contains(item.path("contentType").asString(""))) {
            return VALUE_OMITTED;
        }
        StringJoiner values = new StringJoiner(", ");
        for (JsonNode content : item.path("content")) {
            JsonNode data = content.path("data");
            if (!data.isValueNode() || AttributeContentValues.masked(data) || protectionMarked(content)
                    || protectionMarked(data)) {
                return VALUE_OMITTED;
            }
            if (!data.isNull() && !data.asString().isBlank()) {
                values.add(TextFormat.sanitizeUrl(data.asString()));
            }
        }
        return values.length() == 0 ? VALUE_OMITTED : values.toString();
    }

    /** A protection marker anywhere on the node, even an empty or unknown one, means the value is not plain. */
    private static boolean protectionMarked(JsonNode node) {
        for (JsonNode marker : new JsonNode[]{
                node.path(PROTECTION_LEVEL),
                node.path("properties").path(PROTECTION_LEVEL)}) {
            if (!marker.isMissingNode() && !"none".equalsIgnoreCase(marker.asString(""))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getAuthorityAttributes(String connectorUuid, String interfaceUuid, String kind, String values) {
        return run("reading authority settings", () -> {
            Provider provider = provider(id(connectorUuid), optionalId(interfaceUuid), optionalKind(kind));
            JsonNode definitions = providerDefinitions(provider);
            return ConfigurationAttributes
                    .settings("Authority settings", ConfigurationAttributes
                            .resolve(client, context(provider, definitions), definitions, values));
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
            String kind = optionalKind(body.path("kind").asString(null));
            if (kind == null) {
                body.remove("kind");
            } else {
                body.put("kind", kind);
            }
            String iface = optionalId(body.path(INTERFACE_UUID).asString(null));
            if (iface != null) {
                body.put(INTERFACE_UUID, iface);
            }
            Provider provider = provider(connector, iface, kind);
            JsonNode definitions = providerDefinitions(provider);
            body
                    .set(ATTRIBUTES, ConfigurationAttributes
                            .encode(client, context(provider, definitions), definitions,
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
            String iface = v3InterfaceUuid(current);
            Provider provider = iface == null
                    ? Provider.ofKind(connector, required(current.path("kind").asString(null), "Authority kind"))
                    : Provider.ofInterface(connector, iface);
            JsonNode definitions = providerDefinitions(provider);
            String input = changes.has(ATTRIBUTES) ? changes.path(ATTRIBUTES).toString() : "{}";
            JsonNode resolved = ConfigurationAttributes
                    .resolveReplacement(client, context(provider, definitions), definitions,
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

    /** An interface UUID selects the schema route by its version. Core validates the attributes by that version. */
    private Provider provider(String connector, String iface, String kind) {
        if (iface == null) {
            return kind == null ? Provider.ofSoleInterface(connector) : Provider.ofKind(connector, kind);
        }
        support.require(IlmFeature.AUTHORITY_INTERFACES);
        return V3.equals(interfaceVersion(connector, iface))
                ? Provider.ofInterface(connector, iface)
                : Provider.ofKind(connector, required(kind, "Authority kind"));
    }

    private String interfaceVersion(String connector, String iface) {
        for (JsonNode candidate : client.get("/v2/connectors/{uuid}", connector).path("interfaces")) {
            if (iface.equalsIgnoreCase(candidate.path("uuid").asString(null))
                    && AUTHORITY_INTERFACE.equals(candidate.path("code").asString(null))) {
                return candidate.path("version").asString(null);
            }
        }
        throw new IllegalArgumentException("Authority interfaceUuid is not an authority interface of the connector.");
    }

    private JsonNode providerDefinitions(Provider provider) {
        if (provider.kind() != null) {
            return client.get(KIND_SCHEMA, provider.connector(), provider.kind());
        }
        if (provider.iface() != null) {
            return client.get(INTERFACE_SCHEMA, provider.connector(), provider.iface());
        }
        support.require(IlmFeature.AUTHORITY_INTERFACES);
        return client.get(SOLE_INTERFACE_SCHEMA, provider.connector());
    }

    private static String optionalId(String uuid) {
        return uuid == null ? null : id(uuid);
    }

    private static String optionalKind(String kind) {
        return kind == null || kind.isBlank() ? null : kind.trim();
    }

    /** Core serves the interface schema route for v3 interfaces only. */
    private static String v3InterfaceUuid(JsonNode authority) {
        JsonNode iface = authority.path("connectorInterface");
        return V3.equals(iface.path("version").asString(null)) ? iface.path("uuid").asString(null) : null;
    }

    private static ConfigurationAttributes.CallbackContext context(Provider provider, JsonNode definitions) {
        if (provider.kind() != null) {
            return new ConfigurationAttributes.ConnectorContext(provider.connector(),
                    FunctionGroupCode.AUTHORITY_PROVIDER, provider.kind());
        }
        if (provider.iface() != null) {
            return new ConfigurationAttributes.ConnectorInterfaceContext(provider.connector(), provider.iface());
        }
        if (AttributeCallbacks.hasActiveCallback(definitions)) {
            throw new IllegalArgumentException("Authority interfaceUuid is required to resolve provider callbacks.");
        }
        return new ConfigurationAttributes.ConnectorContext(provider.connector(), FunctionGroupCode.AUTHORITY_PROVIDER,
                CALLBACK_FREE_KIND);
    }

    /** The provider schema of a connector, selected by a v3 interface UUID, a v1 or v2 kind, or neither. */
    private record Provider(String connector, String iface, String kind) {
        static Provider ofInterface(String connector, String iface) {
            return new Provider(connector, iface, null);
        }

        static Provider ofKind(String connector, String kind) {
            return new Provider(connector, null, kind);
        }

        static Provider ofSoleInterface(String connector) {
            return new Provider(connector, null, null);
        }
    }
}
