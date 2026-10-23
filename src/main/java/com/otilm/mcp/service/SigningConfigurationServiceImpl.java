package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SigningConfigurationServiceImpl implements SigningConfigurationService {
    private static final String CONNECTOR_UUID = "connectorUuid";
    private static final String DESCRIPTION = "description";
    private static final String ENABLED = "enabled";
    private static final String FAMILY = "family";
    private static final String MANAGED = "managed";
    private static final String MAX_LEVEL = "maxLevel";
    private static final String RECORD_POLICY = "recordPolicy";
    private static final String SIGNATURE_FORMATTING_CONNECTOR_ATTRIBUTES = "signatureFormattingConnectorAttributes";
    private static final String SIGNATURE_FORMATTING_CONNECTOR_UUID = "signatureFormattingConnectorUuid";
    private static final String SIGNING_OPERATION_ATTRIBUTES = "signingOperationAttributes";
    private static final String SIGNING_SCHEME = "signingScheme";
    private static final String TIMESTAMP_SOURCE = "timestampSource";
    private static final String WORKFLOW = "workflow";

    private static final String BASE = "/v1/signingProfiles";
    private static final String PROFILE = BASE + "/{uuid}";
    private static final String[] RESULT = {
            "uuid",
            "name",
            DESCRIPTION,
            "version",
            ENABLED,
            "state",
            "status",
            "enabledProtocols"};
    private final ConfigurationApiClient client;
    private final PlatformVersionCheck versions;

    public SigningConfigurationServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    @Override
    public String getSigningSchemes() {
        return run("retrieving signing schemes",
                () -> "Signing schemes:\n- managed / static_key: certificateUuid and schema-driven signingOperationAttributes\n- managed / one_time_key: raProfileUuid, csrTemplateUuid, tokenProfileUuid\n- delegated: connectorUuid\nWorkflows: raw_signing, content_signing, timestamping. Provider operations remain subject to ILM authorization.\n");
    }

    @Override
    public String getSigningProtocols(String workflowType) {
        return run("retrieving signing protocols", () -> {
            SigningConfigurationInput.workflowType(workflowType);
            JsonNode protocols = client.get(BASE + "/supportedProtocols?signingWorkflowType={type}", workflowType);
            StringBuilder output = new StringBuilder("Supported signing protocols:\n");
            for (JsonNode protocol : protocols) {
                if (!protocol.isString()) {
                    throw new IllegalArgumentException("Invalid protocol capability response.");
                }
                output.append("- ").append(protocol.asString()).append('\n');
            }
            return output.toString();
        });
    }

    @Override
    public String getSigningCertificates(String workflowType) {
        return run("retrieving signing certificates", () -> {
            SigningConfigurationInput.workflowType(workflowType);
            return ConfigurationSupport
                    .list("Eligible signing certificates",
                            client.get(BASE + "/signingCertificates?signingWorkflowType={type}", workflowType), "uuid",
                            "commonName", "serialNumber", "subjectDn", "issuerDn", "notAfter", "state");
        });
    }

    @Override
    public String getSigningOperationAttributes(String certificateUuid) {
        return run("retrieving signing operation settings", () -> ConfigurationAttributes
                .settings("Signing operation settings", operationDefinitions(certificateUuid)));
    }

    private JsonNode operationDefinitions(String certificateUuid) {
        return client.get(BASE + "/certificates/{uuid}/signatureAttributes", CryptoInput.uuid(certificateUuid));
    }

    @Override
    public String getSigningFormattingAttributes(String connectorUuid, String family, String maxLevel, String values) {
        return run("retrieving signing formatting settings",
                () -> ConfigurationAttributes
                        .settings("Signing formatting settings",
                                formattingDefinitions(connectorUuid, family, maxLevel, values)));
    }

    private JsonNode formattingDefinitions(String connectorUuid, String family, String maxLevel, String values) {
        return formattingDefinitions(connectorUuid, family, maxLevel, values, null);
    }

    private JsonNode formattingDefinitions(String connectorUuid, String family, String maxLevel, String values,
            JsonNode existing) {
        CryptoInput.uuid(connectorUuid);
        JsonNode definitions;
        if (family != null || maxLevel != null) {
            require(IlmFeature.SIGNING_FORMATTING_EXTENSIONS);
            SigningConfigurationInput.family(family);
            SigningConfigurationInput.level(maxLevel);
            definitions = client
                    .get(BASE
                            + "/signatureFormattingConnectors/{uuid}/contentSigningFormattingAttributes?family={family}&maxLevel={level}",
                            connectorUuid, family, maxLevel);
        } else if (older219()) {
            definitions = client.get(BASE + "/signatureFormatterConnectors/{uuid}/formatterAttributes", connectorUuid);
        } else {
            definitions = client
                    .get(BASE + "/signatureFormattingConnectors/{uuid}/formattingAttributes", connectorUuid);
        }
        return resolveConnector(definitions, connectorUuid,
                family == null ? "signatureFormatting" : family + "Formatting", values, existing);
    }

    private JsonNode resolveConnector(JsonNode definitions, String connectorUuid, String code, String values,
            JsonNode existing) {
        boolean callbacks = false;
        for (JsonNode definition : definitions) {
            callbacks |= definition.path("attributeCallback").isObject();
        }
        if (!callbacks) {
            return definitions;
        }
        JsonNode connector = client.get("/v2/connectors/{uuid}", connectorUuid);
        String interfaceUuid = null;
        for (JsonNode candidate : connector.path("interfaces")) {
            if (code.equals(candidate.path("code").asString())) {
                if (interfaceUuid != null) {
                    throw new IllegalArgumentException("Ambiguous provider callback interface.");
                }
                interfaceUuid = candidate.path("uuid").asString(null);
            }
        }
        var context = new ConfigurationAttributes.ConnectorInterfaceContext(connectorUuid, interfaceUuid);
        return existing == null
                ? ConfigurationAttributes.resolve(client, context, definitions, values)
                : ConfigurationAttributes.resolveReplacement(client, context, definitions, existing, values);
    }

    @Override
    public String createSigningProfile(String configuration) {
        return save(null, configuration);
    }

    @Override
    public String updateSigningProfile(String uuid, String configuration) {
        return save(uuid, configuration);
    }

    private String save(String uuid, String configuration) {
        return run("saving signing profile", () -> {
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", DESCRIPTION, SIGNING_SCHEME, WORKFLOW, RECORD_POLICY,
                            "customAttributes");
            if (body.has(RECORD_POLICY)) {
                require(IlmFeature.SIGNING_RECORDS);
                if (!body.path(RECORD_POLICY).isObject()) {
                    throw new IllegalArgumentException("Signing record policy must be an object.");
                }
            }
            if (SigningConfigurationInput.extended(body.path(WORKFLOW))) {
                require(IlmFeature.SIGNING_FORMATTING_EXTENSIONS);
            }
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(PROFILE, CryptoInput.uuid(uuid));
            CryptoInput.text(body, "name", DESCRIPTION);
            CryptoInput.preserve(body, existing, "name", DESCRIPTION);
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Signing profile name"));
            ObjectNode scheme = scheme(body.path(SIGNING_SCHEME), existing.path(SIGNING_SCHEME));
            ObjectNode workflow = workflow(body.path(WORKFLOW), existing.path(WORKFLOW),
                    scheme.path(SIGNING_SCHEME).asString());
            body.set(SIGNING_SCHEME, scheme);
            body.set(WORKFLOW, workflow);
            if (body.has(RECORD_POLICY) || existing.hasNonNull(RECORD_POLICY)) {
                require(IlmFeature.SIGNING_RECORDS);
                ObjectNode policy = SigningConfigurationInput
                        .merge(body.path(RECORD_POLICY), existing.path(RECORD_POLICY), "recordingEnabled",
                                "recordRequestMetadata", "recordSignature", "recordSignedDocument", "recordDtbs",
                                "retentionDays", "deleteAfterRetrieval", "persistenceMode");
                SigningConfigurationInput.recordPolicy(policy, workflow.path("type").asString());
                body.set(RECORD_POLICY, policy);
            }
            body.set("customAttributes", CryptoInput.custom(client, "signingProfiles", existing, body));
            JsonNode result = uuid == null ? client.post(BASE, body) : client.put(PROFILE, body, uuid);
            return ConfigurationSupport
                    .describe("Signing profile saved; returned version is authoritative", result, RESULT);
        });
    }

    private ObjectNode scheme(JsonNode supplied, JsonNode existing) {
        ObjectNode scheme = SigningConfigurationInput
                .merge(supplied, existing, SIGNING_SCHEME, "managedSigningType", "certificateUuid", "raProfileUuid",
                        "csrTemplateUuid", "tokenProfileUuid", CONNECTOR_UUID, SIGNING_OPERATION_ATTRIBUTES,
                        "attributes");
        for (String name : new String[]{"certificate", "raProfile", "csrTemplate", "tokenProfile", "connector"}) {
            SigningConfigurationInput.reference(scheme, existing, name);
        }
        String kind = scheme.path(SIGNING_SCHEME).asString("");
        if (!Set.of(MANAGED, "delegated").contains(kind)) {
            throw new IllegalArgumentException("Signing scheme must be managed or delegated.");
        }
        if ("delegated".equals(kind)) {
            CryptoInput.uuid(scheme.path(CONNECTOR_UUID).asString(null));
            emptyOnly(scheme, supplied, "attributes");
            return scheme;
        }
        String managed = scheme.path("managedSigningType").asString("");
        if ("static_key".equals(managed)) {
            String certificate = CryptoInput.uuid(scheme.path("certificateUuid").asString(null));
            JsonNode definitions = operationDefinitions(certificate);
            String values = values(supplied, SIGNING_OPERATION_ATTRIBUTES);
            if (hasCallbacks(definitions)) {
                JsonNode cert = client.get("/v1/certificates/{uuid}", certificate);
                String keyUuid = cert.path("keyUuid").asString(cert.path("key").path("uuid").asString(null));
                JsonNode key = client.get("/v1/keys/{uuid}", CryptoInput.uuid(keyUuid));
                JsonNode token = client
                        .get("/v1/tokens/{uuid}", CryptoInput.uuid(key.path("tokenInstanceUuid").asString(null)));
                definitions = resolveConnector(definitions, CryptoInput.uuid(token.path(CONNECTOR_UUID).asString(null)),
                        "cryptography", values, previous(existing, SIGNING_OPERATION_ATTRIBUTES));
            }
            scheme
                    .set(SIGNING_OPERATION_ATTRIBUTES, ConfigurationAttributes
                            .replace(definitions, previous(existing, SIGNING_OPERATION_ATTRIBUTES), values));
        } else if ("one_time_key".equals(managed)) {
            for (String field : new String[]{"raProfileUuid", "csrTemplateUuid", "tokenProfileUuid"}) {
                CryptoInput.uuid(scheme.path(field).asString(null));
            }
            emptyOnly(scheme, supplied, SIGNING_OPERATION_ATTRIBUTES);
        } else {
            throw new IllegalArgumentException("Managed signing type must be static_key or one_time_key.");
        }
        return scheme;
    }

    private static void emptyOnly(ObjectNode body, JsonNode supplied, String field) {
        JsonNode values = supplied.path(field);
        if (!values.isMissingNode() && !values.isNull() && (!values.isObject() || !values.isEmpty())) {
            throw new IllegalArgumentException(
                    "This signing scheme has no persisted provider attribute schema in the platform contract.");
        }
        body.set(field, ConfigurationAttributes.JSON.createArrayNode());
    }

    private ObjectNode workflow(JsonNode supplied, JsonNode existing, String scheme) {
        ObjectNode workflow = SigningConfigurationInput
                .merge(supplied, existing, "type", SIGNATURE_FORMATTING_CONNECTOR_UUID,
                        SIGNATURE_FORMATTING_CONNECTOR_ATTRIBUTES, "qualifiedTimestamp", "timeQualityConfigurationUuid",
                        "defaultPolicyId", "allowedPolicyIds", "allowedDigestAlgorithms", "validateTokenSignature",
                        FAMILY, MAX_LEVEL, TIMESTAMP_SOURCE, "documentSizeCap", "requireNonRepudiation",
                        "requiredExtendedKeyUsageOids");
        SigningConfigurationInput.reference(workflow, existing, "signatureFormattingConnector");
        SigningConfigurationInput.reference(workflow, existing, "timeQualityConfiguration");
        if (!workflow.has(SIGNATURE_FORMATTING_CONNECTOR_UUID) && existing.has("signatureFormatterConnector")) {
            workflow
                    .set(SIGNATURE_FORMATTING_CONNECTOR_UUID,
                            existing.path("signatureFormatterConnector").path("uuid"));
        }
        String type = workflow.path("type").asString("");
        SigningConfigurationInput.workflowType(type);
        if (SigningConfigurationInput.extended(workflow)) {
            require(IlmFeature.SIGNING_FORMATTING_EXTENSIONS);
        }
        if (MANAGED.equals(scheme) && "content_signing".equals(type)
                && versions.unsupported(IlmFeature.SIGNING_FORMATTING_EXTENSIONS).isEmpty()) {
            SigningConfigurationInput.family(workflow.path(FAMILY).asString(null));
            SigningConfigurationInput.level(workflow.path(MAX_LEVEL).asString(null));
        }
        if (!supplied.has(TIMESTAMP_SOURCE)
                && workflow.path(TIMESTAMP_SOURCE).path("signingProfile").path("uuid").isString()) {
            ObjectNode source = ConfigurationAttributes.JSON.createObjectNode();
            source.put("type", "internal");
            source.set("signingProfileUuid", workflow.path(TIMESTAMP_SOURCE).path("signingProfile").path("uuid"));
            workflow.set(TIMESTAMP_SOURCE, source);
        }
        SigningConfigurationInput.workflow(workflow, scheme);
        if (MANAGED.equals(scheme) && !"raw_signing".equals(type)) {
            String connector = CryptoInput.uuid(workflow.path(SIGNATURE_FORMATTING_CONNECTOR_UUID).asString(null));
            String family = workflow.path(FAMILY).asString(null);
            String level = workflow.path(MAX_LEVEL).asString(null);
            String values = values(supplied, SIGNATURE_FORMATTING_CONNECTOR_ATTRIBUTES);
            JsonNode old = existing.path(SIGNATURE_FORMATTING_CONNECTOR_ATTRIBUTES);
            if (!old.isArray()) {
                old = existing.path("signatureFormatterConnectorAttributes");
            }
            JsonNode previous = old.isArray() ? old : ConfigurationAttributes.JSON.createArrayNode();
            JsonNode definitions = formattingDefinitions(connector, family, level, values, previous);
            workflow
                    .set(SIGNATURE_FORMATTING_CONNECTOR_ATTRIBUTES,
                            ConfigurationAttributes.replace(definitions, previous, values));
            if (older219()) {
                workflow.set("signatureFormatterConnectorUuid", workflow.remove(SIGNATURE_FORMATTING_CONNECTOR_UUID));
                workflow
                        .set("signatureFormatterConnectorAttributes",
                                workflow.remove(SIGNATURE_FORMATTING_CONNECTOR_ATTRIBUTES));
            }
        }
        return workflow;
    }

    private static boolean hasCallbacks(JsonNode definitions) {
        for (JsonNode definition : definitions) {
            if (definition.path("attributeCallback").isObject()) {
                return true;
            }
        }
        return false;
    }

    private static String values(JsonNode supplied, String field) {
        if (supplied.isMissingNode() || supplied.isNull()) {
            return "{}";
        }
        return CryptoInput.values(supplied, field);
    }

    private static JsonNode previous(JsonNode existing, String field) {
        return existing.path(field).isArray() ? existing.path(field) : ConfigurationAttributes.JSON.createArrayNode();
    }

    @Override
    public String deleteSigningProfile(String uuid) {
        return run("deleting signing profile", () -> {
            client.delete(PROFILE, CryptoInput.uuid(uuid));
            return "Deleted signing profile [" + uuid + "].";
        });
    }

    @Override
    public String enableSigningProfile(String uuid) {
        return state(uuid, "enable");
    }

    @Override
    public String disableSigningProfile(String uuid) {
        return state(uuid, "disable");
    }

    private String state(String uuid, String operation) {
        return run(operation + " signing profile", () -> {
            client.patch(PROFILE + "/" + operation, null, CryptoInput.uuid(uuid));
            return "Signing profile " + operation + " request accepted [" + uuid + "].";
        });
    }

    @Override
    public String getSigningTspActivation(String uuid) {
        return run("retrieving signing TSP activation", () -> ConfigurationSupport
                .describe("Signing TSP activation", client.get(PROFILE + "/protocols/tsp", CryptoInput.uuid(uuid)),
                        ENABLED, "tspProfile.uuid", "tspProfile.name", "signingUrl", "url"));
    }

    @Override
    public String activateSigningTsp(String uuid, String tspProfileUuid) {
        return run("activating signing TSP",
                () -> ConfigurationSupport
                        .describe("Signing TSP activation accepted",
                                client
                                        .patch(PROFILE + "/protocols/tsp/activate/{tspUuid}", null,
                                                CryptoInput.uuid(uuid), CryptoInput.uuid(tspProfileUuid)),
                                ENABLED, "tspProfile.uuid", "tspProfile.name", "signingUrl"));
    }

    @Override
    public String deactivateSigningTsp(String uuid) {
        return run("deactivating signing TSP", () -> {
            client.patch(PROFILE + "/protocols/tsp/deactivate", null, CryptoInput.uuid(uuid));
            return "Signing TSP deactivation accepted [" + uuid + "].";
        });
    }

    private boolean older219() {
        return versions.unsupported(IlmFeature.SIGNING_RECORDS).isPresent();
    }

    private void require(IlmFeature feature) {
        versions.unsupported(feature).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private String run(String action, Supplier<String> body) {
        return versions
                .unsupported(IlmFeature.SIGNING_PROFILES)
                .orElseGet(() -> ConfigurationSupport.run(action, body));
    }
}
