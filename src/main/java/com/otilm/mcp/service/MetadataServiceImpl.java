package com.otilm.mcp.service;

import com.otilm.api.model.common.attribute.common.content.AttributeContentType;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;
import static com.otilm.mcp.service.ConfigurationSupport.uuid;

@Service
public class MetadataServiceImpl implements MetadataService {
    private static final String METADATA_UUID = "Metadata UUID";
    private static final String CONTENT_TYPE = "contentType";
    private static final String DESCRIPTION = "description";
    private static final String GROUP = "group";
    private static final String LABEL = "label";
    private static final String VISIBLE = "visible";

    private static final String METADATA_ENDPOINT = "/v1/attributes/metadata/{uuid}";
    private static final String[] FIELDS = {
            "uuid",
            "name",
            LABEL,
            DESCRIPTION,
            CONTENT_TYPE,
            VISIBLE,
            GROUP,
            "connectorUuid"};
    private final ConfigurationApiClient api;

    public MetadataServiceImpl(ConfigurationApiClient api) {
        this.api = api;
    }

    @Override
    public String listGlobalMetadata() {
        return run("listing global metadata",
                () -> list("Global metadata", api.get("/v1/attributes/metadata"), FIELDS));
    }

    @Override
    public String getGlobalMetadata(String uuid) {
        return run("getting global metadata",
                () -> describe("Global metadata", api.get(METADATA_ENDPOINT, uuid(uuid, METADATA_UUID)), FIELDS));
    }

    @Override
    public String createGlobalMetadata(String configuration) {
        return run("creating global metadata", () -> {
            ObjectNode body = AdministrationInput
                    .configuration(configuration, "name", LABEL, CONTENT_TYPE, DESCRIPTION, VISIBLE, GROUP);
            body.put("name", required(body.path("name").asString(null), "Metadata name"));
            String type = required(body.path(CONTENT_TYPE).asString(null), "Content type");
            if (Arrays.stream(AttributeContentType.values()).noneMatch(value -> value.getCode().equals(type))) {
                throw new IllegalArgumentException("Unknown metadata content type.");
            }
            validate(body);
            return describe("Global metadata created", api.post("/v1/attributes/metadata", body), FIELDS);
        });
    }

    @Override
    public String updateGlobalMetadata(String uuid, String configuration) {
        return run("updating global metadata", () -> {
            String id = uuid(uuid, METADATA_UUID);
            ObjectNode patch = AdministrationInput.configuration(configuration, LABEL, DESCRIPTION, VISIBLE, GROUP);
            ObjectNode body = AdministrationInput
                    .select(api.get(METADATA_ENDPOINT, id), LABEL, DESCRIPTION, VISIBLE, GROUP);
            patch.properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));
            validate(body);
            return describe("Global metadata updated", api.put(METADATA_ENDPOINT, body, id), FIELDS);
        });
    }

    @Override
    public String deleteGlobalMetadata(String uuid) {
        return run("deleting global metadata", () -> {
            api.delete(METADATA_ENDPOINT, uuid(uuid, METADATA_UUID));
            return "Global metadata deleted.";
        });
    }

    @Override
    public String listConnectorMetadata(String connectorUuid) {
        return run("listing connector metadata",
                () -> list("Connector metadata available for promotion",
                        connectorUuid == null || connectorUuid.isBlank()
                                ? api.get("/v1/attributes/metadata/promote")
                                : api
                                        .get("/v1/attributes/metadata/promote?connectorUuid={uuid}",
                                                uuid(connectorUuid, "Connector UUID")),
                        FIELDS));
    }

    @Override
    public String promoteConnectorMetadata(String connectorUuid, String metadataUuid) {
        return run("promoting connector metadata", () -> {
            ObjectNode body = AdministrationInput.JSON
                    .createObjectNode()
                    .put("uuid", uuid(metadataUuid, METADATA_UUID))
                    .put("connectorUuid", uuid(connectorUuid, "Connector UUID"));
            return describe("Connector metadata promoted", api.post("/v1/attributes/metadata/promote", body), FIELDS);
        });
    }

    private static void validate(ObjectNode body) {
        AdministrationInput.text(body, "name", CONTENT_TYPE, LABEL, DESCRIPTION, GROUP);
        AdministrationInput.bool(body, VISIBLE);
        body.put(LABEL, required(body.path(LABEL).asString(null), "Metadata label"));
    }
}
