package com.otilm.mcp.service;

import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.HashSet;
import java.util.Set;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.strings;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.object;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class DiscoveryManagementServiceImpl implements DiscoveryManagementService {
    private static final String ATTRIBUTES = "attributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String INTERFACE_UUID = "interfaceUuid";
    private static final String RESOURCE = "resource";
    private static final String RESOURCE_ATTRIBUTES = "resourceAttributes";
    private static final String RESOURCES = "resources";
    private static final String TRIGGERS = "triggers";

    private static final String BASE = "/v1/discoveries";
    private static final String ITEM = BASE + "/{uuid}";
    private static final String[] META = {
            "uuid",
            "name",
            "kind",
            "status",
            "connectorStatus",
            "startTime",
            "endTime",
            CONNECTOR_UUID,
            "connectorName",
            "totalCertificatesDiscovered",
            RESOURCES,
            "itemsDiscovered",
            "itemsNewlyDiscovered",
            "itemsProcessed",
            "itemsFailed",
            "runMessageCount",
            "stoppable"};
    private final ConfigurationApiClient client;
    private final PlatformVersionCheck versions;

    public DiscoveryManagementServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    @Override
    public String getDiscovery(String uuid) {
        return run("reading discovery", () -> describe("Discovery", client.get(ITEM, id(uuid)), META));
    }

    @Override
    public String getDiscoveryCertificates(String uuid, Boolean newlyDiscovered, int page, int size) {
        return run("reading discovery certificates", () -> {
            id(uuid);
            page(page, size);
            String path = ITEM + "/certificates?pageNumber={page}&itemsPerPage={size}";
            JsonNode result = newlyDiscovered == null
                    ? client.get(path, uuid, page, size)
                    : client.get(path + "&newlyDiscovered={new}", uuid, page, size, newlyDiscovered);
            return list("Discovery certificates", result, "uuid", "inventoryUuid", "certificateUuid", "commonName",
                    "subjectDn", "serialNumber", "fingerprint", "newlyDiscovered", "processed");
        });
    }

    @Override
    public String getDiscoveryItems(String uuid, String resource, Boolean newlyDiscovered, int page, int size) {
        return run("reading discovery items", () -> {
            id(uuid);
            page(page, size);
            extended();
            String path = ITEM + "/items?pageNumber={page}&itemsPerPage={size}";
            java.util.List<Object> coordinates = new java.util.ArrayList<>(java.util.List.of(uuid, page, size));
            if (resource != null) {
                resource(resource);
                path += "&resource={resource}";
                coordinates.add(resource);
            }
            if (newlyDiscovered != null) {
                path += "&newlyDiscovered={new}";
                coordinates.add(newlyDiscovered);
            }
            return list("Discovery items", client.get(path, coordinates.toArray()), "uuid", "inventory.uuid",
                    "inventory.name", "sequence", "uniqueRef", "discoveredAt", RESOURCE, "newlyDiscovered",
                    "processed");
        });
    }

    @Override
    public String getDiscoveryMessages(String uuid, int page, int size) {
        return run("reading discovery messages", () -> {
            id(uuid);
            page(page, size);
            extended();
            return list("Discovery messages",
                    client.get(ITEM + "/messages?pageNumber={page}&itemsPerPage={size}", uuid, page, size), "severity",
                    "code", "message", RESOURCE, "occurrences", "firstSeenAt", "lastSeenAt");
        });
    }

    @Override
    public String listDiscoveryResources(String connectorUuid) {
        return run("reading discovery resources", () -> {
            id(connectorUuid);
            extended();
            return list("Discoverable resources", client.get(BASE + "/{connector}/resources", connectorUuid), RESOURCE);
        });
    }

    @Override
    public String getDiscoveryAttributes(String connectorUuid, String kind, String interfaceUuid, String resource,
            String values) {
        return run("reading discovery settings", () -> {
            id(connectorUuid);
            JsonNode definitions = schema(connectorUuid, kind, interfaceUuid, resource);
            return ConfigurationAttributes
                    .settings("Discovery settings",
                            ConfigurationAttributes
                                    .resolve(client, context(connectorUuid, kind, interfaceUuid, definitions),
                                            definitions, values));
        });
    }

    @Override
    public String startDiscovery(String configuration) {
        return run("starting discovery", () -> {
            ObjectNode request = request(configuration);
            JsonNode result = client.post(BASE, request);
            String uuid = result.path("uuid").asString(null);
            String output = "Discovery run accepted" + (uuid == null ? "." : ": " + id(uuid) + ".");
            if (uuid != null) {
                try {
                    output += "\n" + describe("Current discovery state", client.get(ITEM, uuid), META);
                } catch (RuntimeException unknown) {
                    output += "\nCurrent run state could not be read.";
                }
            }
            return output + "\nDiscovery processing may remain pending; inspect run state and results.";
        });
    }

    @Override
    public String scheduleDiscovery(String jobName, String cronExpression, boolean oneTime, String configuration) {
        return run("scheduling discovery", () -> {
            required(jobName, "Job name");
            if (cronExpression == null || !CronExpression.isValidExpression(cronExpression)) {
                throw new IllegalArgumentException("cronExpression must be a valid Quartz cron expression.");
            }
            ObjectNode body = JSON.createObjectNode();
            body.put("jobName", jobName);
            body.put("cronExpression", cronExpression);
            body.put("oneTime", oneTime);
            body.set("request", request(configuration));
            return describe("Discovery scheduled; use scheduler job tools to inspect and maintain this job",
                    client.post(BASE + "/schedule", body), "uuid")
                    + "\nThe job stores this discovery request. Editing job cron later does not change the stored provider request.";
        });
    }

    @Override
    public String controlDiscovery(String uuid, String action) {
        return run("controlling discovery", () -> {
            id(uuid);
            extended();
            if (!Set.of("stop", "resume", "cancel").contains(action)) {
                throw new IllegalArgumentException("Discovery control must be stop, resume or cancel.");
            }
            String path = switch (action) {
                case "stop" -> ITEM + "/stop";
                case "resume" -> ITEM + "/resume";
                default -> ITEM + "/cancel";
            };
            client.patch(path, null, uuid);
            String result = "Discovery " + action + " request accepted for " + uuid + ".";
            try {
                result += "\n" + describe("Current run state", client.get(ITEM, uuid), META);
            } catch (RuntimeException unknown) {
                result += "\nCurrent run state could not be read.";
            }
            return result + "\nInspect run state before treating this request as complete.";
        });
    }

    @Override
    public String deleteDiscovery(String uuid) {
        return run("deleting discovery", () -> {
            client.delete(ITEM, id(uuid));
            return "Deleted discovery " + uuid + ".";
        });
    }

    private ObjectNode request(String configuration) {
        ObjectNode input = configuration(configuration, "name", CONNECTOR_UUID, INTERFACE_UUID, "kind", ATTRIBUTES,
                CUSTOM_ATTRIBUTES, TRIGGERS, RESOURCES, RESOURCE_ATTRIBUTES);
        text(input, "name", CONNECTOR_UUID, INTERFACE_UUID, "kind");
        required(input.path("name").asString(null), "Discovery name");
        String connector = id(input.path(CONNECTOR_UUID).asString(null));
        String kind = input.path("kind").asString(null);
        String iface = input.path(INTERFACE_UUID).asString(null);
        boolean modern = input.has(RESOURCES) || input.has(RESOURCE_ATTRIBUTES) || input.has(INTERFACE_UUID);
        if (modern) {
            configureResources(input, connector, kind, iface);
        } else {
            required(kind, "Discovery kind");
        }
        JsonNode definitions = schema(connector, kind, iface, null);
        input
                .set(ATTRIBUTES,
                        ConfigurationAttributes
                                .encode(client, context(connector, kind, iface, definitions), definitions,
                                        input.has(ATTRIBUTES) ? input.path(ATTRIBUTES).toString() : "{}"));
        input
                .set(CUSTOM_ATTRIBUTES, ConfigurationAttributes
                        .encode(client.get("/v1/attributes/custom/resources/discoveries"),
                                input.has(CUSTOM_ATTRIBUTES) ? input.path(CUSTOM_ATTRIBUTES).toString() : "{}"));
        configureClassificationTriggers(input);
        return input;
    }

    private void configureResources(ObjectNode input, String connector, String kind, String iface) {
        extended();
        if (iface != null) {
            id(iface);
        }
        if (kind != null) {
            throw new IllegalArgumentException("Resource discovery does not accept a legacy kind.");
        }
        ArrayNode selected = strings(input.path(RESOURCES), RESOURCES, false);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one discoverable resource.");
        }
        JsonNode available = client.get(BASE + "/{connector}/resources", connector);
        ObjectNode values = input.has(RESOURCE_ATTRIBUTES)
                ? object(input.path(RESOURCE_ATTRIBUTES).toString(), "Resource attributes")
                : JSON.createObjectNode();
        validateResourceValues(selected, values);
        ObjectNode encoded = encodeResourceAttributes(connector, iface, selected, values, available);
        input.set(RESOURCES, selected);
        input.set(RESOURCE_ATTRIBUTES, encoded);
    }

    private static void validateResourceValues(ArrayNode selected, ObjectNode values) {
        Set<String> selectedCodes = new HashSet<>();
        selected.forEach(entry -> selectedCodes.add(entry.asString()));
        values.properties().forEach(entry -> {
            if (!selectedCodes.contains(entry.getKey())) {
                throw new IllegalArgumentException("Resource attribute values must belong to selected resources.");
            }
        });
    }

    private ObjectNode encodeResourceAttributes(String connector, String iface, ArrayNode selected, ObjectNode values,
            JsonNode available) {
        ObjectNode encoded = JSON.createObjectNode();
        for (JsonNode item : selected) {
            String resource = item.asString();
            requireOfferedResource(resource, available);
            JsonNode definitions = client.get(BASE + "/{connector}/{resource}/attributes", connector, resource);
            encoded
                    .set(resource,
                            ConfigurationAttributes
                                    .encode(client, context(connector, null, iface, definitions), definitions,
                                            values.has(resource) ? values.path(resource).toString() : "{}"));
        }
        return encoded;
    }

    private static void requireOfferedResource(String resource, JsonNode available) {
        boolean offered = false;
        for (JsonNode choice : available) {
            if (resource.equals(choice.path(RESOURCE).asString())) {
                offered = true;
            }
        }
        if (!offered) {
            throw new IllegalArgumentException(
                    "Selected discovery resource is unavailable; use list_discovery_resources.");
        }
    }

    private void configureClassificationTriggers(ObjectNode input) {
        if (!input.has(TRIGGERS)) {
            return;
        }
        ArrayNode triggers = strings(input.path(TRIGGERS), TRIGGERS, true);
        for (JsonNode trigger : triggers) {
            JsonNode definition = client.get("/v1/workflows/triggers/{uuid}", trigger.asString());
            if (!"certificates".equals(definition.path(RESOURCE).asString()) || definition.hasNonNull("event")
                    && !"certificate_discovered".equals(definition.path("event").asString())) {
                throw new IllegalArgumentException(
                        "Discovery classification triggers must target certificate_discovered on certificates; discovery_finished is a separate run event.");
            }
        }
        input.set(TRIGGERS, triggers);
    }

    private JsonNode schema(String connector, String kind, String iface, String resource) {
        if (resource != null) {
            extended();
            resource(resource);
            return client.get(BASE + "/{connector}/{resource}/attributes", connector, resource);
        }
        if (kind == null || iface != null) {
            extended();
            return client.get(BASE + "/{connector}/attributes", connector);
        }
        return client
                .get("/v1/connectors/{connector}/attributes/discoveryProvider/{kind}", connector,
                        required(kind, "Discovery kind"));
    }

    private static ConfigurationAttributes.CallbackContext context(String connector, String kind, String iface,
            JsonNode definitions) {
        if (iface != null) {
            return new ConfigurationAttributes.ConnectorInterfaceContext(connector, iface);
        }
        if (kind == null) {
            for (JsonNode definition : definitions) {
                if (definition.hasNonNull("attributeCallback")) {
                    throw new IllegalArgumentException(
                            "interfaceUuid is required to resolve discovery provider callbacks.");
                }
            }
            kind = "Discovery";
        }
        return new ConfigurationAttributes.ConnectorContext(connector, FunctionGroupCode.DISCOVERY_PROVIDER, kind);
    }

    private void extended() {
        versions.unsupported(IlmFeature.DISCOVERY_EXTENSIONS).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private static void resource(String value) {
        if (!Set.of("certificates", "keys", "cryptographicKeys", "secrets").contains(value)) {
            throw new IllegalArgumentException("Unsupported discovery resource code.");
        }
    }

    private static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 1000) {
            throw new IllegalArgumentException("Discovery page starts at 1; size must be between 1 and 1000.");
        }
    }
}
