package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class OidServiceImpl implements OidService {
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String ALT_CODES = "altCodes";
    private static final String CATEGORY = "category";
    private static final String DESCRIPTION = "description";
    private static final String DISPLAY_NAME = "displayName";
    private static final String VALUE_SCHEMA = "valueSchema";

    private static final String OID_ENDPOINT = "/v1/oids/{oid}";
    private static final String[] FIELDS = {"oid", DISPLAY_NAME, DESCRIPTION, CATEGORY, ADDITIONAL_PROPERTIES};
    private static final String[] EDITABLE = {DISPLAY_NAME, DESCRIPTION, CATEGORY, ADDITIONAL_PROPERTIES};
    private final ConfigurationApiClient api;
    private final PlatformVersionCheck versions;

    public OidServiceImpl(ConfigurationApiClient api, PlatformVersionCheck versions) {
        this.api = api;
        this.versions = versions;
    }

    @Override
    public String listCustomOids(String filters, Integer pageNumber, Integer itemsPerPage) {
        return run("listing custom OIDs", () -> {
            if (pageNumber != null && pageNumber < 1 || itemsPerPage != null && itemsPerPage < 1) {
                throw new IllegalArgumentException("Pagination must use positive page and item counts.");
            }
            SearchRequestDto request;
            try {
                request = SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Invalid OID search filters.");
            }
            return list("Custom OIDs", api.post("/v1/oids/list", request), FIELDS);
        });
    }

    @Override
    public String getCustomOid(String oid) {
        return run("getting custom OID", () -> describe("Custom OID", api.get(OID_ENDPOINT, oid(oid)), FIELDS));
    }

    @Override
    public String createCustomOid(String configuration) {
        return run("creating custom OID", () -> {
            ObjectNode body = AdministrationInput
                    .configuration(configuration, "oid", DISPLAY_NAME, DESCRIPTION, CATEGORY, ADDITIONAL_PROPERTIES);
            body.put("oid", oid(body.path("oid").asString(null)));
            validate(body);
            return describe("Custom OID created", api.post("/v1/oids", body), FIELDS);
        });
    }

    @Override
    public String updateCustomOid(String oid, String configuration) {
        return run("updating custom OID", () -> {
            String id = oid(oid);
            ObjectNode patch = AdministrationInput.configuration(configuration, EDITABLE);
            JsonNode existing = api.get(OID_ENDPOINT, id);
            if (patch.has(CATEGORY) && !patch.path(CATEGORY).equals(existing.path(CATEGORY))) {
                throw new IllegalArgumentException("OID category cannot be changed.");
            }
            ObjectNode body = AdministrationInput.select(existing, EDITABLE);
            for (var field : patch.properties()) {
                if (field.getKey().equals(ADDITIONAL_PROPERTIES) && field.getValue().isObject()
                        && body.path(ADDITIONAL_PROPERTIES).isObject()) {
                    ObjectNode merged = (ObjectNode) body.path(ADDITIONAL_PROPERTIES);
                    field.getValue().properties().forEach(entry -> merged.set(entry.getKey(), entry.getValue()));
                } else {
                    body.set(field.getKey(), field.getValue());
                }
            }
            validate(body);
            return describe("Custom OID updated", api.put(OID_ENDPOINT, body, id), FIELDS);
        });
    }

    @Override
    public String deleteCustomOid(String oid) {
        return run("deleting custom OID", () -> {
            api.delete(OID_ENDPOINT, oid(oid));
            return "Custom OID deleted.";
        });
    }

    @Override
    public String listSystemOids() {
        return run("listing system OIDs",
                () -> versions
                        .unsupported(IlmFeature.SYSTEM_OIDS)
                        .orElseGet(() -> list("System OIDs", api.get("/v1/oids/system"), FIELDS)));
    }

    private void validate(ObjectNode body) {
        AdministrationInput.text(body, "oid", DISPLAY_NAME, DESCRIPTION, CATEGORY);
        body.put(DISPLAY_NAME, required(body.path(DISPLAY_NAME).asString(null), "OID displayName"));
        String category = body.path(CATEGORY).asString();
        if (!Set.of("rdnAttributeType", "extendedKeyUsage", "generic", "certificateExtension").contains(category)) {
            throw new IllegalArgumentException("Unknown OID category.");
        }
        JsonNode properties = body.path(ADDITIONAL_PROPERTIES);
        if (category.equals("certificateExtension")) {
            validateCertificateExtension(properties);
        } else if (category.equals("rdnAttributeType")) {
            validateRdn(properties);
        } else if (!properties.isMissingNode() && !properties.isNull()
                && (!properties.isObject() || !properties.isEmpty())) {
            throw new IllegalArgumentException("This OID category has no additional properties.");
        }
    }

    private void validateCertificateExtension(JsonNode properties) {
        requireVersion(IlmFeature.CERTIFICATE_EXTENSION_OIDS);
        if (!properties.isObject()) {
            throw new IllegalArgumentException("Certificate extension additionalProperties are required.");
        }
        AdministrationInput.allow(properties, "defaultCritical", "valueEncoding", VALUE_SCHEMA);
        if (!properties.path("defaultCritical").isBoolean()) {
            throw new IllegalArgumentException("defaultCritical must be true or false.");
        }
        String encoding = properties.path("valueEncoding").asString();
        if (!Set
                .of("UTF8String", "IA5String", "PrintableString", "OctetString", "BitString", "DER")
                .contains(encoding)) {
            throw new IllegalArgumentException("Unknown OID valueEncoding.");
        }
        if (properties.hasNonNull(VALUE_SCHEMA)) {
            requireVersion(IlmFeature.OID_VALUE_SCHEMA);
            if (!properties.path(VALUE_SCHEMA).isString() || !encoding.equals("DER")) {
                throw new IllegalArgumentException("valueSchema requires DER encoding and text.");
            }
        }
    }

    private static void validateRdn(JsonNode properties) {
        if (!properties.isObject()) {
            throw new IllegalArgumentException("RDN additionalProperties with code are required.");
        }
        AdministrationInput.allow(properties, "code", ALT_CODES);
        code(properties.path("code"));
        if (properties.has(ALT_CODES)) {
            AdministrationInput.strings(properties.path(ALT_CODES), "Alternative codes", false);
            properties.path(ALT_CODES).forEach(OidServiceImpl::code);
        }
    }

    private void requireVersion(IlmFeature feature) {
        versions.unsupported(feature).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private static void code(JsonNode value) {
        if (!value.isString() || !value.asString().matches("[A-Za-z][A-Za-z0-9-]*")) {
            throw new IllegalArgumentException(
                    "RDN code must start with a letter and contain only letters, digits and hyphens.");
        }
    }

    private static String oid(String value) {
        if (value == null || !value.matches("([01]\\.(\\d|[1-3]\\d)|2\\.(0|[1-9]\\d*))(\\.(0|[1-9]\\d*)){0,49}")) {
            throw new IllegalArgumentException("OID must use valid dotted numeric notation.");
        }
        return value;
    }
}
