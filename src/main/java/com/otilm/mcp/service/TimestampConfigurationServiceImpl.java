package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;
import com.otilm.mcp.client.ConfigurationApiClient;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TimestampConfigurationServiceImpl implements TimestampConfigurationService {
    private static final String UUID_ENDPOINT = "/{uuid}";
    private static final String ACCURACY = "accuracy";
    private static final String ALLOWED_AUTHENTICATION_METHODS = "allowedAuthenticationMethods";
    private static final String CUSTOM_ATTRIBUTES = "customAttributes";
    private static final String DEFAULT_SIGNING_PROFILE_UUID = "defaultSigningProfileUuid";
    private static final String DESCRIPTION = "description";
    private static final String LEAP_SECOND_GUARD = "leapSecondGuard";
    private static final String MAPPED_USER_UUID = "mappedUserUuid";
    private static final String MAX_CLOCK_DRIFT = "maxClockDrift";
    private static final String NTP_CHECK_INTERVAL = "ntpCheckInterval";
    private static final String NTP_CHECK_TIMEOUT = "ntpCheckTimeout";
    private static final String NTP_SAMPLES_PER_SERVER = "ntpSamplesPerServer";
    private static final String NTP_SERVERS = "ntpServers";
    private static final String NTP_SERVERS_MIN_REACHABLE = "ntpServersMinReachable";
    private static final String PASSWORD = "password";
    private static final String USERNAME = "username";
    private static final String VAULT_PROFILE_UUID = "vaultProfileUuid";

    private static final String TSP = "/v1/tspProfiles/{uuid}";
    private static final String CREDENTIALS = "/v1/tspProfiles/{tspUuid}/basicCredentials";
    private static final String TIME = "/v1/timeQualityConfigurations";
    private static final String[] TSP_FIELDS = {
            "uuid",
            "name",
            DESCRIPTION,
            "enabled",
            "signingUrl",
            "defaultSigningProfile.uuid",
            "defaultSigningProfile.name",
            "vaultProfile.uuid",
            "vaultProfile.name",
            ALLOWED_AUTHENTICATION_METHODS};
    private static final String[] CREDENTIAL_FIELDS = {"uuid", USERNAME, "mappedUser.uuid", "mappedUser.name"};
    private static final String[] TIME_FIELDS = {
            "uuid",
            "name",
            ACCURACY,
            NTP_SERVERS,
            NTP_CHECK_INTERVAL,
            NTP_CHECK_TIMEOUT,
            NTP_SAMPLES_PER_SERVER,
            NTP_SERVERS_MIN_REACHABLE,
            MAX_CLOCK_DRIFT,
            LEAP_SECOND_GUARD};
    private final ConfigurationApiClient client;
    private final PlatformVersionCheck versions;

    public TimestampConfigurationServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    @Override
    public String getTspProfile(String uuid) {
        return tsp("retrieving TSP profile", () -> ConfigurationSupport
                .describe("TSP profile", client.get(TSP, CryptoInput.uuid(uuid)), TSP_FIELDS));
    }

    @Override
    public String createTspProfile(String configuration) {
        return saveTsp(null, configuration);
    }

    @Override
    public String updateTspProfile(String uuid, String configuration) {
        return saveTsp(uuid, configuration);
    }

    private String saveTsp(String uuid, String configuration) {
        return tsp("saving TSP profile", () -> {
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", DESCRIPTION, DEFAULT_SIGNING_PROFILE_UUID, VAULT_PROFILE_UUID,
                            ALLOWED_AUTHENTICATION_METHODS, CUSTOM_ATTRIBUTES);
            if (body.has(VAULT_PROFILE_UUID) || body.has(ALLOWED_AUTHENTICATION_METHODS)) {
                requireAuthentication();
            }
            CryptoInput.text(body, "name", DESCRIPTION, DEFAULT_SIGNING_PROFILE_UUID, VAULT_PROFILE_UUID);
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(TSP, CryptoInput.uuid(uuid));
            CryptoInput.preserve(body, existing, "name", DESCRIPTION, ALLOWED_AUTHENTICATION_METHODS);
            SigningConfigurationInput.reference(body, existing, "defaultSigningProfile");
            SigningConfigurationInput.reference(body, existing, "vaultProfile");
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "TSP profile name"));
            validateTspReferences(body);
            validateTspAuthentication(body);
            body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "tspProfiles", existing, body));
            return ConfigurationSupport
                    .describe("TSP profile saved",
                            uuid == null ? client.post("/v1/tspProfiles", body) : client.put(TSP, body, uuid),
                            TSP_FIELDS);
        });
    }

    private static void validateTspReferences(ObjectNode body) {
        for (String field : new String[]{DEFAULT_SIGNING_PROFILE_UUID, VAULT_PROFILE_UUID}) {
            if (body.hasNonNull(field)) {
                CryptoInput.uuid(body.path(field).asString());
            }
        }
    }

    private void validateTspAuthentication(ObjectNode body) {
        if (!body.has(ALLOWED_AUTHENTICATION_METHODS)) {
            return;
        }
        requireAuthentication();
        for (JsonNode method : CryptoInput.strings(body.path(ALLOWED_AUTHENTICATION_METHODS), false)) {
            if (!Set.of("clientCertificate", "bearerToken", "basicPassword").contains(method.asString())) {
                throw new IllegalArgumentException("Unsupported TSP authentication method.");
            }
            if ("basicPassword".equals(method.asString()) && !body.hasNonNull(VAULT_PROFILE_UUID)) {
                throw new IllegalArgumentException("Basic password authentication requires a vault profile UUID.");
            }
        }
    }

    @Override
    public String deleteTspProfile(String uuid) {
        return tsp("deleting TSP profile", () -> {
            client.delete(TSP, CryptoInput.uuid(uuid));
            return "Deleted TSP profile [" + uuid + "].";
        });
    }

    @Override
    public String enableTspProfile(String uuid) {
        return state(uuid, "enable");
    }

    @Override
    public String disableTspProfile(String uuid) {
        return state(uuid, "disable");
    }

    private String state(String uuid, String operation) {
        return tsp(operation + " TSP profile", () -> {
            client.patch(TSP + "/" + operation, null, CryptoInput.uuid(uuid));
            return "TSP profile " + operation + " request accepted [" + uuid + "].";
        });
    }

    @Override
    public String listTspBasicCredentials(String tspUuid) {
        return authentication("listing TSP basic credentials", () -> ConfigurationSupport
                .list("TSP basic credentials", client.get(CREDENTIALS, CryptoInput.uuid(tspUuid)), CREDENTIAL_FIELDS));
    }

    @Override
    public String getTspBasicCredential(String tspUuid, String uuid) {
        return authentication("retrieving TSP basic credential", () -> ConfigurationSupport
                .describe("TSP basic credential",
                        client.get(CREDENTIALS + UUID_ENDPOINT, CryptoInput.uuid(tspUuid), CryptoInput.uuid(uuid)),
                        CREDENTIAL_FIELDS));
    }

    @Override
    public String createTspBasicCredential(String tspUuid, String configuration) {
        return saveCredential(tspUuid, null, configuration);
    }

    @Override
    public String updateTspBasicCredential(String tspUuid, String uuid, String configuration) {
        return saveCredential(tspUuid, uuid, configuration);
    }

    private String saveCredential(String tspUuid, String uuid, String configuration) {
        return authentication("saving TSP basic credential", () -> {
            CryptoInput.uuid(tspUuid);
            ObjectNode body = CryptoInput.configuration(configuration, USERNAME, PASSWORD, MAPPED_USER_UUID);
            CryptoInput.text(body, USERNAME, PASSWORD, MAPPED_USER_UUID);
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(CREDENTIALS + UUID_ENDPOINT, tspUuid, CryptoInput.uuid(uuid));
            CryptoInput.preserve(body, existing, USERNAME);
            SigningConfigurationInput.reference(body, existing, "mappedUser");
            body.put(USERNAME, ConfigurationSupport.required(body.path(USERNAME).asString(null), "Basic username"));
            CryptoInput.uuid(body.path(MAPPED_USER_UUID).asString(null));
            boolean renamed = !body.path(USERNAME).asString().equals(existing.path(USERNAME).asString());
            String password = body.path(PASSWORD).asString(null);
            if (uuid == null || renamed) {
                ConfigurationSupport.required(password, "A fresh password");
            }
            if (password != null && AttributeContentValues.masked(body.path(PASSWORD))) {
                throw new IllegalArgumentException("A fresh password is required; masked values cannot be reused.");
            }
            if (password == null || password.isBlank()) {
                body.remove(PASSWORD);
            }
            return ConfigurationSupport
                    .describe("TSP basic credential saved",
                            uuid == null
                                    ? client.post(CREDENTIALS, body, tspUuid)
                                    : client.put(CREDENTIALS + UUID_ENDPOINT, body, tspUuid, uuid),
                            CREDENTIAL_FIELDS);
        });
    }

    @Override
    public String deleteTspBasicCredential(String tspUuid, String uuid) {
        return authentication("deleting TSP basic credential", () -> {
            client.delete(CREDENTIALS + UUID_ENDPOINT, CryptoInput.uuid(tspUuid), CryptoInput.uuid(uuid));
            return "Deleted TSP basic credential [" + uuid + "].";
        });
    }

    @Override
    public String searchTimeQualityConfigurations(String filters, Integer itemsPerPage, Integer pageNumber) {
        return time("searching time quality configurations", () -> ConfigurationSupport
                .list("Time quality configurations",
                        client
                                .post(TIME + "/list",
                                        SearchRequests.of(new SearchRequestDto(), filters, itemsPerPage, pageNumber)),
                        TIME_FIELDS));
    }

    @Override
    public String getTimeQualityConfiguration(String uuid) {
        return time("retrieving time quality configuration",
                () -> ConfigurationSupport
                        .describe("Time quality configuration",
                                client.get(TIME + UUID_ENDPOINT, CryptoInput.uuid(uuid)), TIME_FIELDS));
    }

    @Override
    public String getTimeQualitySigningProfiles(String uuid) {
        return time("retrieving time quality references",
                () -> ConfigurationSupport
                        .list("Referencing signing profiles",
                                client.get(TIME + "/{uuid}/signingProfiles", CryptoInput.uuid(uuid)), "uuid", "name",
                                "enabled", "version"));
    }

    @Override
    public String createTimeQualityConfiguration(String configuration) {
        return saveTime(null, configuration);
    }

    @Override
    public String updateTimeQualityConfiguration(String uuid, String configuration) {
        return saveTime(uuid, configuration);
    }

    private String saveTime(String uuid, String configuration) {
        return time("saving time quality configuration", () -> {
            ObjectNode body = CryptoInput
                    .configuration(configuration, "name", ACCURACY, NTP_SERVERS, NTP_CHECK_INTERVAL,
                            NTP_SAMPLES_PER_SERVER, NTP_CHECK_TIMEOUT, NTP_SERVERS_MIN_REACHABLE, MAX_CLOCK_DRIFT,
                            LEAP_SECOND_GUARD, CUSTOM_ATTRIBUTES);
            JsonNode existing = uuid == null
                    ? ConfigurationAttributes.JSON.createObjectNode()
                    : client.get(TIME + UUID_ENDPOINT, CryptoInput.uuid(uuid));
            CryptoInput.preserve(body, existing, TIME_FIELDS);
            body.remove("uuid");
            CryptoInput.text(body, "name", ACCURACY, NTP_CHECK_INTERVAL, NTP_CHECK_TIMEOUT, MAX_CLOCK_DRIFT);
            CryptoInput.bool(body, LEAP_SECOND_GUARD);
            body.put("name", ConfigurationSupport.required(body.path("name").asString(null), "Time quality name"));
            validateTime(body);
            body.set(CUSTOM_ATTRIBUTES, CryptoInput.custom(client, "timeQualityConfigurations", existing, body));
            return ConfigurationSupport
                    .describe("Time quality configuration saved",
                            uuid == null ? client.post(TIME, body) : client.put(TIME + UUID_ENDPOINT, body, uuid),
                            TIME_FIELDS);
        });
    }

    private static void validateTime(ObjectNode body) {
        Duration accuracy = duration(body, ACCURACY);
        Duration interval = duration(body, NTP_CHECK_INTERVAL);
        Duration timeout = duration(body, NTP_CHECK_TIMEOUT);
        Duration drift = duration(body, MAX_CLOCK_DRIFT);
        if (drift.compareTo(accuracy) >= 0 || timeout.compareTo(interval) >= 0 || interval.compareTo(accuracy) >= 0) {
            throw new IllegalArgumentException(
                    "Time quality requires maxClockDrift < accuracy and ntpCheckTimeout < ntpCheckInterval < accuracy.");
        }
        JsonNode servers = CryptoInput.strings(body.path(NTP_SERVERS), false);
        if (servers.isEmpty()) {
            throw new IllegalArgumentException("At least one NTP server is required.");
        }
        for (JsonNode server : servers) {
            if (!hostname(server.asString())) {
                throw new IllegalArgumentException("NTP servers must be hostnames or IP addresses.");
            }
        }
        for (String field : new String[]{NTP_SAMPLES_PER_SERVER, NTP_SERVERS_MIN_REACHABLE}) {
            if (body.has(field) && (!body.path(field).isIntegralNumber() || body.path(field).asInt() <= 0)) {
                throw new IllegalArgumentException("NTP sample and reachable-server counts must be positive integers.");
            }
        }
        if (body.path(NTP_SERVERS_MIN_REACHABLE).asInt(1) > servers.size()) {
            throw new IllegalArgumentException(
                    "NTP minimum reachable servers cannot exceed the configured server count.");
        }
    }

    private static boolean hostname(String value) {
        if (value.isEmpty() || value.length() > 253) {
            return false;
        }
        if (org.bouncycastle.util.IPAddress.isValid(value)) {
            return true;
        }
        for (String label : value.split("\\.", -1)) {
            if (label.length() > 63 || !label.matches("[A-Za-z0-9](?:-*+[A-Za-z0-9])*+")) {
                return false;
            }
        }
        return true;
    }

    private static Duration duration(JsonNode body, String field) {
        try {
            Duration duration = Duration.parse(body.path(field).asString(""));
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("Time quality durations must be positive.");
            }
            return duration;
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Time quality durations must use positive ISO 8601 durations.");
        }
    }

    @Override
    public String deleteTimeQualityConfiguration(String uuid) {
        return time("deleting time quality configuration", () -> {
            client.delete(TIME + UUID_ENDPOINT, CryptoInput.uuid(uuid));
            return "Deleted time quality configuration [" + uuid + "].";
        });
    }

    private void requireAuthentication() {
        versions.unsupported(IlmFeature.TSP_AUTHENTICATION).ifPresent(message -> {
            throw new IllegalArgumentException(message);
        });
    }

    private String authentication(String action, Supplier<String> body) {
        return versions
                .unsupported(IlmFeature.TSP_AUTHENTICATION)
                .orElseGet(() -> ConfigurationSupport.run(action, body));
    }

    private String tsp(String action, Supplier<String> body) {
        return versions.unsupported(IlmFeature.TSP_PROFILES).orElseGet(() -> ConfigurationSupport.run(action, body));
    }

    private String time(String action, Supplier<String> body) {
        return versions.unsupported(IlmFeature.TIME_QUALITY).orElseGet(() -> ConfigurationSupport.run(action, body));
    }
}
