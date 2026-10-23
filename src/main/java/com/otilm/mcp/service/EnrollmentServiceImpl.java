package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static com.otilm.mcp.service.AdministrationInput.JSON;
import static com.otilm.mcp.service.AdministrationInput.allow;
import static com.otilm.mcp.service.AdministrationInput.bool;
import static com.otilm.mcp.service.AdministrationInput.configuration;
import static com.otilm.mcp.service.AdministrationInput.id;
import static com.otilm.mcp.service.AdministrationInput.select;
import static com.otilm.mcp.service.AdministrationInput.strings;
import static com.otilm.mcp.service.AdministrationInput.text;
import static com.otilm.mcp.service.ConfigurationSupport.describe;
import static com.otilm.mcp.service.ConfigurationSupport.list;
import static com.otilm.mcp.service.ConfigurationSupport.required;
import static com.otilm.mcp.service.ConfigurationSupport.run;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {
    private static final String PROFILE = " profile ";
    private static final String UUID_ENDPOINT = "/{uuid}";
    private static final String CA_CERTIFICATE_UUID = "caCertificateUuid";
    private static final String CERTIFICATE_ASSOCIATIONS = "certificateAssociations";
    private static final String CERTIFICATE_REGISTRATION = "certificateRegistration";
    private static final String CHALLENGE_PASSWORD = "challengePassword";
    private static final String CHALLENGE_SOURCE = "challengeSource";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DESCRIPTION = "description";
    private static final String EAB_SECRET_UUIDS = "eabSecretUuids";
    private static final String ENABLE_CHALLENGE_PASSWORD = "enableChallengePassword";
    private static final String ENABLE_INTUNE = "enableIntune";
    private static final String GROUP_UUIDS = "groupUuids";
    private static final String IDENTIFIER_AUTHORIZATION_MODE = "identifierAuthorizationMode";
    private static final String INTUNE_APPLICATION_KEY = "intuneApplicationKey";
    private static final String ISSUE_CERTIFICATE_ATTRIBUTES = "issueCertificateAttributes";
    private static final String OWNER_UUID = "ownerUuid";
    private static final String PREAUTHORIZED_IDENTIFIERS = "preauthorizedIdentifiers";
    private static final String RA_PROFILE_UUID = "raProfileUuid";
    private static final String RENEWAL_THRESHOLD = "renewalThreshold";
    private static final String REQUEST_PROTECTION_METHOD = "requestProtectionMethod";
    private static final String REQUIRE_CONTACT = "requireContact";
    private static final String REQUIRE_TERMS_OF_SERVICE = "requireTermsOfService";
    private static final String RESPONSE_PROTECTION_METHOD = "responseProtectionMethod";
    private static final String REVOKE_CERTIFICATE_ATTRIBUTES = "revokeCertificateAttributes";
    private static final String SHARED_SECRET = "sharedSecret";
    private static final String SIGNING_CERTIFICATE_UUID = "signingCertificateUuid";
    private static final String TERMS_OF_SERVICE_URL = "termsOfServiceUrl";
    private static final String VARIANT = "variant";
    private static final String WEBSITE_URL = "websiteUrl";

    private static final String[] ACME = {
            "name",
            DESCRIPTION,
            TERMS_OF_SERVICE_URL,
            WEBSITE_URL,
            "dnsResolverIp",
            "dnsResolverPort",
            RA_PROFILE_UUID,
            "retryInterval",
            "validity",
            ISSUE_CERTIFICATE_ATTRIBUTES,
            REVOKE_CERTIFICATE_ATTRIBUTES,
            REQUIRE_CONTACT,
            REQUIRE_TERMS_OF_SERVICE,
            CUSTOM_ATTRIBUTES,
            CERTIFICATE_ASSOCIATIONS,
            EAB_SECRET_UUIDS,
            PREAUTHORIZED_IDENTIFIERS,
            IDENTIFIER_AUTHORIZATION_MODE,
            "termsOfServiceChangeDisable",
            "termsOfServiceChangeUrl"};
    private static final String[] SCEP = {
            "name",
            DESCRIPTION,
            RA_PROFILE_UUID,
            ISSUE_CERTIFICATE_ATTRIBUTES,
            CA_CERTIFICATE_UUID,
            CUSTOM_ATTRIBUTES,
            RENEWAL_THRESHOLD,
            "includeCaCertificate",
            "includeCaCertificateChain",
            CHALLENGE_PASSWORD,
            ENABLE_CHALLENGE_PASSWORD,
            CHALLENGE_SOURCE,
            ENABLE_INTUNE,
            "intuneTenant",
            "intuneApplicationId",
            INTUNE_APPLICATION_KEY,
            CERTIFICATE_ASSOCIATIONS};
    private static final String[] CMP = {
            "name",
            DESCRIPTION,
            RA_PROFILE_UUID,
            ISSUE_CERTIFICATE_ATTRIBUTES,
            REVOKE_CERTIFICATE_ATTRIBUTES,
            CUSTOM_ATTRIBUTES,
            REQUEST_PROTECTION_METHOD,
            RESPONSE_PROTECTION_METHOD,
            SHARED_SECRET,
            SIGNING_CERTIFICATE_UUID,
            VARIANT,
            CHALLENGE_SOURCE,
            CERTIFICATE_ASSOCIATIONS};
    private static final String[] PROTECTED = {CHALLENGE_PASSWORD, INTUNE_APPLICATION_KEY, SHARED_SECRET};
    private final ConfigurationApiClient client;
    private final CertificateConfiguration support;

    public EnrollmentServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        support = new CertificateConfiguration(client, versions);
    }

    @Override
    public String listProfiles(String protocol) {
        return run("listing enrollment profiles",
                () -> list(protocol + " profiles", client.get(base(protocol)), CertificateConfiguration.META));
    }

    @Override
    public String getProfile(String protocol, String uuid) {
        return run("reading enrollment profile", () -> metadata(client.get(base(protocol) + UUID_ENDPOINT, id(uuid))));
    }

    @Override
    public String createProfile(String protocol, String configuration) {
        return run("creating enrollment profile", () -> {
            ObjectNode changes = input(protocol, configuration);
            required(changes.path("name").asString(null), "name");
            ObjectNode body = body(protocol, changes, JSON.createObjectNode(), false);
            return metadata(client.post(base(protocol), body));
        });
    }

    @Override
    public String updateProfile(String protocol, String uuid, String configuration) {
        return run("updating enrollment profile", () -> {
            id(uuid);
            ObjectNode changes = input(protocol, configuration);
            JsonNode current = client.get(base(protocol) + UUID_ENDPOINT, uuid);
            if (changes.has("name") && !"cmp".equals(protocol) && !changes.path("name").equals(current.path("name"))) {
                throw new IllegalArgumentException(
                        "Enrollment profile name cannot be changed by this update endpoint.");
            }
            ObjectNode body = body(protocol, changes, current, true);
            if (!"cmp".equals(protocol)) {
                body.remove("name");
            }
            return "Updated " + protocol + PROFILE + uuid + ".\n"
                    + metadata(client.put(base(protocol) + UUID_ENDPOINT, body, uuid));
        });
    }

    @Override
    public String deleteProfile(String protocol, String uuid) {
        return run("deleting enrollment profile", () -> {
            client.delete(base(protocol) + UUID_ENDPOINT, id(uuid));
            return "Deleted " + protocol + PROFILE + uuid + ".";
        });
    }

    @Override
    public String setProfileEnabled(String protocol, String uuid, boolean enabled) {
        return run("changing enrollment enabled state", () -> {
            client.patch(base(protocol) + UUID_ENDPOINT + (enabled ? "/enable" : "/disable"), null, id(uuid));
            return protocol + PROFILE + uuid + " enabled=" + enabled + ".";
        });
    }

    @Override
    public String setDefaultRaProfile(String protocol, String uuid, String raProfileUuid) {
        return run("changing enrollment default RA profile", () -> {
            id(uuid);
            id(raProfileUuid);
            String suffix = "acme".equals(protocol) ? "/raprofile/{ra}" : "/raProfiles/{ra}";
            client.patch(base(protocol) + UUID_ENDPOINT + suffix, null, uuid, raProfileUuid);
            return "Set " + protocol + PROFILE + uuid + " default RA profile to " + raProfileUuid + ".";
        });
    }

    @Override
    public String getEnrollmentCertificateChoices(String protocol) {
        return run("reading enrollment certificate choices", () -> {
            CertificateConfiguration.protocol(protocol);
            if ("acme".equals(protocol)) {
                throw new IllegalArgumentException("ACME does not use a configured signing/CA certificate.");
            }
            return list("Enrollment certificate choices",
                    client
                            .get("scep".equals(protocol)
                                    ? "/v1/scepProfiles/caCertificates"
                                    : "/v1/cmpProfiles/signingCertificates"),
                    "uuid", "commonName", "subjectDn", "state", "keyUuid");
        });
    }

    @Override
    public String generateAcmeEabKey() {
        return run("generating ACME EAB key", () -> {
            support.require(IlmFeature.ENROLLMENT_EXTENSIONS);
            JsonNode response = client.post("/v1/acmeProfiles/eabKeys", null);
            String key = response.path("key").asString(null);
            required(key, "Returned EAB key");
            return "Generated ACME EAB key (base64url): " + key
                    + "\nStore it as secret content and associate that secret UUID with the ACME profile.";
        });
    }

    private ObjectNode input(String protocol, String configuration) {
        ObjectNode input = configuration(configuration, fields(protocol));
        fieldVersions(input);
        for (String field : new String[]{
                ISSUE_CERTIFICATE_ATTRIBUTES,
                REVOKE_CERTIFICATE_ATTRIBUTES,
                CUSTOM_ATTRIBUTES}) {
            if (input.has(field) && !input.path(field).isObject()) {
                throw new IllegalArgumentException(field + " must be a plain name-to-value JSON object.");
            }
        }
        text(input, "name", DESCRIPTION, TERMS_OF_SERVICE_URL, WEBSITE_URL, "dnsResolverIp", "dnsResolverPort",
                "termsOfServiceChangeUrl", CHALLENGE_PASSWORD, "intuneTenant", "intuneApplicationId",
                INTUNE_APPLICATION_KEY, SHARED_SECRET, VARIANT, REQUEST_PROTECTION_METHOD, RESPONSE_PROTECTION_METHOD,
                CHALLENGE_SOURCE, IDENTIFIER_AUTHORIZATION_MODE);
        for (String field : new String[]{
                REQUIRE_CONTACT,
                REQUIRE_TERMS_OF_SERVICE,
                "termsOfServiceChangeDisable",
                "includeCaCertificate",
                "includeCaCertificateChain",
                ENABLE_CHALLENGE_PASSWORD,
                ENABLE_INTUNE}) {
            bool(input, field);
        }
        CertificateConfiguration.positive(input, "retryInterval", "validity", RENEWAL_THRESHOLD);
        CertificateConfiguration.uuids(input, RA_PROFILE_UUID, CA_CERTIFICATE_UUID, SIGNING_CERTIFICATE_UUID);
        if (input.has(EAB_SECRET_UUIDS)) {
            input.set(EAB_SECRET_UUIDS, strings(input.path(EAB_SECRET_UUIDS), EAB_SECRET_UUIDS, true));
        }
        choice(input, REQUEST_PROTECTION_METHOD, Set.of(SHARED_SECRET, "signature"));
        choice(input, RESPONSE_PROTECTION_METHOD, Set.of(SHARED_SECRET, "signature"));
        choice(input, VARIANT, Set.of("v2", "v2_3gpp", "v3"));
        choice(input, CHALLENGE_SOURCE, Set.of("protocolDefault", CERTIFICATE_REGISTRATION));
        choice(input, IDENTIFIER_AUTHORIZATION_MODE, Set.of("preauthorizedOrChallenge", "preauthorizedOnly"));
        if (input.has(PREAUTHORIZED_IDENTIFIERS)) {
            if (!input.path(PREAUTHORIZED_IDENTIFIERS).isArray()) {
                throw new IllegalArgumentException("preauthorizedIdentifiers must be an array.");
            }
            for (JsonNode identifier : input.path(PREAUTHORIZED_IDENTIFIERS)) {
                allow(identifier, "type", "value", "matchType", "allowWildcard");
                required(identifier.path("value").asString(null), "Identifier value");
                bool(identifier, "allowWildcard");
                choice(identifier, "type", Set.of("dns", "ip"));
                choice(identifier, "matchType", Set.of("exact", "subdomain"));
            }
        }
        return input;
    }

    private ObjectNode body(String protocol, ObjectNode changes, JsonNode current, boolean update) {
        ObjectNode body = select(current, fields(protocol));
        for (String field : PROTECTED) {
            body.remove(field);
        }
        copyProfileReferences(body, current);
        changes.properties().forEach(entry -> body.set(entry.getKey(), entry.getValue()));
        if (update) {
            requireFreshProtected(protocol, body, changes);
        }
        if ("cmp".equals(protocol)) {
            validateCmpProtection(body);
        }
        String ra = body.path(RA_PROFILE_UUID).asString(null);
        encodeOperation(body, current, ra, ISSUE_CERTIFICATE_ATTRIBUTES, "issue");
        if (!"scep".equals(protocol)) {
            encodeOperation(body, current, ra, REVOKE_CERTIFICATE_ATTRIBUTES, "revoke");
        }
        body
                .set(CUSTOM_ATTRIBUTES,
                        support
                                .custom(protocol + "Profiles", current.path(CUSTOM_ATTRIBUTES),
                                        changes.path(CUSTOM_ATTRIBUTES)));
        encodeCertificateAssociations(body, current, changes);
        return body;
    }

    private static void copyProfileReferences(ObjectNode body, JsonNode current) {
        if (current.path("raProfile").hasNonNull("uuid")) {
            body.set(RA_PROFILE_UUID, current.path("raProfile").get("uuid"));
        }
        if (current.path("caCertificate").hasNonNull("uuid")) {
            body.set(CA_CERTIFICATE_UUID, current.path("caCertificate").get("uuid"));
        }
        if (current.path("signingCertificate").hasNonNull("uuid")) {
            body.set(SIGNING_CERTIFICATE_UUID, current.path("signingCertificate").get("uuid"));
        }
        if (current.has("renewThreshold")) {
            body.set(RENEWAL_THRESHOLD, current.path("renewThreshold"));
        }
    }

    private static void validateCmpProtection(ObjectNode body) {
        required(body.path(VARIANT).asString(null), VARIANT);
        for (String field : new String[]{REQUEST_PROTECTION_METHOD, RESPONSE_PROTECTION_METHOD}) {
            required(body.path(field).asString(null), field);
        }
        if (registrationChallenge(body)) {
            if (!"v2".equals(body.path(VARIANT).asString())) {
                throw new IllegalArgumentException("certificateRegistration requires CMP v2.");
            }
            if (!SHARED_SECRET.equals(body.path(REQUEST_PROTECTION_METHOD).asString())) {
                throw new IllegalArgumentException("certificateRegistration requires sharedSecret request protection.");
            }
            if (body.has(SHARED_SECRET)) {
                throw new IllegalArgumentException(
                        "Do not supply a profile sharedSecret with certificateRegistration challengeSource.");
            }
        } else if ((SHARED_SECRET.equals(body.path(REQUEST_PROTECTION_METHOD).asString())
                || SHARED_SECRET.equals(body.path(RESPONSE_PROTECTION_METHOD).asString()))
                && !body.hasNonNull(SHARED_SECRET)) {
            throw new IllegalArgumentException("Supply a fresh sharedSecret for shared-secret protection.");
        }
    }

    private void encodeCertificateAssociations(ObjectNode body, JsonNode current, JsonNode changes) {
        if (!current.has(CERTIFICATE_ASSOCIATIONS) && !changes.has(CERTIFICATE_ASSOCIATIONS)) {
            return;
        }
        ObjectNode associations = select(current.path(CERTIFICATE_ASSOCIATIONS), OWNER_UUID, GROUP_UUIDS);
        JsonNode supplied = changes.path(CERTIFICATE_ASSOCIATIONS);
        if (!supplied.isMissingNode()) {
            if (!supplied.isObject()) {
                throw new IllegalArgumentException("certificateAssociations must be an object.");
            }
            allow(supplied, OWNER_UUID, GROUP_UUIDS, CUSTOM_ATTRIBUTES);
            for (String field : new String[]{OWNER_UUID, GROUP_UUIDS}) {
                if (supplied.has(field)) {
                    associations.set(field, supplied.get(field));
                }
            }
        }
        CertificateConfiguration.uuids(associations, OWNER_UUID);
        if (associations.has(GROUP_UUIDS)) {
            associations.set(GROUP_UUIDS, strings(associations.path(GROUP_UUIDS), GROUP_UUIDS, true));
        }
        associations
                .set(CUSTOM_ATTRIBUTES,
                        support
                                .custom("certificates", current.path(CERTIFICATE_ASSOCIATIONS).path(CUSTOM_ATTRIBUTES),
                                        supplied.path(CUSTOM_ATTRIBUTES)));
        body.set(CERTIFICATE_ASSOCIATIONS, associations);
    }

    private void encodeOperation(ObjectNode body, JsonNode current, String ra, String field, String operation) {
        JsonNode supplied = body.path(field);
        // Response attribute arrays are retained through schema validation; callers supply plain objects.
        JsonNode fresh = supplied.isObject() ? supplied : JSON.createObjectNode();
        if (ra == null) {
            if (!fresh.isEmpty() || !CertificateConfiguration.array(current.path(field)).isEmpty()) {
                throw new IllegalArgumentException(field + " requires a default raProfileUuid.");
            }
            body.set(field, JSON.createArrayNode());
            return;
        }
        JsonNode profile = client.get("/v1/raProfiles/{profile}", id(ra));
        String authority = id(profile.path("authorityInstanceUuid").asString(null));
        body
                .set(field,
                        support
                                .operationAttributes(authority, ra, CertificateConfiguration.RA, operation,
                                        current.path(field), fresh));
    }

    private static void requireFreshProtected(String protocol, JsonNode merged, JsonNode supplied) {
        if ("scep".equals(protocol)) {
            boolean challenge = !merged.has(ENABLE_CHALLENGE_PASSWORD)
                    || merged.path(ENABLE_CHALLENGE_PASSWORD).asBoolean(false);
            if (challenge && !CERTIFICATE_REGISTRATION.equals(merged.path(CHALLENGE_SOURCE).asString())) {
                fresh(supplied, CHALLENGE_PASSWORD);
            }
            if (merged.path(ENABLE_INTUNE).asBoolean(false)) {
                fresh(supplied, INTUNE_APPLICATION_KEY);
            }
        }
        if ("cmp".equals(protocol) && !registrationChallenge(merged)
                && (SHARED_SECRET.equals(merged.path(REQUEST_PROTECTION_METHOD).asString())
                        || SHARED_SECRET.equals(merged.path(RESPONSE_PROTECTION_METHOD).asString()))) {
            fresh(supplied, SHARED_SECRET);
        }
    }

    private static boolean registrationChallenge(JsonNode profile) {
        return CERTIFICATE_REGISTRATION.equals(profile.path(CHALLENGE_SOURCE).asString());
    }

    private static void fresh(JsonNode input, String field) {
        String value = input.path(field).asString(null);
        if (value == null || value.isBlank() || value.matches("[\\*•]+")) {
            throw new IllegalArgumentException("Supply a fresh " + field + " for this complete replacement.");
        }
    }

    private void fieldVersions(JsonNode input) {
        if (input.has(ENABLE_CHALLENGE_PASSWORD)) {
            support.require(IlmFeature.ENROLLMENT_SCEP_CHALLENGE);
        }
        for (String field : new String[]{
                EAB_SECRET_UUIDS,
                PREAUTHORIZED_IDENTIFIERS,
                IDENTIFIER_AUTHORIZATION_MODE,
                CHALLENGE_SOURCE}) {
            if (input.has(field)) {
                support.require(IlmFeature.ENROLLMENT_EXTENSIONS);
            }
        }
    }

    private static void choice(JsonNode input, String field, Set<String> values) {
        if (input.hasNonNull(field) && !values.contains(input.path(field).asString())) {
            throw new IllegalArgumentException("Unsupported " + field + ".");
        }
    }

    private static String[] fields(String protocol) {
        return switch (CertificateConfiguration.protocol(protocol)) {
            case "acme" -> ACME;
            case "scep" -> SCEP;
            default -> CMP;
        };
    }

    private static String base(String protocol) {
        return switch (CertificateConfiguration.protocol(protocol)) {
            case "acme" -> "/v1/acmeProfiles";
            case "scep" -> "/v1/scepProfiles";
            default -> "/v1/cmpProfiles";
        };
    }

    private static String metadata(JsonNode value) {
        return describe("Enrollment profile", value, "uuid", "name", DESCRIPTION, "enabled", VARIANT, "raProfile.uuid",
                "raProfile.name", "directoryUrl", "scepUrl", "cmpUrl", TERMS_OF_SERVICE_URL, WEBSITE_URL,
                REQUIRE_CONTACT, REQUIRE_TERMS_OF_SERVICE, REQUEST_PROTECTION_METHOD, RESPONSE_PROTECTION_METHOD,
                ENABLE_CHALLENGE_PASSWORD, ENABLE_INTUNE, CHALLENGE_SOURCE, IDENTIFIER_AUTHORIZATION_MODE);
    }
}
