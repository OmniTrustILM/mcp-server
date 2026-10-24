package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;
import static com.otilm.mcp.service.ConfigurationSupport.uuid;

@Service
public class ConnectorServiceImpl implements ConnectorService {
    private static final String V2_CONNECTORS_UUID_ENDPOINT = "/v2/connectors/{uuid}";
    private static final String CONNECTOR_UUID = "Connector UUID";
    private static final String AUTH_ATTRIBUTES = "authAttributes";
    private static final String AUTH_TYPE = "authType";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String NO_PROXY = "noProxy";
    private static final String PROXY_UUID = "proxyUuid";
    private static final String STATUS = "status";
    private static final String VERSION = "version";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CUSTOM = "/v1/attributes/custom/resources/connectors";
    private static final String[] FIELDS = {
            "uuid",
            "name",
            "description",
            VERSION,
            "url",
            STATUS,
            AUTH_TYPE,
            "proxy",
            "functionGroups",
            "interfaces"};
    private final ConfigurationApiClient api;
    private final PlatformVersionCheck versions;

    public ConnectorServiceImpl(ConfigurationApiClient api, PlatformVersionCheck versions) {
        this.api = api;
        this.versions = versions;
    }

    @Override
    public String getConnector(String uuid) {
        return run("getting connector",
                () -> describe("Connector", api.get(V2_CONNECTORS_UUID_ENDPOINT, uuid(uuid, CONNECTOR_UUID)), FIELDS));
    }

    @Override
    public String getConnectorInfo(String uuid) {
        return run("getting connector information", () -> {
            String id = uuid(uuid, CONNECTOR_UUID);
            if (!"v2".equals(api.get(V2_CONNECTORS_UUID_ENDPOINT, id).path(VERSION).asString())) {
                return "Connector information requires connector framework v2.";
            }
            return describe("Connector information", api.get("/v2/connectors/{uuid}/info", id), "id", "name", VERSION,
                    "description");
        });
    }

    @Override
    public String getConnectorHealth(String uuid) {
        return run("getting connector health", () -> describe("Connector health",
                api.get("/v2/connectors/{uuid}/health", uuid(uuid, CONNECTOR_UUID)), STATUS));
    }

    @Override
    public String getConnectorAuthenticationAttributes(String authType) {
        return run("getting connector authentication settings", () -> {
            String type = authType(authType);
            return ConfigurationAttributes
                    .settings("Connector authentication attributes",
                            type.equals("none")
                                    ? JSON.createArrayNode()
                                    : api.get("/v1/connectors/auth/attributes/{type}", type));
        });
    }

    @Override
    public String createConnector(String configuration) {
        return run("creating connector", () -> {
            ObjectNode input = AdministrationInput
                    .configuration(configuration, "name", VERSION, "url", AUTH_TYPE, AUTH_ATTRIBUTES, CUSTOM_ATTRIBUTES,
                            PROXY_UUID);
            String name = required(input.path("name").asString(null), "Connector name");
            String version = input.path(VERSION).asString();
            if (!Set.of("v1", "v2").contains(version)) {
                throw new IllegalArgumentException("Connector version must be v1 or v2.");
            }
            ObjectNode body = connection(input, false);
            body.put("name", name).put(VERSION, version);
            body
                    .set(CUSTOM_ATTRIBUTES,
                            ConfigurationAttributes.encode(api.get(CUSTOM), values(input, CUSTOM_ATTRIBUTES)));
            return describe("Connector created", api.post("/v2/connectors", body), FIELDS);
        });
    }

    @Override
    public String updateConnector(String uuid, String configuration) {
        return run("updating connector", () -> {
            String id = uuid(uuid, CONNECTOR_UUID);
            ObjectNode input = AdministrationInput
                    .configuration(configuration, "url", AUTH_TYPE, AUTH_ATTRIBUTES, CUSTOM_ATTRIBUTES, PROXY_UUID,
                            NO_PROXY);
            ObjectNode body = connection(input, true);
            JsonNode existing = api.get(V2_CONNECTORS_UUID_ENDPOINT, id);
            body
                    .set(CUSTOM_ATTRIBUTES,
                            ConfigurationAttributes
                                    .replace(api.get(CUSTOM), existing.path(CUSTOM_ATTRIBUTES),
                                            values(input, CUSTOM_ATTRIBUTES)));
            return describe("Connector updated", api.put(V2_CONNECTORS_UUID_ENDPOINT, body, id), FIELDS);
        });
    }

    @Override
    public String testConnectorConnection(String configuration) {
        return run("probing connector", () -> {
            ObjectNode input = AdministrationInput
                    .configuration(configuration, "url", AUTH_TYPE, AUTH_ATTRIBUTES, PROXY_UUID, "uuid");
            ObjectNode body = connection(input, false);
            if (body.has(PROXY_UUID)) {
                JsonNode proxy = api.get("/v1/proxies/{uuid}", body.path(PROXY_UUID).asString());
                body.remove(PROXY_UUID);
                body.set("proxy", proxy);
            }
            if (input.has("uuid")) {
                body.put("uuid", uuid(input.path("uuid").asString(null), CONNECTOR_UUID));
            }
            return connectionResult("Connector connection probe", api.post("/v2/connectors/connect", body));
        });
    }

    @Override
    public String reconnectConnector(String uuid) {
        return run("reconnecting connector", () -> connectionResult("Connector reconnect",
                api.post("/v2/connectors/{uuid}/reconnect", null, uuid(uuid, CONNECTOR_UUID))));
    }

    @Override
    public String approveConnector(String uuid) {
        return run("approving connector", () -> describe("Connector approved",
                api.patch("/v2/connectors/{uuid}/approve", null, uuid(uuid, CONNECTOR_UUID))));
    }

    @Override
    public String deleteConnector(String uuid) {
        return run("deleting connector", () -> {
            api.deleteWithDependencies(V2_CONNECTORS_UUID_ENDPOINT, uuid(uuid, CONNECTOR_UUID));
            return "Connector deleted.";
        });
    }

    private ObjectNode connection(ObjectNode input, boolean update) {
        ObjectNode body = JSON.createObjectNode();
        String url = required(input.path("url").asString(null), "Connector URL");
        try {
            URI parsed = URI.create(url);
            if (!Set.of("http", "https").contains(parsed.getScheme()) || parsed.getHost() == null
                    || parsed.getUserInfo() != null || parsed.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Connector URL must be an HTTP(S) URL without embedded credentials or fragment.");
        }
        String type = authType(input.path(AUTH_TYPE).asString(null));
        if (update && !input.hasNonNull(PROXY_UUID) && !input.path(NO_PROXY).asBoolean(false)) {
            throw new IllegalArgumentException(
                    "Specify proxyUuid or noProxy=true to choose the replacement proxy routing.");
        }
        if (input.has(NO_PROXY) && !input.path(NO_PROXY).isBoolean()) {
            throw new IllegalArgumentException("noProxy must be true or false.");
        }
        if (input.hasNonNull(PROXY_UUID) && input.path(NO_PROXY).asBoolean(false)) {
            throw new IllegalArgumentException("Choose either proxyUuid or noProxy=true.");
        }
        if (input.hasNonNull(PROXY_UUID)) {
            versions.unsupported(IlmFeature.CONNECTOR_PROXY).ifPresent(message -> {
                throw new IllegalArgumentException(message);
            });
            body.put(PROXY_UUID, uuid(input.path(PROXY_UUID).asString(null), "Proxy UUID"));
        }
        body.put("url", url).put(AUTH_TYPE, type);
        if (!type.equals("none") && !input.hasNonNull(AUTH_ATTRIBUTES)) {
            throw new IllegalArgumentException(
                    "Authentication requires fresh authAttributes, including password or other protected values.");
        }
        JsonNode definitions = type.equals("none")
                ? JSON.createArrayNode()
                : api.get("/v1/connectors/auth/attributes/{type}", type);
        body.set(AUTH_ATTRIBUTES, ConfigurationAttributes.encode(definitions, values(input, AUTH_ATTRIBUTES)));
        return body;
    }

    private static String authType(String type) {
        String value = type == null ? "" : type;
        if (!Set.of("none", "basic", "certificate", "apiKey").contains(value)) {
            throw new IllegalArgumentException(
                    "Supported connector authentication types are none, basic, certificate and apiKey; JWT is not implemented by ILM.");
        }
        return value;
    }

    private static String values(JsonNode input, String key) {
        return input.has(key) ? input.path(key).toString() : "{}";
    }

    private static String connectionResult(String title, JsonNode result) {
        if (result.isArray()) {
            StringBuilder output = new StringBuilder();
            for (JsonNode entry : result) {
                output.append(connectionResult(title, entry)).append('\n');
            }
            return result.isEmpty() ? title + ": no connection result returned." : output.toString();
        }
        String outcome = result.hasNonNull("errorMessage") ? " failed" : " completed";
        return describe(title + outcome, result, VERSION, "connectorUuid", STATUS, "connector.id", "connector.name",
                "interfaces");
    }
}
