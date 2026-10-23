package com.otilm.mcp.service;

import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Validates owned signing request shapes without Jackson 2 polymorphic DTOs. */
final class SigningConfigurationInput {
    private static final String ALLOWED_DIGEST_ALGORITHMS = "allowedDigestAlgorithms";
    private static final String ALLOWED_POLICY_IDS = "allowedPolicyIds";
    private static final String CONTENT_SIGNING = "content_signing";
    private static final String DEFAULT_POLICY_ID = "defaultPolicyId";
    private static final String DOCUMENT_SIZE_CAP = "documentSizeCap";
    private static final String FAMILY = "family";
    private static final String MAX_LEVEL = "maxLevel";
    private static final String PERSISTENCE_MODE = "persistenceMode";
    private static final String RAW_SIGNING = "raw_signing";
    private static final String RECORD_SIGNED_DOCUMENT = "recordSignedDocument";
    private static final String REQUIRE_NON_REPUDIATION = "requireNonRepudiation";
    private static final String REQUIRED_EXTENDED_KEY_USAGE_OIDS = "requiredExtendedKeyUsageOids";
    private static final String RETENTION_DAYS = "retentionDays";
    private static final String SIGNATURE_FORMATTING_CONNECTOR_UUID = "signatureFormattingConnectorUuid";
    private static final String TIME_QUALITY_CONFIGURATION_UUID = "timeQualityConfigurationUuid";
    private static final String TIMESTAMP_SOURCE = "timestampSource";

    private static final Set<String> EXTENDED = Set
            .of(FAMILY, MAX_LEVEL, TIMESTAMP_SOURCE, DOCUMENT_SIZE_CAP, REQUIRE_NON_REPUDIATION,
                    REQUIRED_EXTENDED_KEY_USAGE_OIDS);

    private SigningConfigurationInput() {
    }

    static ObjectNode merge(JsonNode supplied, JsonNode existing, String... allowed) {
        ObjectNode result = ConfigurationAttributes.JSON.createObjectNode();
        if (!supplied.isMissingNode() && !supplied.isObject()) {
            throw new IllegalArgumentException("Signing configuration sections must be objects.");
        }
        Set<String> fields = Set.of(allowed);
        if (supplied.isObject() && !fields.containsAll(supplied.propertyNames())) {
            throw new IllegalArgumentException("Unknown signing configuration field.");
        }
        for (String field : fields) {
            if (existing.has(field)) {
                result.set(field, existing.path(field));
            }
            if (supplied.has(field)) {
                result.set(field, supplied.path(field));
            }
        }
        return result;
    }

    static void reference(ObjectNode request, JsonNode response, String field) {
        if (!request.has(field + "Uuid") && response.path(field).path("uuid").isString()) {
            request.set(field + "Uuid", response.path(field).path("uuid"));
        }
    }

    static void workflowType(String value) {
        choice(value, Set.of(RAW_SIGNING, CONTENT_SIGNING, "timestamping"), "Signing workflow type");
    }

    static boolean extended(JsonNode workflow) {
        for (String field : EXTENDED) {
            if (workflow.has(field)) {
                return true;
            }
        }
        return false;
    }

    static void family(String value) {
        choice(value, Set.of("pades", "xades", "cades", "jades"), "Signature family");
    }

    static void level(String value) {
        choice(value, Set.of("signed", "timestamped", "long_term", "archival"), "Signature level");
    }

    private static void choice(String value, Set<String> offered, String label) {
        if (!offered.contains(value == null ? "" : value)) {
            throw new IllegalArgumentException(label + " must use a supported code.");
        }
    }

    static void workflow(ObjectNode workflow, String scheme) {
        String type = workflow.path("type").asString();
        if (!workflowFields(type).containsAll(workflow.propertyNames())) {
            throw new IllegalArgumentException("Options are incompatible with the selected signing workflow.");
        }
        validateWorkflowValues(workflow);
        validatePolicyIds(workflow);
        validateDigests(workflow);
        if (CONTENT_SIGNING.equals(type) && "managed".equals(scheme) && workflow.has(FAMILY)) {
            family(workflow.path(FAMILY).asString());
            level(workflow.path(MAX_LEVEL).asString());
            timestampSource(workflow);
        }
        if ("delegated".equals(scheme) && hasManagedFormatting(workflow)) {
            throw new IllegalArgumentException("Delegated signing must omit managed formatting options.");
        }
    }

    private static Set<String> workflowFields(String type) {
        return switch (type) {
            case RAW_SIGNING -> Set.of("type");
            case CONTENT_SIGNING -> Set
                    .of("type", SIGNATURE_FORMATTING_CONNECTOR_UUID, "signatureFormattingConnectorAttributes", FAMILY,
                            MAX_LEVEL, TIMESTAMP_SOURCE, DOCUMENT_SIZE_CAP, REQUIRE_NON_REPUDIATION,
                            REQUIRED_EXTENDED_KEY_USAGE_OIDS);
            default -> Set
                    .of("type", SIGNATURE_FORMATTING_CONNECTOR_UUID, "signatureFormattingConnectorAttributes",
                            "qualifiedTimestamp", TIME_QUALITY_CONFIGURATION_UUID, DEFAULT_POLICY_ID,
                            ALLOWED_POLICY_IDS, ALLOWED_DIGEST_ALGORITHMS, "validateTokenSignature");
        };
    }

    private static void validateWorkflowValues(ObjectNode workflow) {
        CryptoInput.bool(workflow, "qualifiedTimestamp", "validateTokenSignature", REQUIRE_NON_REPUDIATION);
        CryptoInput
                .text(workflow, "type", SIGNATURE_FORMATTING_CONNECTOR_UUID, TIME_QUALITY_CONFIGURATION_UUID,
                        DEFAULT_POLICY_ID, FAMILY, MAX_LEVEL);
        for (String field : new String[]{SIGNATURE_FORMATTING_CONNECTOR_UUID, TIME_QUALITY_CONFIGURATION_UUID}) {
            if (workflow.hasNonNull(field)) {
                CryptoInput.uuid(workflow.path(field).asString());
            }
        }
        if (workflow.hasNonNull(DOCUMENT_SIZE_CAP) && (!workflow.path(DOCUMENT_SIZE_CAP).isIntegralNumber()
                || workflow.path(DOCUMENT_SIZE_CAP).asLong() <= 0)) {
            throw new IllegalArgumentException("Document size cap must be a positive integer.");
        }
    }

    private static void validatePolicyIds(ObjectNode workflow) {
        oidList(workflow, ALLOWED_POLICY_IDS);
        oidList(workflow, REQUIRED_EXTENDED_KEY_USAGE_OIDS);
        if (workflow.hasNonNull(DEFAULT_POLICY_ID)) {
            oid(workflow.path(DEFAULT_POLICY_ID).asString());
            if (workflow.path(ALLOWED_POLICY_IDS).isArray() && !workflow.path(ALLOWED_POLICY_IDS).isEmpty()) {
                boolean offered = false;
                for (JsonNode candidate : workflow.path(ALLOWED_POLICY_IDS)) {
                    offered |= candidate.equals(workflow.path(DEFAULT_POLICY_ID));
                }
                if (!offered) {
                    throw new IllegalArgumentException("Default policy must be in allowedPolicyIds.");
                }
            }
        }
    }

    private static void validateDigests(ObjectNode workflow) {
        if (workflow.has(ALLOWED_DIGEST_ALGORITHMS)) {
            for (JsonNode digest : CryptoInput.strings(workflow.path(ALLOWED_DIGEST_ALGORITHMS), false)) {
                choice(digest.asString(),
                        Set
                                .of("MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512", "SHA3-256", "SHA3-384",
                                        "SHA3-512"),
                        "Digest algorithm");
            }
        }
    }

    private static boolean hasManagedFormatting(ObjectNode workflow) {
        return workflow.hasNonNull(SIGNATURE_FORMATTING_CONNECTOR_UUID) || workflow.hasNonNull(FAMILY)
                || workflow.hasNonNull(MAX_LEVEL) || workflow.hasNonNull(TIMESTAMP_SOURCE)
                || workflow.path(REQUIRE_NON_REPUDIATION).asBoolean(false)
                || !workflow.path(REQUIRED_EXTENDED_KEY_USAGE_OIDS).isEmpty();
    }

    private static void timestampSource(ObjectNode workflow) {
        JsonNode source = workflow.path(TIMESTAMP_SOURCE);
        boolean signed = "signed".equals(workflow.path(MAX_LEVEL).asString());
        if (signed && !source.isMissingNode() && !source.isNull()) {
            throw new IllegalArgumentException("Signed level must omit timestampSource.");
        }
        if (!signed) {
            if (!source.isObject() || !Set.of("type", "signingProfileUuid").containsAll(source.propertyNames())) {
                throw new IllegalArgumentException("Timestamped levels require an internal timestampSource.");
            }
            if (source.has("type")) {
                choice(source.path("type").asString(), Set.of("internal"), "Timestamp source type");
            }
            CryptoInput.uuid(source.path("signingProfileUuid").asString(null));
        }
    }

    private static void oidList(ObjectNode body, String field) {
        if (body.has(field)) {
            for (JsonNode value : CryptoInput.strings(body.path(field), false)) {
                oid(value.asString());
            }
        }
    }

    private static void oid(String value) {
        if (!value.matches(com.otilm.api.model.common.validation.OidFormat.REGEX)) {
            throw new IllegalArgumentException("Object identifier must be a dotted numeric OID.");
        }
    }

    static void recordPolicy(JsonNode policy, String workflow) {
        Set<String> allowed = Set
                .of("recordingEnabled", "recordRequestMetadata", "recordSignature", RECORD_SIGNED_DOCUMENT,
                        "recordDtbs", RETENTION_DAYS, "deleteAfterRetrieval", PERSISTENCE_MODE);
        if (!policy.isObject() || !allowed.containsAll(policy.propertyNames())) {
            throw new IllegalArgumentException("Invalid signing record policy fields.");
        }
        CryptoInput
                .bool((ObjectNode) policy, "recordingEnabled", "recordRequestMetadata", "recordSignature",
                        RECORD_SIGNED_DOCUMENT, "recordDtbs", "deleteAfterRetrieval");
        if (policy.hasNonNull(RETENTION_DAYS)
                && (!policy.path(RETENTION_DAYS).isIntegralNumber() || policy.path(RETENTION_DAYS).asInt() <= 0)) {
            throw new IllegalArgumentException("Record retention must be a positive integer.");
        }
        if (policy.path(RECORD_SIGNED_DOCUMENT).asBoolean(false) && RAW_SIGNING.equals(workflow)) {
            throw new IllegalArgumentException("Raw signing cannot record a signed document.");
        }
        if (policy.has(PERSISTENCE_MODE)) {
            choice(policy.path(PERSISTENCE_MODE).asString(), Set.of("immediate", "deferred_durable", "best_effort"),
                    "Record persistence mode");
        }
    }
}
