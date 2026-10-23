package com.otilm.mcp.service;

import com.otilm.api.model.core.auth.Resource;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.allow;
import static com.otilm.mcp.service.AdministrationInput.bool;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.strings;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.CertificateConfiguration.OPS;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.object;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class CertificateOperationsServiceImpl implements CertificateOperationsService {
    private static final String ALT_KEY_UUID = "altKeyUuid";
    private static final String ALT_SIGNATURE_ATTRIBUTES = "altSignatureAttributes";
    private static final String ALT_TOKEN_PROFILE_UUID = "altTokenProfileUuid";
    private static final String ATTRIBUTES = "attributes";
    private static final String AUTHORIZATION_SECRET = "authorizationSecret";
    private static final String CERTIFICATE = "certificate";
    private static final String CERTIFICATES = "certificates";
    private static final String CSR_ATTRIBUTES = "csrAttributes";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String ENTRIES = "entries";
    private static final String ENTRY_REFERENCE = "entryReference";
    private static final String EXPIRES_AT = "expiresAt";
    private static final String EXPORT_ATTRIBUTES = "exportAttributes";
    private static final String FORMAT = "format";
    private static final String GROUP_UUIDS = "groupUuids";
    private static final String IDENTIFY = "identify";
    private static final String IDENTIFY_ATTRIBUTES = "identifyAttributes";
    private static final String IMPORT_ATTRIBUTES = "importAttributes";
    private static final String ISSUE = "issue";
    private static final String ISSUE_ATTRIBUTES = "issueAttributes";
    private static final String KEY_DESTINATION = "keyDestination";
    private static final String KEY_TYPE = "keyType";
    private static final String KEY_UUID = "keyUuid";
    private static final String OWNER_UUID = "ownerUuid";
    private static final String PASSPHRASE = "passphrase";
    private static final String RA_PROFILE_ATTRIBUTES = "raProfileAttributes";
    private static final String RA_PROFILE_UUID = "raProfileUuid";
    private static final String REASON = "reason";
    private static final String REGISTER = "register";
    private static final String RENEW = "renew";
    private static final String REPLACE_IN_LOCATIONS = "replaceInLocations";
    private static final String REQUEST = "request";
    private static final String SIGNATURE_ATTRIBUTES = "signatureAttributes";
    private static final String SOURCE_CERTIFICATE_UUID = "sourceCertificateUuid";
    private static final String SUBJECT_ALT_NAME = "subjectAltName";
    private static final String SUBJECT_DN = "subjectDn";
    private static final String TOKEN_PROFILE_UUID = "tokenProfileUuid";
    private static final String TRUSTED_CA = "trustedCa";

    private static final String CERT = "/v1/certificates/{certificate}";
    private final ConfigurationApiClient client;
    private final CertificateConfiguration support;

    public CertificateOperationsServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        support = new CertificateConfiguration(client, versions);
    }

    @Override
    public String getCertificateOperations(String authority, String profile) {
        return run("reading certificate operations", () -> {
            coordinates(authority, profile, null);
            support.require(IlmFeature.CERTIFICATE_REGISTRATION);
            return list("Certificate operation capabilities",
                    client.get(OPS + "/availableOperations", authority, profile).path("operations"), "operation",
                    "supported", "asyncSupported", "cancelSupported");
        });
    }

    @Override
    public String getCertificateOperationAttributes(String authority, String profile, String operation, String values) {
        return run("reading certificate operation settings", () -> {
            coordinates(authority, profile, null);
            operationVersion(operation);
            JsonNode definitions = client.get(OPS + "/attributes/{operation}", authority, profile, operation);
            return ConfigurationAttributes
                    .settings("Certificate " + operation + " settings",
                            support.resolveOperation(authority, profile, definitions, values));
        });
    }

    @Override
    public String getCsrAttributes(String profile) {
        return run("reading CSR settings", () -> {
            if (profile != null) {
                support.require(IlmFeature.RA_REQUEST_ATTRIBUTES);
            }
            return ConfigurationAttributes.settings("CSR settings", csrDefinitions(profile));
        });
    }

    @Override
    public String createCertificateRequest(String configuration) {
        return run("creating certificate request", () -> {
            ObjectNode body = configuration(configuration, RA_PROFILE_UUID, SOURCE_CERTIFICATE_UUID, REQUEST, FORMAT,
                    TOKEN_PROFILE_UUID, KEY_UUID, ALT_TOKEN_PROFILE_UUID, ALT_KEY_UUID, CSR_ATTRIBUTES,
                    SIGNATURE_ATTRIBUTES, ALT_SIGNATURE_ATTRIBUTES, ISSUE_ATTRIBUTES, CUSTOM_ATTRIBUTES);
            CertificateConfiguration
                    .uuids(body, RA_PROFILE_UUID, SOURCE_CERTIFICATE_UUID, TOKEN_PROFILE_UUID, KEY_UUID,
                            ALT_TOKEN_PROFILE_UUID, ALT_KEY_UUID);
            csrInput(body, body.path(RA_PROFILE_UUID).asString(null), true);
            String profile = body.path(RA_PROFILE_UUID).asString(null);
            if (profile != null) {
                JsonNode ra = client.get("/v1/raProfiles/{profile}", profile);
                String authority = id(ra.path("authorityInstanceUuid").asString(null));
                body.set(ISSUE_ATTRIBUTES, providerAttributes(authority, profile, ISSUE, body.path(ISSUE_ATTRIBUTES)));
            } else if (body.has(ISSUE_ATTRIBUTES)) {
                throw new IllegalArgumentException("issueAttributes requires raProfileUuid.");
            }
            body.set(CUSTOM_ATTRIBUTES, support.custom(CERTIFICATES, null, body.path(CUSTOM_ATTRIBUTES)));
            return accepted(client.post("/v1/certificates/create", body), null, "Certificate request accepted");
        });
    }

    @Override
    public String issueCertificate(String authority, String profile, String configuration) {
        return run("requesting certificate issuance", () -> {
            coordinates(authority, profile, null);
            ObjectNode body = issueBody(configuration, profile, true);
            body.set(ATTRIBUTES, providerAttributes(authority, profile, ISSUE, body.path(ATTRIBUTES)));
            body.set(CUSTOM_ATTRIBUTES, support.custom(CERTIFICATES, null, body.path(CUSTOM_ATTRIBUTES)));
            return accepted(client.post(OPS + "/certificates", body, authority, profile), null,
                    "Certificate issuance request accepted");
        });
    }

    @Override
    public String issueExistingCertificate(String authority, String profile, String certificate, String configuration) {
        return run("requesting existing certificate issuance", () -> {
            coordinates(authority, profile, certificate);
            ObjectNode body = null;
            if (configuration != null && !configuration.isBlank()) {
                support.require(IlmFeature.CERTIFICATE_REGISTRATION);
                body = issueBody(configuration, profile, true);
                body.set(ATTRIBUTES, providerAttributes(authority, profile, ISSUE, body.path(ATTRIBUTES)));
                body.set(CUSTOM_ATTRIBUTES, support.custom(CERTIFICATES, null, body.path(CUSTOM_ATTRIBUTES)));
            }
            return accepted(
                    client.post(OPS + "/certificates/{certificate}/issue", body, authority, profile, certificate),
                    certificate, "Existing certificate issuance request accepted");
        });
    }

    @Override
    public String registerCertificate(String authority, String profile, String configuration) {
        return run("registering certificate", () -> {
            coordinates(authority, profile, null);
            support.require(IlmFeature.CERTIFICATE_REGISTRATION);
            ObjectNode body = configuration(configuration, SUBJECT_DN, SUBJECT_ALT_NAME, "extensions", CSR_ATTRIBUTES,
                    ATTRIBUTES, RA_PROFILE_ATTRIBUTES, CUSTOM_ATTRIBUTES, OWNER_UUID, GROUP_UUIDS,
                    SOURCE_CERTIFICATE_UUID, AUTHORIZATION_SECRET, EXPIRES_AT);
            if (body.has(SOURCE_CERTIFICATE_UUID)) {
                support.require(IlmFeature.CERTIFICATE_PROVIDER_EXTENSIONS);
            }
            text(body, SUBJECT_DN, SUBJECT_ALT_NAME, AUTHORIZATION_SECRET, EXPIRES_AT);
            CertificateConfiguration.uuids(body, OWNER_UUID, SOURCE_CERTIFICATE_UUID);
            if (body.has(GROUP_UUIDS)) {
                body.set(GROUP_UUIDS, strings(body.path(GROUP_UUIDS), GROUP_UUIDS, true));
            }
            boolean structured = validateRegistrationIdentity(body);
            validateRegistrationProtection(body);
            if (structured) {
                body
                        .set(CSR_ATTRIBUTES, ConfigurationAttributes
                                .encode(csrDefinitions(profile), body.path(CSR_ATTRIBUTES).toString()));
            }
            body.set(ATTRIBUTES, providerAttributes(authority, profile, REGISTER, body.path(ATTRIBUTES)));
            body
                    .set(RA_PROFILE_ATTRIBUTES,
                            support
                                    .attributes("/v1/authorities/{authority}/attributes/raProfile",
                                            new ConfigurationAttributes.ResourceContext(Resource.RA_PROFILE, authority),
                                            null, body.path(RA_PROFILE_ATTRIBUTES), authority));
            body.set(CUSTOM_ATTRIBUTES, support.custom(CERTIFICATES, null, body.path(CUSTOM_ATTRIBUTES)));
            return accepted(client.post(OPS + "/certificates/register", body, authority, profile), null,
                    "Certificate registration request accepted");
        });
    }

    @Override
    public String renewCertificate(String authority, String profile, String certificate, String configuration) {
        return run("renewing certificate", () -> {
            coordinates(authority, profile, certificate);
            ObjectNode body = configuration(configuration, REPLACE_IN_LOCATIONS, REQUEST, FORMAT, ATTRIBUTES,
                    AUTHORIZATION_SECRET);
            bool(body, REPLACE_IN_LOCATIONS);
            text(body, REQUEST, FORMAT, AUTHORIZATION_SECRET);
            format(body);
            if (body.has(ATTRIBUTES) || body.has(AUTHORIZATION_SECRET)) {
                support.require(IlmFeature.CERTIFICATE_PROVIDER_EXTENSIONS);
            }
            if (body.has(ATTRIBUTES)) {
                body.set(ATTRIBUTES, providerAttributes(authority, profile, RENEW, body.path(ATTRIBUTES)));
            }
            return accepted(
                    client.post(OPS + "/certificates/{certificate}/renew", body, authority, profile, certificate),
                    certificate, "Certificate renewal request accepted");
        });
    }

    @Override
    public String rekeyCertificate(String authority, String profile, String certificate, String configuration) {
        return run("rekeying certificate", () -> {
            coordinates(authority, profile, certificate);
            ObjectNode body = configuration(configuration, REPLACE_IN_LOCATIONS, REQUEST, FORMAT, KEY_UUID,
                    TOKEN_PROFILE_UUID, ALT_KEY_UUID, ALT_TOKEN_PROFILE_UUID, SIGNATURE_ATTRIBUTES,
                    ALT_SIGNATURE_ATTRIBUTES, ATTRIBUTES, AUTHORIZATION_SECRET);
            bool(body, REPLACE_IN_LOCATIONS);
            text(body, REQUEST, FORMAT, AUTHORIZATION_SECRET);
            format(body);
            CertificateConfiguration.uuids(body, KEY_UUID, TOKEN_PROFILE_UUID, ALT_KEY_UUID, ALT_TOKEN_PROFILE_UUID);
            if (!body.hasNonNull(REQUEST)) {
                id(body.path(TOKEN_PROFILE_UUID).asString(null));
                id(body.path(KEY_UUID).asString(null));
                signAttributes(body, KEY_UUID, TOKEN_PROFILE_UUID, SIGNATURE_ATTRIBUTES);
                if (body.hasNonNull(ALT_KEY_UUID)) {
                    signAttributes(body, ALT_KEY_UUID, ALT_TOKEN_PROFILE_UUID, ALT_SIGNATURE_ATTRIBUTES);
                }
            } else if (body.has(SIGNATURE_ATTRIBUTES) || body.has(ALT_SIGNATURE_ATTRIBUTES)) {
                throw new IllegalArgumentException(
                        "Signature attributes are only used when generating the rekey CSR from a key.");
            }
            if (body.has(ATTRIBUTES) || body.has(AUTHORIZATION_SECRET)) {
                support.require(IlmFeature.CERTIFICATE_PROVIDER_EXTENSIONS);
            }
            if (body.has(ATTRIBUTES)) {
                body.set(ATTRIBUTES, providerAttributes(authority, profile, RENEW, body.path(ATTRIBUTES)));
            }
            return accepted(
                    client.post(OPS + "/certificates/{certificate}/rekey", body, authority, profile, certificate),
                    certificate, "Certificate rekey request accepted");
        });
    }

    @Override
    public String finalizeCertificate(String authority, String profile, String certificate, String configuration) {
        return run("finalizing certificate issuance", () -> {
            coordinates(authority, profile, certificate);
            support.require(IlmFeature.CERTIFICATE_MANUAL_OPERATIONS);
            ObjectNode body = configuration(configuration, CERTIFICATE, CUSTOM_ATTRIBUTES, IDENTIFY_ATTRIBUTES);
            text(body, CERTIFICATE);
            required(body.path(CERTIFICATE).asString(null), CERTIFICATE);
            if (body.has(IDENTIFY_ATTRIBUTES)) {
                support.require(IlmFeature.CERTIFICATE_PROVIDER_EXTENSIONS);
                body
                        .set(IDENTIFY_ATTRIBUTES,
                                providerAttributes(authority, profile, IDENTIFY, body.path(IDENTIFY_ATTRIBUTES)));
            }
            body.set(CUSTOM_ATTRIBUTES, support.custom(CERTIFICATES, null, body.path(CUSTOM_ATTRIBUTES)));
            return accepted(client
                    .post(OPS + "/certificates/{certificate}/issue/finalize", body, authority, profile, certificate),
                    certificate, "Manual certificate finalization accepted");
        });
    }

    @Override
    public String revokeCertificate(String authority, String profile, String certificate, String configuration) {
        return run("revoking certificate", () -> {
            coordinates(authority, profile, certificate);
            ObjectNode body = configuration(configuration, REASON, ATTRIBUTES, "destroyKey");
            bool(body, "destroyKey");
            if (!Set
                    .of("unspecified", "keyCompromise", "cACompromise", "affiliationChanged", "superseded",
                            "cessationOfOperation", "certificateHold", "privilegeWithdrawn", "aACompromise")
                    .contains(body.path(REASON).asString(""))) {
                throw new IllegalArgumentException("A supported certificate revocation reason is required.");
            }
            body.set(ATTRIBUTES, providerAttributes(authority, profile, "revoke", body.path(ATTRIBUTES)));
            return accepted(
                    client.post(OPS + "/certificates/{certificate}/revoke", body, authority, profile, certificate),
                    certificate, "Certificate revocation request accepted");
        });
    }

    @Override
    public String confirmCertificateRevocation(String authority, String profile, String certificate) {
        return run("confirming certificate revocation", () -> {
            coordinates(authority, profile, certificate);
            support.require(IlmFeature.CERTIFICATE_MANUAL_OPERATIONS);
            return accepted(client
                    .post(OPS + "/certificates/{certificate}/revoke/confirm", null, authority, profile, certificate),
                    certificate, "Manual revocation confirmation accepted");
        });
    }

    @Override
    public String cancelCertificateOperation(String authority, String profile, String certificate, String reason) {
        return run("cancelling certificate operation", () -> {
            coordinates(authority, profile, certificate);
            support.require(IlmFeature.CERTIFICATE_MANUAL_OPERATIONS);
            ObjectNode body = JSON.createObjectNode();
            if (reason != null) {
                body.put(REASON, reason);
            }
            return accepted(
                    client.post(OPS + "/certificates/{certificate}/cancel", body, authority, profile, certificate),
                    certificate, "Certificate cancellation request accepted");
        });
    }

    @Override
    public String uploadCertificate(String certificate, String customAttributes, boolean asynchronous) {
        return run("uploading certificate", () -> {
            required(certificate, CERTIFICATE);
            if (asynchronous) {
                support.require(IlmFeature.CERTIFICATE_MANUAL_OPERATIONS);
            }
            ObjectNode body = JSON.createObjectNode();
            body.put(CERTIFICATE, certificate);
            body
                    .set(CUSTOM_ATTRIBUTES,
                            support.custom(CERTIFICATES, null, object(customAttributes, "Custom attributes")));
            JsonNode result = client
                    .post(asynchronous ? "/v1/certificates/upload/async" : "/v1/certificates/upload", body);
            return asynchronous
                    ? describe("Certificate upload accepted; processing may remain pending", result, "fingerprint",
                            "uuid")
                    : accepted(result, null, "Certificate upload accepted");
        });
    }

    @Override
    public String importCertificates(String configuration) {
        return run("importing certificates", () -> {
            support.require(IlmFeature.CERTIFICATE_TRANSFER);
            ObjectNode body = configuration(configuration, "file", PASSPHRASE, ENTRIES, CUSTOM_ATTRIBUTES);
            CertificateConfiguration.file(body.path("file"));
            text(body, PASSPHRASE);
            if (!body.path(ENTRIES).isArray() || body.path(ENTRIES).isEmpty()) {
                throw new IllegalArgumentException("entries must contain the exact entry references to import.");
            }
            Set<String> entries = new HashSet<>();
            for (JsonNode entry : body.path(ENTRIES)) {
                allow(entry, ENTRY_REFERENCE, KEY_DESTINATION);
                String reference = required(entry.path(ENTRY_REFERENCE).asString(null), ENTRY_REFERENCE);
                if (!entries.add(reference)) {
                    throw new IllegalArgumentException("Duplicate import entryReference.");
                }
                if (entry.hasNonNull(KEY_DESTINATION)) {
                    importDestination((ObjectNode) entry.path(KEY_DESTINATION));
                }
            }
            body.set(CUSTOM_ATTRIBUTES, support.custom(CERTIFICATES, null, body.path(CUSTOM_ATTRIBUTES)));
            return list("Certificate import results", client.post("/v1/certificates/import", body).path("results"),
                    ENTRY_REFERENCE, "kind", "imported", "certificateUuid", "certificateOutcome", KEY_UUID,
                    "keyOutcome");
        });
    }

    @Override
    public String exportCertificate(String certificate, String format, String encoding, boolean chain,
            boolean includeEndCertificate) {
        return run("exporting public certificate", () -> {
            id(certificate);
            if (!Set.of("raw", "pkcs7").contains(format) || !Set.of("pem", "der").contains(encoding)) {
                throw new IllegalArgumentException("Use format raw or pkcs7 and encoding pem or der.");
            }
            JsonNode result = chain
                    ? client
                            .get(CERT + "/chain/{format}?withEndCertificate={include}&encoding={encoding}", certificate,
                                    format, includeEndCertificate, encoding)
                    : client.get(CERT + "/{format}?encoding={encoding}", certificate, format, encoding);
            return describe("Public certificate export", result, FORMAT, "encoding", "completeChain") + "\nContent:\n"
                    + result.path("content").asString("");
        });
    }

    @Override
    public String exportCertificateKeystore(String certificate, String configuration) {
        return run("exporting certificate keystore", () -> {
            id(certificate);
            support.require(IlmFeature.CERTIFICATE_TRANSFER);
            ObjectNode body = configuration(configuration, PASSPHRASE, EXPORT_ATTRIBUTES);
            text(body, PASSPHRASE);
            required(body.path(PASSPHRASE).asString(null), PASSPHRASE);
            String passphrase = body.path(PASSPHRASE).asString();
            if (passphrase.codePointCount(0, passphrase.length()) < 12
                    || !Normalizer.isNormalized(passphrase, Normalizer.Form.NFC)) {
                throw new IllegalArgumentException("passphrase requires at least 12 Unicode code points in NFC form.");
            }
            if (body.has(EXPORT_ATTRIBUTES)) {
                JsonNode current = client.get(CERT, certificate);
                String key = id(current.path("key").path("uuid").asString(null));
                JsonNode keyDetail = client.get("/v1/keys/{key}", key);
                String item = privateItem(keyDetail);
                body
                        .set(EXPORT_ATTRIBUTES,
                                ConfigurationAttributes
                                        .encode(client.get("/v1/keys/{key}/items/{item}/export/attributes", key, item),
                                                body.path(EXPORT_ATTRIBUTES).toString()));
            }
            var result = client.download(CERT + "/keystore", body, certificate);
            return "Certificate keystore\nFilename: " + result.filename() + "\nContent type: " + result.contentType()
                    + "\nBase64: " + Base64.getEncoder().encodeToString(result.data());
        });
    }

    @Override
    public String updateCertificateMetadata(String certificate, String configuration) {
        return run("updating certificate metadata", () -> {
            id(certificate);
            ObjectNode body = configuration(configuration, GROUP_UUIDS, OWNER_UUID, RA_PROFILE_UUID, TRUSTED_CA,
                    ATTRIBUTES);
            validateMetadataChange(body);
            if (body.has(ATTRIBUTES)) {
                support.require(IlmFeature.CERTIFICATE_PROVIDER_EXTENSIONS);
                String profile = id(body.path(RA_PROFILE_UUID).asString(null));
                String authority = id(
                        client.get("/v1/raProfiles/{profile}", profile).path("authorityInstanceUuid").asString(null));
                body.set(ATTRIBUTES, providerAttributes(authority, profile, IDENTIFY, body.path(ATTRIBUTES)));
            }
            client.patch(CERT, body, certificate);
            return "Updated certificate " + certificate + " metadata.";
        });
    }

    @Override
    public String setCertificateArchived(String certificate, boolean archived) {
        return run("changing certificate archive state", () -> {
            client.patch(CERT + (archived ? "/archive" : "/unarchive"), null, id(certificate));
            return "Certificate " + certificate + " archived=" + archived + ".";
        });
    }

    @Override
    public String deleteCertificate(String certificate) {
        return run("deleting certificate", () -> {
            client.delete(CERT, id(certificate));
            return "Deleted certificate " + certificate + " from inventory.";
        });
    }

    @Override
    public String getCertificateRelations(String certificate) {
        return run("reading certificate relations",
                () -> describe("Certificate relations", client.get(CERT + "/relations", id(certificate)),
                        "certificateUuid", "successorCertificates", "predecessorCertificates"));
    }

    @Override
    public String setCertificateRelation(String certificate, String related, boolean associate) {
        return run("changing certificate relation", () -> {
            id(certificate);
            id(related);
            if (certificate.equals(related)) {
                throw new IllegalArgumentException("A certificate cannot relate to itself.");
            }
            if (associate) {
                client.patch(CERT + "/relations/{related}", null, certificate, related);
            } else {
                client.delete(CERT + "/relations/{related}", certificate, related);
            }
            return (associate ? "Associated " : "Disassociated ") + "certificates " + certificate + " and " + related
                    + ".";
        });
    }

    @Override
    public String getCertificateApprovals(String certificate, int page, int size) {
        return run("reading certificate approvals", () -> {
            id(certificate);
            if (page < 1 || size < 1 || size > 1000) {
                throw new IllegalArgumentException("Use positive page and size at most 1000.");
            }
            return list("Certificate approvals",
                    client
                            .get(CERT + "/approvals?pageNumber={page}&itemsPerPage={size}", certificate, page, size)
                            .path("approvals"),
                    "approvalUuid", "status", "resourceAction", "approvalProfileName", "createdAt", "expiryAt");
        });
    }

    private static boolean validateRegistrationIdentity(JsonNode body) {
        boolean structured = body.has(CSR_ATTRIBUTES);
        boolean flat = body.path(SUBJECT_DN).isString() && !body.path(SUBJECT_DN).asString().isBlank()
                || body.path(SUBJECT_ALT_NAME).isString() && !body.path(SUBJECT_ALT_NAME).asString().isBlank();
        if (!structured && !flat) {
            throw new IllegalArgumentException("Registration requires subjectDn, subjectAltName or csrAttributes.");
        }
        if (structured && (flat || body.has("extensions"))) {
            throw new IllegalArgumentException("Use csrAttributes or flat registration identity fields, not both.");
        }
        return structured;
    }

    private static void validateRegistrationProtection(JsonNode body) {
        if (body.has(AUTHORIZATION_SECRET)) {
            String secret = body.path(AUTHORIZATION_SECRET).asString();
            if (secret.length() < 12 || secret.length() > 255 || !secret.matches("[\\x20-\\x7E]+")) {
                throw new IllegalArgumentException(
                        "authorizationSecret must contain 12 to 255 printable ASCII characters.");
            }
        }
        if (body.hasNonNull(EXPIRES_AT)) {
            try {
                if (!OffsetDateTime.parse(body.path(EXPIRES_AT).asString()).isAfter(OffsetDateTime.now())) {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("expiresAt must be a future ISO-8601 instant.");
            }
        }
    }

    private static void validateMetadataChange(ObjectNode body) {
        int changes = 0;
        for (String field : new String[]{GROUP_UUIDS, OWNER_UUID, RA_PROFILE_UUID, TRUSTED_CA}) {
            if (body.has(field)) {
                changes++;
            }
        }
        if (changes != 1) {
            throw new IllegalArgumentException(
                    "Change exactly one of groupUuids, ownerUuid, raProfileUuid or trustedCa per call.");
        }
        bool(body, TRUSTED_CA);
        for (String field : new String[]{OWNER_UUID, RA_PROFILE_UUID}) {
            if (body.has(field) && (!body.path(field).isString() || !body.path(field).asString().isEmpty())) {
                id(body.path(field).asString(null));
            }
        }
        if (body.has(GROUP_UUIDS)) {
            body.set(GROUP_UUIDS, strings(body.path(GROUP_UUIDS), GROUP_UUIDS, true));
        }
    }

    private ObjectNode issueBody(String configuration, String profile, boolean requireCsr) {
        ObjectNode body = configuration(configuration, REQUEST, FORMAT, TOKEN_PROFILE_UUID, KEY_UUID,
                ALT_TOKEN_PROFILE_UUID, ALT_KEY_UUID, CSR_ATTRIBUTES, SIGNATURE_ATTRIBUTES, ALT_SIGNATURE_ATTRIBUTES,
                ATTRIBUTES, CUSTOM_ATTRIBUTES, AUTHORIZATION_SECRET);
        if (body.has(AUTHORIZATION_SECRET)) {
            support.require(IlmFeature.CERTIFICATE_REGISTRATION);
        }
        csrInput(body, profile, requireCsr);
        return body;
    }

    private void csrInput(ObjectNode body, String profile, boolean requireCsr) {
        text(body, REQUEST, FORMAT, AUTHORIZATION_SECRET);
        format(body);
        CertificateConfiguration.uuids(body, TOKEN_PROFILE_UUID, KEY_UUID, ALT_TOKEN_PROFILE_UUID, ALT_KEY_UUID);
        if (!body.hasNonNull(REQUEST) && requireCsr) {
            id(body.path(TOKEN_PROFILE_UUID).asString(null));
            id(body.path(KEY_UUID).asString(null));
            body
                    .set(CSR_ATTRIBUTES,
                            ConfigurationAttributes
                                    .encode(csrDefinitions(profile),
                                            body.has(CSR_ATTRIBUTES) ? body.path(CSR_ATTRIBUTES).toString() : "{}"));
            signAttributes(body, KEY_UUID, TOKEN_PROFILE_UUID, SIGNATURE_ATTRIBUTES);
            if (body.hasNonNull(ALT_KEY_UUID)) {
                signAttributes(body, ALT_KEY_UUID, ALT_TOKEN_PROFILE_UUID, ALT_SIGNATURE_ATTRIBUTES);
            }
        } else if (body.has(CSR_ATTRIBUTES) || body.has(SIGNATURE_ATTRIBUTES) || body.has(ALT_SIGNATURE_ATTRIBUTES)) {
            throw new IllegalArgumentException(
                    "CSR/signature attribute values are only used when generating a CSR from a key.");
        }
    }

    private void signAttributes(ObjectNode body, String keyField, String profileField, String valuesField) {
        JsonNode key = client.get("/v1/keys/{key}", id(body.path(keyField).asString(null)));
        String token = id(key.path("tokenInstanceUuid").asString(null));
        String profile = id(body.path(profileField).asString(null));
        String item = privateItem(key);
        JsonNode definitions = client
                .get("/v1/operations/tokens/{token}/tokenProfiles/{profile}/keys/{key}/items/{item}/sign/attributes",
                        token, profile, body.path(keyField).asString(), item);
        body
                .set(valuesField, ConfigurationAttributes
                        .encode(definitions, body.has(valuesField) ? body.path(valuesField).toString() : "{}"));
    }

    private static String privateItem(JsonNode key) {
        String item = null;
        for (JsonNode entry : key.path("items")) {
            if ("Private".equals(entry.path("type").asString())) {
                if (item != null) {
                    throw new IllegalArgumentException(
                            "Key has multiple private items; use a key with one signing private item.");
                }
                item = id(entry.path("uuid").asString(null));
            }
        }
        if (item == null) {
            throw new IllegalArgumentException("Key has no private item.");
        }
        return item;
    }

    private JsonNode csrDefinitions(String profile) {
        if (profile == null) {
            return client.get("/v1/certificates/csr/attributes");
        }
        id(profile);
        if (support.versions.unsupported(IlmFeature.RA_REQUEST_ATTRIBUTES).isPresent()) {
            return client.get("/v1/certificates/csr/attributes");
        }
        return client.get("/v1/certificates/csr/attributes?raProfileUuid={profile}", profile);
    }

    private JsonNode providerAttributes(String authority, String profile, String operation, JsonNode values) {
        operationVersion(operation);
        return support.operationAttributes(authority, profile, OPS, operation, null, values);
    }

    private void operationVersion(String operation) {
        if (!Set.of(ISSUE, "revoke", REGISTER, RENEW, IDENTIFY).contains(operation)) {
            throw new IllegalArgumentException("Unsupported certificate operation schema.");
        }
        if (REGISTER.equals(operation)) {
            support.require(IlmFeature.CERTIFICATE_REGISTRATION);
        }
        if (RENEW.equals(operation) || IDENTIFY.equals(operation)) {
            support.require(IlmFeature.CERTIFICATE_PROVIDER_EXTENSIONS);
        }
    }

    private static void format(ObjectNode body) {
        if (body.hasNonNull(FORMAT) && !Set.of("pkcs10", "crmf").contains(body.path(FORMAT).asString())) {
            throw new IllegalArgumentException("Certificate request format must be pkcs10 or crmf.");
        }
    }

    private String accepted(JsonNode response, String fallback, String action) {
        String certificate = response.path("uuid").asString(fallback);
        String result = action + (certificate == null ? "." : ": " + id(certificate) + ".");
        if (certificate != null) {
            try {
                result += "\n" + describe("Current certificate state", client.get(CERT, certificate), "uuid", "state",
                        "status", "validationStatus", "complianceStatus");
            } catch (RuntimeException unreadable) {
                result += "\nCurrent certificate state could not be read.";
            }
        }
        return result
                + "\nProcessing or approval may remain pending; inspect certificate state and approvals before treating the operation as complete.";
    }

    private void importDestination(ObjectNode destination) {
        allow(destination, TOKEN_PROFILE_UUID, "keyName", "exportable", KEY_TYPE, IMPORT_ATTRIBUTES, CUSTOM_ATTRIBUTES);
        String profile = id(destination.path(TOKEN_PROFILE_UUID).asString(null));
        text(destination, "keyName");
        bool(destination, "exportable");
        String type = destination.path(KEY_TYPE).asString("keyPair");
        if (!Set.of("keyPair", "secret").contains(type)) {
            throw new IllegalArgumentException("Import keyType must be keyPair or secret.");
        }
        String token = id(client.get("/v1/tokenProfiles/{profile}", profile).path("tokenInstanceUuid").asString(null));
        destination.remove(KEY_TYPE);
        destination
                .set(IMPORT_ATTRIBUTES,
                        ConfigurationAttributes
                                .encode(client
                                        .get("/v1/tokens/{token}/tokenProfiles/{profile}/keys/{type}/import/attributes",
                                                token, profile, type),
                                        destination.has(IMPORT_ATTRIBUTES)
                                                ? destination.path(IMPORT_ATTRIBUTES).toString()
                                                : "{}"));
        destination.set(CUSTOM_ATTRIBUTES, support.custom("keys", null, destination.path(CUSTOM_ATTRIBUTES)));
    }

    private static void coordinates(String authority, String profile, String certificate) {
        id(authority);
        id(profile);
        if (certificate != null) {
            id(certificate);
        }
    }
}
