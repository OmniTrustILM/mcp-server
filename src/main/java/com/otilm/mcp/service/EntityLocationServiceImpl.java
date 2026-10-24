package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.api.model.core.auth.Resource;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.mcp.client.ConfigurationApiClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class EntityLocationServiceImpl implements EntityLocationService {
    private static final String CERTIFICATES_CERTIFICATE_UUID_ENDPOINT = "/certificates/{certificateUuid}";
    private static final String ATTRIBUTES = "attributes";
    private static final String CERTIFICATE_CUSTOM_ATTRIBUTES = "certificateCustomAttributes";
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String CSR_ATTRIBUTES = "csrAttributes";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String ENABLED = "enabled";
    private static final String ISSUE_ATTRIBUTES = "issueAttributes";

    private static final String ENTITY = "/v1/entities/{entityUuid}";
    private static final String LOCATION = ENTITY + "/locations/{locationUuid}";
    private static final String[] ENTITY_FIELDS = {"uuid", "name", CONNECTOR_UUID, "connectorName", "kind"};
    private static final String[] LOCATION_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "entityInstanceUuid",
            "entityInstanceName",
            ENABLED,
            "supportMultipleEntries",
            "supportKeyManagement",
            "state",
            "status"};
    private final ConfigurationApiClient client;

    public EntityLocationServiceImpl(ConfigurationApiClient client) {
        this.client = client;
    }

    @Override
    public String getEntity(String uuid) {
        return ConfigurationSupport
                .run("retrieving entity", () -> ConfigurationSupport
                        .describe("Entity", client.get(ENTITY, CryptoInput.uuid(uuid)), ENTITY_FIELDS));
    }

    @Override
    public String getEntityAttributes(String connectorUuid, String kind, String values) {
        return ConfigurationSupport
                .run("retrieving entity settings", () -> ConfigurationAttributes
                        .settings("Entity settings", entityDefinitions(connectorUuid, kind, values)));
    }

    private JsonNode entityDefinitions(String connectorUuid, String kind, String values) {
        return entityDefinitions(connectorUuid, kind, values, null);
    }

    private JsonNode entityDefinitions(String connectorUuid, String kind, String values, JsonNode existing) {
        CryptoInput.uuid(connectorUuid);
        ConfigurationSupport.required(kind, "Entity provider kind");
        JsonNode definitions = client
                .get("/v1/connectors/{uuid}/attributes/entityProvider/{kind}", connectorUuid, kind);
        var context = new ConfigurationAttributes.ConnectorContext(connectorUuid, FunctionGroupCode.ENTITY_PROVIDER,
                kind);
        return existing == null
                ? ConfigurationAttributes.resolve(client, context, definitions, values)
                : ConfigurationAttributes.resolveReplacement(client, context, definitions, existing, values);
    }

    private void provider(String connectorUuid, String kind) {
        JsonNode connector = client.get("/v1/connectors/{uuid}", CryptoInput.uuid(connectorUuid));
        boolean found = false;
        for (JsonNode group : connector.path("functionGroups")) {
            if ("entityProvider".equals(group.path("functionGroupCode").asString())) {
                for (JsonNode offered : group.path("kinds")) {
                    found |= kind.equals(offered.asString());
                }
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Connector does not offer the requested entity kind.");
        }
    }

    @Override
    public String createEntity(String configuration) {
        return entityWrite(null, configuration);
    }

    @Override
    public String updateEntity(String uuid, String configuration) {
        return entityWrite(uuid, configuration);
    }

    private String entityWrite(String uuid, String configuration) {
        return ConfigurationSupport.run("saving entity", () -> {
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", CONNECTOR_UUID, "kind", ATTRIBUTES, CUSTOM_ATTRIBUTES);
            if (uuid != null) {
                rejectEntityIdentityChanges(body);
            }
            CryptoInput.text(body, "name", CONNECTOR_UUID, "kind");
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(ENTITY, CryptoInput.uuid(uuid));
            JsonNode references = uuid == null ? body : existing;
            if (uuid == null) {
                body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Entity name"));
            }
            String connector = CryptoInput.uuid(references.path(CONNECTOR_UUID).asString(null));
            String kind = ConfigurationSupport.required(references.path("kind").asString(null), "Entity kind");
            provider(connector, kind);
            String values = CryptoInput.values(body, ATTRIBUTES);
            body
                    .set(ATTRIBUTES,
                            ConfigurationAttributes
                                    .replace(entityDefinitions(connector, kind, values, previous(existing, ATTRIBUTES)),
                                            previous(existing, ATTRIBUTES), values));
            body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "entities", existing, body));
            JsonNode result = uuid == null ? client.post("/v1/entities", body) : client.put(ENTITY, body, uuid);
            return ConfigurationSupport.describe("Entity saved", result, ENTITY_FIELDS);
        });
    }

    private static void rejectEntityIdentityChanges(JsonNode body) {
        for (String field : new String[]{"name", CONNECTOR_UUID, "kind"}) {
            if (body.has(field)) {
                throw new IllegalArgumentException("Entity " + field + " cannot be updated.");
            }
        }
    }

    @Override
    public String deleteEntity(String uuid) {
        return ConfigurationSupport.run("deleting entity", () -> {
            client.delete(ENTITY, CryptoInput.uuid(uuid));
            return "Deleted entity [" + uuid + "].";
        });
    }

    @Override
    public String searchLocations(String filters, Integer itemsPerPage, Integer pageNumber) {
        return ConfigurationSupport
                .run("searching locations", () -> ConfigurationSupport
                        .list("Locations",
                                client
                                        .post("/v1/locations",
                                                SearchRequests
                                                        .of(new SearchRequestDto(), filters, itemsPerPage, pageNumber)),
                                LOCATION_FIELDS));
    }

    @Override
    public String getLocationSearchableFields() {
        return ConfigurationSupport
                .run("retrieving location searchable fields",
                        () -> ConfigurationSupport
                                .list("Location searchable fields", client.get("/v1/locations/search"), "name",
                                        "fieldIdentifier", "fields"));
    }

    @Override
    public String getLocation(String entityUuid, String locationUuid) {
        return ConfigurationSupport
                .run("retrieving location", () -> locationText("Location",
                        client.get(LOCATION, CryptoInput.uuid(entityUuid), CryptoInput.uuid(locationUuid))));
    }

    private String locationText(String title, JsonNode result) {
        StringBuilder output = new StringBuilder(ConfigurationSupport.describe(title, result, LOCATION_FIELDS));
        for (JsonNode certificate : result.path("certificates")) {
            output
                    .append(ConfigurationSupport
                            .describe("Certificate association", certificate, "uuid", "certificateUuid", "state",
                                    "status", "commonName"));
        }
        return output.toString();
    }

    private JsonNode locationDefinitions(String entityUuid, String suffix, String locationUuid, String values) {
        return locationDefinitions(entityUuid, suffix, locationUuid, values, null);
    }

    private JsonNode locationDefinitions(String entityUuid, String suffix, String locationUuid, String values,
            JsonNode existing) {
        CryptoInput.uuid(entityUuid);
        JsonNode definitions = locationUuid == null
                ? client.get(ENTITY + "/attributes/location", entityUuid)
                : client.get(LOCATION + "/attributes/" + suffix, entityUuid, CryptoInput.uuid(locationUuid));
        var context = new ConfigurationAttributes.ResourceContext(Resource.LOCATION, entityUuid);
        return existing == null
                ? ConfigurationAttributes.resolve(client, context, definitions, values)
                : ConfigurationAttributes.resolveReplacement(client, context, definitions, existing, values);
    }

    @Override
    public String getLocationAttributes(String entityUuid, String values) {
        return ConfigurationSupport
                .run("retrieving location settings", () -> ConfigurationAttributes
                        .settings("Location settings", locationDefinitions(entityUuid, null, null, values)));
    }

    @Override
    public String validateLocationAttributes(String entityUuid, String attributes) {
        return ConfigurationSupport.run("validating location settings", () -> {
            ArrayNode body = ConfigurationAttributes
                    .encode(locationDefinitions(entityUuid, null, null, attributes), attributes);
            client.post(ENTITY + "/attributes/location/validate", body, entityUuid);
            return "Location attributes validated for entity [" + entityUuid + "].";
        });
    }

    @Override
    public String createLocation(String entityUuid, String configuration) {
        return locationWrite(entityUuid, null, configuration);
    }

    @Override
    public String updateLocation(String entityUuid, String locationUuid, String configuration) {
        return locationWrite(entityUuid, locationUuid, configuration);
    }

    private String locationWrite(String entityUuid, String locationUuid, String configuration) {
        return ConfigurationSupport.run("saving location", () -> {
            CryptoInput.uuid(entityUuid);
            ObjectNode body = locationUuid == null
                    ? CryptoInput
                            .configuration(configuration, "name", DESCRIPTION, ENABLED, ATTRIBUTES, CUSTOM_ATTRIBUTES)
                    : CryptoInput.configuration(configuration, DESCRIPTION, ENABLED, ATTRIBUTES, CUSTOM_ATTRIBUTES);
            CryptoInput.text(body, "name", DESCRIPTION);
            CryptoInput.bool(body, ENABLED);
            JsonNode existing = locationUuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(LOCATION, entityUuid, CryptoInput.uuid(locationUuid));
            CryptoInput.preserve(body, existing, DESCRIPTION, ENABLED);
            if (locationUuid == null) {
                body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Location name"));
            }
            String values = CryptoInput.values(body, ATTRIBUTES);
            body
                    .set(ATTRIBUTES,
                            ConfigurationAttributes
                                    .replace(
                                            locationDefinitions(entityUuid, null, null, values,
                                                    previous(existing, ATTRIBUTES)),
                                            previous(existing, ATTRIBUTES), values));
            body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "locations", existing, body));
            JsonNode result = locationUuid == null
                    ? client.post(ENTITY + "/locations", body, entityUuid)
                    : client.put(LOCATION, body, entityUuid, locationUuid);
            return locationText("Location saved", result);
        });
    }

    @Override
    public String deleteLocation(String entityUuid, String locationUuid) {
        return ConfigurationSupport.run("deleting location", () -> {
            client.delete(LOCATION, CryptoInput.uuid(entityUuid), CryptoInput.uuid(locationUuid));
            return "Deleted location [" + locationUuid + "]; this removes the ILM location association.";
        });
    }

    @Override
    public String enableLocation(String entityUuid, String locationUuid) {
        return locationState(entityUuid, locationUuid, "enable");
    }

    @Override
    public String disableLocation(String entityUuid, String locationUuid) {
        return locationState(entityUuid, locationUuid, "disable");
    }

    private String locationState(String entityUuid, String locationUuid, String operation) {
        return ConfigurationSupport.run(operation + " location", () -> {
            client
                    .patch(LOCATION + "/" + operation, null, CryptoInput.uuid(entityUuid),
                            CryptoInput.uuid(locationUuid));
            return "Location " + operation + " request accepted [" + locationUuid + "].";
        });
    }

    @Override
    public String getLocationPushAttributes(String entityUuid, String locationUuid, String values) {
        return operationSettings(entityUuid, locationUuid, "push", values);
    }

    @Override
    public String getLocationCsrAttributes(String entityUuid, String locationUuid, String values) {
        return operationSettings(entityUuid, locationUuid, "issue", values);
    }

    private String operationSettings(String entityUuid, String locationUuid, String operation, String values) {
        return ConfigurationSupport
                .run("retrieving location operation settings",
                        () -> ConfigurationAttributes
                                .settings("Location " + operation + " settings",
                                        locationDefinitions(entityUuid, operation, locationUuid, values)));
    }

    @Override
    public String pushCertificateToLocation(String entityUuid, String locationUuid, String certificateUuid,
            String attributes) {
        return ConfigurationSupport.run("pushing certificate to location", () -> {
            CryptoInput.uuid(certificateUuid);
            ObjectNode body = ConfigurationAttributes.JSON.createObjectNode();
            body
                    .set(ATTRIBUTES, ConfigurationAttributes
                            .encode(locationDefinitions(entityUuid, "push", locationUuid, attributes), attributes));
            return accepted(client
                    .put(LOCATION + CERTIFICATES_CERTIFICATE_UUID_ENDPOINT, body, entityUuid, locationUuid,
                            certificateUuid));
        });
    }

    @Override
    public String removeCertificateFromLocation(String entityUuid, String locationUuid, String certificateUuid) {
        return ConfigurationSupport.run("removing certificate from location", () -> {
            client
                    .delete(LOCATION + CERTIFICATES_CERTIFICATE_UUID_ENDPOINT, CryptoInput.uuid(entityUuid),
                            CryptoInput.uuid(locationUuid), CryptoInput.uuid(certificateUuid));
            return "Certificate removal request accepted; provider certificate/key material may be removed from location ["
                    + locationUuid + "].";
        });
    }

    @Override
    public String renewCertificateInLocation(String entityUuid, String locationUuid, String certificateUuid) {
        return ConfigurationSupport
                .run("renewing location certificate", () -> accepted(client
                        .patch(LOCATION + CERTIFICATES_CERTIFICATE_UUID_ENDPOINT, null, CryptoInput.uuid(entityUuid),
                                CryptoInput.uuid(locationUuid), CryptoInput.uuid(certificateUuid))));
    }

    @Override
    public String syncLocation(String entityUuid, String locationUuid) {
        return ConfigurationSupport
                .run("synchronizing location", () -> accepted(client
                        .put(LOCATION + "/sync", null, CryptoInput.uuid(entityUuid), CryptoInput.uuid(locationUuid))));
    }

    @Override
    public String issueCertificateToLocation(String entityUuid, String locationUuid, String configuration) {
        return ConfigurationSupport.run("issuing certificate to location", () -> {
            CryptoInput.uuid(entityUuid);
            CryptoInput.uuid(locationUuid);
            ObjectNode body = CryptoInput
                    .configuration(configuration, "raProfileUuid", CSR_ATTRIBUTES, ISSUE_ATTRIBUTES, CUSTOM_ATTRIBUTES,
                            CERTIFICATE_CUSTOM_ATTRIBUTES);
            String profile = CryptoInput.uuid(body.path("raProfileUuid").asString(null));
            JsonNode ra = client.get("/v1/raProfiles/{uuid}", profile);
            String authority = CryptoInput.uuid(ra.path("authorityInstanceUuid").asString(null));
            body
                    .set(CSR_ATTRIBUTES,
                            ConfigurationAttributes
                                    .encode(locationDefinitions(entityUuid, "issue", locationUuid,
                                            CryptoInput.values(body, CSR_ATTRIBUTES)),
                                            CryptoInput.values(body, CSR_ATTRIBUTES)));
            JsonNode issue = client
                    .get("/v1/authorities/{authorityUuid}/raProfiles/{profileUuid}/attributes/issue", authority,
                            profile);
            body
                    .set(ISSUE_ATTRIBUTES, ConfigurationAttributes
                            .encode(client, new ConfigurationAttributes.ResourceContext(Resource.RA_PROFILE, authority),
                                    issue, CryptoInput.values(body, ISSUE_ATTRIBUTES)));
            body
                    .set(CUSTOM_ATTRIBUTES, CryptoInput
                            .custom(client, "locations", ConfigurationAttributes.JSON.createObjectNode(), body));
            ObjectNode certificate = ConfigurationAttributes.JSON.createObjectNode();
            if (body.has(CERTIFICATE_CUSTOM_ATTRIBUTES)) {
                certificate.set(CUSTOM_ATTRIBUTES, body.path(CERTIFICATE_CUSTOM_ATTRIBUTES));
            }
            body
                    .set(CERTIFICATE_CUSTOM_ATTRIBUTES,
                            CryptoInput
                                    .custom(client, "certificates", ConfigurationAttributes.JSON.createObjectNode(),
                                            certificate));
            return accepted(client.post(LOCATION + "/certificates", body, entityUuid, locationUuid));
        });
    }

    private String accepted(JsonNode result) {
        return locationText(
                "Location provider operation accepted; inspect actual certificate state and pending processing",
                result);
    }

    private JsonNode previous(JsonNode existing, String field) {
        return existing.path(field).isArray() ? existing.path(field) : ConfigurationAttributes.JSON.createArrayNode();
    }
}
