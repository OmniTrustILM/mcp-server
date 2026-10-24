package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.ConfigurationApiClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.bool;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.select;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.CertificateConfiguration.RA;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class RaProfileServiceImpl implements RaProfileService {
    private static final String ATTRIBUTES = "attributes";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String ENABLED = "enabled";
    private static final String EXPIRING_THRESHOLD = "expiringThreshold";
    private static final String EXTERNAL_CSR_VALIDATION_STRICT = "externalCsrValidationStrict";
    private static final String FREQUENCY = "frequency";
    private static final String ISSUE_CERTIFICATE_ATTRIBUTES = "issueCertificateAttributes";
    private static final String MERGE_MODE = "mergeMode";
    private static final String REQUEST_ATTRIBUTES = "requestAttributes";
    private static final String REVOKE_CERTIFICATE_ATTRIBUTES = "revokeCertificateAttributes";
    private static final String VALUE_SOURCE_BINDINGS = "valueSourceBindings";

    private final ConfigurationApiClient client;
    private final CertificateConfiguration support;

    public RaProfileServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        support = new CertificateConfiguration(client, versions);
    }

    @Override
    public String getRaProfile(String uuid) {
        return run("reading RA profile", () -> {
            JsonNode profile = client.get("/v1/raProfiles/{uuid}", id(uuid));
            return describe("RA profile", profile, CertificateConfiguration.META)
                    + describe("Validation settings", profile.path("certificateValidationSettings"), ENABLED, FREQUENCY,
                            EXPIRING_THRESHOLD)
                    + ConfigurationAttributes
                            .settings("Request attribute definitions", CertificateConfiguration
                                    .array(profile.path("certificateRequestAttributes").path(REQUEST_ATTRIBUTES)));
        });
    }

    @Override
    public String getRaProfileAttributes(String authorityUuid, String values) {
        return run("reading RA profile settings", () -> {
            id(authorityUuid);
            JsonNode definitions = client.get("/v1/authorities/{authority}/attributes/raProfile", authorityUuid);
            return ConfigurationAttributes
                    .settings("RA profile settings", ConfigurationAttributes
                            .resolve(client,
                                    new ConfigurationAttributes.ResourceContext(Resource.RA_PROFILE, authorityUuid),
                                    definitions, values));
        });
    }

    @Override
    public String createRaProfile(String authorityUuid, String configuration) {
        return run("creating RA profile", () -> {
            id(authorityUuid);
            ObjectNode body = configuration(configuration, "name", DESCRIPTION, ENABLED, ATTRIBUTES, CUSTOM_ATTRIBUTES);
            required(body.path("name").asString(null), "name");
            text(body, "name", DESCRIPTION);
            bool(body, ENABLED);
            body.set(ATTRIBUTES, raAttributes(authorityUuid, null, body.path(ATTRIBUTES)));
            body.set(CUSTOM_ATTRIBUTES, support.custom("raProfiles", null, body.path(CUSTOM_ATTRIBUTES)));
            return describe("Created RA profile",
                    client.post("/v1/authorities/{authority}/raProfiles", body, authorityUuid),
                    CertificateConfiguration.META);
        });
    }

    @Override
    public String updateRaProfile(String authorityUuid, String uuid, String configuration) {
        return run("updating RA profile", () -> {
            coordinates(authorityUuid, uuid);
            ObjectNode changes = configuration(configuration, DESCRIPTION, ENABLED, ATTRIBUTES, CUSTOM_ATTRIBUTES);
            text(changes, DESCRIPTION);
            bool(changes, ENABLED);
            JsonNode current = client.get(RA, authorityUuid, uuid);
            ObjectNode body = select(current, DESCRIPTION, ENABLED);
            for (String field : new String[]{DESCRIPTION, ENABLED}) {
                if (changes.has(field)) {
                    body.set(field, changes.get(field));
                }
            }
            body.set(ATTRIBUTES, raAttributes(authorityUuid, current.path(ATTRIBUTES), changes.path(ATTRIBUTES)));
            body
                    .set(CUSTOM_ATTRIBUTES, support
                            .custom("raProfiles", current.path(CUSTOM_ATTRIBUTES), changes.path(CUSTOM_ATTRIBUTES)));
            return "Updated RA profile " + uuid + ".\n"
                    + describe("RA profile", client.put(RA, body, authorityUuid, uuid), CertificateConfiguration.META);
        });
    }

    @Override
    public String deleteRaProfile(String authorityUuid, String uuid) {
        return run("deleting RA profile", () -> {
            coordinates(authorityUuid, uuid);
            client.delete(RA, authorityUuid, uuid);
            return "Deleted RA profile " + uuid + ".";
        });
    }

    @Override
    public String setRaProfileEnabled(String authorityUuid, String uuid, boolean enabled) {
        return run("changing RA profile enabled state", () -> {
            coordinates(authorityUuid, uuid);
            client.patch(RA + (enabled ? "/enable" : "/disable"), null, authorityUuid, uuid);
            return "RA profile " + uuid + " enabled=" + enabled + ".";
        });
    }

    @Override
    public String updateRaProfileValidation(String authorityUuid, String uuid, String configuration) {
        return run("updating RA validation settings", () -> {
            coordinates(authorityUuid, uuid);
            ObjectNode changes = configuration(configuration, ENABLED, FREQUENCY, EXPIRING_THRESHOLD);
            if (changes.hasNonNull(ENABLED)) {
                bool(changes, ENABLED);
            }
            CertificateConfiguration.positive(changes, FREQUENCY, EXPIRING_THRESHOLD);
            ObjectNode body = select(client.get(RA, authorityUuid, uuid).path("certificateValidationSettings"), ENABLED,
                    FREQUENCY, EXPIRING_THRESHOLD);
            changes.properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));
            client.patch(RA + "/validation", body, authorityUuid, uuid);
            return "Updated certificate validation settings for RA profile " + uuid + ".";
        });
    }

    @Override
    public String updateRaProfileRequestAttributes(String authorityUuid, String uuid, String configuration) {
        return run("updating RA request attributes", () -> {
            coordinates(authorityUuid, uuid);
            support.require(IlmFeature.RA_REQUEST_ATTRIBUTES);
            ObjectNode changes = configuration(configuration, REQUEST_ATTRIBUTES, MERGE_MODE, VALUE_SOURCE_BINDINGS,
                    EXTERNAL_CSR_VALIDATION_STRICT);
            bool(changes, EXTERNAL_CSR_VALIDATION_STRICT);
            ObjectNode body = select(client.get(RA, authorityUuid, uuid).path("certificateRequestAttributes"),
                    REQUEST_ATTRIBUTES, MERGE_MODE, VALUE_SOURCE_BINDINGS, EXTERNAL_CSR_VALIDATION_STRICT);
            changes.properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));
            if (!body.path(REQUEST_ATTRIBUTES).isArray()) {
                throw new IllegalArgumentException(
                        "requestAttributes must be a complete array of attribute definitions.");
            }
            for (JsonNode definition : body.path(REQUEST_ATTRIBUTES)) {
                required(definition.path("name").asString(null), "Request attribute name");
                if (!definition.path("properties").isObject()) {
                    throw new IllegalArgumentException("Request attribute properties must be an object.");
                }
            }
            if (body.hasNonNull(MERGE_MODE) && !java.util.Set
                    .of("staticOnly", "connectorOnly", "merge")
                    .contains(body.path(MERGE_MODE).asString())) {
                throw new IllegalArgumentException("Unsupported request attribute mergeMode.");
            }
            if (body.has(VALUE_SOURCE_BINDINGS) && !body.path(VALUE_SOURCE_BINDINGS).isArray()) {
                throw new IllegalArgumentException("valueSourceBindings must be an array.");
            }
            client.patch(RA + "/requestAttributes", body, authorityUuid, uuid);
            return "Updated request attribute settings for RA profile " + uuid + ".";
        });
    }

    @Override
    public String getRaProfileProtocol(String authorityUuid, String uuid, String protocol) {
        return run("reading RA enrollment protocol", () -> {
            coordinates(authorityUuid, uuid);
            return describe("RA enrollment protocol",
                    client
                            .get(RA + "/protocols/{protocol}", authorityUuid, uuid,
                                    CertificateConfiguration.protocol(protocol)),
                    ENABLED, "acmeProfile.uuid", "acmeProfile.name", "scepProfile.uuid", "scepProfile.name",
                    "cmpProfile.uuid", "cmpProfile.name", "directoryUrl", "url");
        });
    }

    @Override
    public String setRaProfileProtocol(String authorityUuid, String uuid, String protocol, String enrollmentUuid,
            String configuration) {
        return run("changing RA enrollment protocol", () -> {
            coordinates(authorityUuid, uuid);
            CertificateConfiguration.protocol(protocol);
            if (enrollmentUuid == null) {
                client.patch(RA + "/protocols/{protocol}/deactivate", null, authorityUuid, uuid, protocol);
                return "Deactivated " + protocol + " for RA profile " + uuid + ".";
            }
            id(enrollmentUuid);
            ObjectNode body = configuration(configuration, ISSUE_CERTIFICATE_ATTRIBUTES, REVOKE_CERTIFICATE_ATTRIBUTES);
            body
                    .set(ISSUE_CERTIFICATE_ATTRIBUTES,
                            operationAttributes(authorityUuid, uuid, "issue", body.path(ISSUE_CERTIFICATE_ATTRIBUTES)));
            if (!"scep".equals(protocol)) {
                body
                        .set(REVOKE_CERTIFICATE_ATTRIBUTES, operationAttributes(authorityUuid, uuid, "revoke",
                                body.path(REVOKE_CERTIFICATE_ATTRIBUTES)));
            } else if (body.has(REVOKE_CERTIFICATE_ATTRIBUTES)) {
                throw new IllegalArgumentException("SCEP activation supports issueCertificateAttributes only.");
            }
            client
                    .patch(RA + "/protocols/{protocol}/activate/{enrollment}", body, authorityUuid, uuid, protocol,
                            enrollmentUuid);
            return "Activated " + protocol + " profile " + enrollmentUuid + " for RA profile " + uuid + ".";
        });
    }

    @Override
    public String getRaProfileAssociations(String authorityUuid, String uuid) {
        return run("reading RA profile associations", () -> {
            coordinates(authorityUuid, uuid);
            return list("Compliance profiles", client.get(RA + "/complianceProfiles", authorityUuid, uuid), "uuid",
                    "name")
                    + list("Approval profiles", client.get(RA + "/approvalProfiles", authorityUuid, uuid), "uuid",
                            "name", "version");
        });
    }

    @Override
    public String getAuthorityChain(String authorityUuid, String uuid) {
        return run("reading RA authority chain", () -> {
            coordinates(authorityUuid, uuid);
            return list("Authority certificate chain", client.get(RA + "/caCertificates", authorityUuid, uuid), "uuid",
                    "subjectDn", "issuerDn", "serialNumber", "state");
        });
    }

    private JsonNode raAttributes(String authorityUuid, JsonNode existing, JsonNode values) {
        return support
                .attributes("/v1/authorities/{authority}/attributes/raProfile",
                        new ConfigurationAttributes.ResourceContext(Resource.RA_PROFILE, authorityUuid), existing,
                        values, authorityUuid);
    }

    private JsonNode operationAttributes(String authority, String profile, String operation, JsonNode values) {
        return support.operationAttributes(authority, profile, RA, operation, null, values);
    }

    private static void coordinates(String authority, String profile) {
        id(authority);
        id(profile);
    }
}
