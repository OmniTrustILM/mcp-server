package com.otilm.mcp.service;

import com.otilm.mcp.client.ConfigurationApiClient;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class SettingsServiceImpl implements SettingsService {
    private static final String AUDIT_LOGS = "auditLogs";
    private static final String AUTHENTICATION = "authentication";
    private static final String CERTIFICATES = "certificates";
    private static final String DISABLE_LOCALHOST_USER = "disableLocalhostUser";
    private static final String EVENT_LOGS = "eventLogs";
    private static final String EVENTS = "events";
    private static final String LOGGING = "logging";
    private static final String OAUTH2_PROVIDERS = "oauth2Providers";
    private static final String PLATFORM = "platform";
    private static final String REGISTRATION = "registration";
    private static final String REQUEST_ATTRIBUTES = "requestAttributes";
    private static final String UTILS = "utils";
    private static final String VALIDATION = "validation";

    private static final String ROOT = "/v1/settings";
    private static final String PROVIDER = ROOT + "/authentication/oauth2Providers/{name}";
    private static final Set<String> SECTIONS = Set
            .of(PLATFORM, VALIDATION, REQUEST_ATTRIBUTES, REGISTRATION, UTILS, "cbom", AUTHENTICATION, LOGGING, EVENTS);
    private final ConfigurationApiClient client;
    private final PlatformVersionCheck versions;

    public SettingsServiceImpl(ConfigurationApiClient client, PlatformVersionCheck versions) {
        this.client = client;
        this.versions = versions;
    }

    @Override
    public String getPlatformSettings(String section) {
        return ConfigurationSupport.run("retrieving settings", () -> {
            String selected = section(section);
            String unsupported = unsupported(selected, null);
            if (unsupported != null) {
                return unsupported;
            }
            JsonNode response = client.get(endpoint(selected));
            ObjectNode safe = SettingsFields.display(readable(selected, response));
            return ConfigurationSupport
                    .describe("Settings: " + selected, safe, safe.propertyNames().toArray(String[]::new));
        });
    }

    @Override
    public String updatePlatformSettings(String section, String settings) {
        return update(section, settings, false);
    }

    private String update(String section, String settings, boolean replaceProviders) {
        return ConfigurationSupport.run("updating settings", () -> {
            String selected = section(section);
            ObjectNode patch = ConfigurationSupport.object(settings, "Settings");
            if (patch.isEmpty()) {
                throw new IllegalArgumentException("Supply at least one settings field to update.");
            }
            if (replaceProviders) {
                SettingsFields.allowed(patch, Set.of(OAUTH2_PROVIDERS));
                if (!patch.has(OAUTH2_PROVIDERS)) {
                    throw new IllegalArgumentException("Supply the complete oauth2Providers list.");
                }
            } else if (selected.equals(AUTHENTICATION) && patch.has(OAUTH2_PROVIDERS)) {
                throw new IllegalArgumentException(
                        "Provider-list replacement requires the full-access replace_oauth_providers tool.");
            }
            SettingsFields.validate(selected, patch);
            String unsupported = unsupported(selected, patch);
            if (unsupported != null) {
                return unsupported;
            }
            JsonNode current = client.get(endpoint(selected));
            ObjectNode request = replacement(selected, current, patch);
            validateReplacement(selected, request);
            client.put(endpoint(selected), request);
            if (replaceProviders) {
                return "Authentication settings updated; OAuth provider registrations replaced. Omitted providers and their stored credentials were removed. The localhost flag was preserved.";
            }
            String effect = selected.equals(EVENTS)
                    ? " Trigger associations included in this replacement use the caller's execution identity."
                    : " Omitted readable fields were preserved.";
            return "Settings: " + selected + " updated." + effect;
        });
    }

    @Override
    public String getOAuthProviderSettings(String providerName) {
        return ConfigurationSupport.run("retrieving OAuth provider settings", () -> {
            String name = SettingsFields.providerName(providerName);
            ObjectNode result = SettingsFields.display(provider(client.get(PROVIDER, name), false));
            return ConfigurationSupport
                    .describe("OAuth provider: " + name, result, result.propertyNames().toArray(String[]::new));
        });
    }

    @Override
    public String updateOAuthProviderSettings(String providerName, String settings) {
        return ConfigurationSupport.run("updating OAuth provider settings", () -> {
            String name = SettingsFields.providerName(providerName);
            ObjectNode patch = ConfigurationSupport.object(settings, "OAuth provider settings");
            if (patch.isEmpty()) {
                throw new IllegalArgumentException("Supply at least one OAuth provider field to update.");
            }
            SettingsFields.validateOAuth(patch);
            String unsupported = oauthVersion(patch);
            if (unsupported != null) {
                return unsupported;
            }
            JsonNode current = client.get(PROVIDER, name);
            ObjectNode request = SettingsFields.merge(provider(current, true), patch);
            request.remove("name");
            SettingsFields.validateOAuth(request);
            SettingsFields.completeOAuth(request);
            client.put(PROVIDER, request, name);
            return "OAuth provider " + name
                    + " updated. Omitted readable fields and the existing secret were preserved.";
        });
    }

    @Override
    public String replaceOAuthProviders(String settings) {
        return update(AUTHENTICATION, settings, true);
    }

    @Override
    public String removeOAuthProvider(String providerName) {
        return ConfigurationSupport.run("removing OAuth provider", () -> {
            String name = SettingsFields.providerName(providerName);
            client.delete(PROVIDER, name);
            return "OAuth provider " + name + " removed.";
        });
    }

    private ObjectNode replacement(String section, JsonNode current, ObjectNode patch) {
        ObjectNode request = SettingsFields.object();
        switch (section) {
            case PLATFORM -> {
                if (patch.has(UTILS)) {
                    request
                            .set(UTILS,
                                    SettingsFields
                                            .merge(SettingsFields.fields(current.path(UTILS), SettingsFields.UTILS),
                                                    (ObjectNode) patch.get(UTILS)));
                }
                if (patch.has(CERTIFICATES)) {
                    ObjectNode certificates = SettingsFields.object();
                    for (var entry : patch.get(CERTIFICATES).properties()) {
                        certificates
                                .set(entry.getKey(),
                                        SettingsFields
                                                .merge(certificate(entry.getKey(),
                                                        current.path(CERTIFICATES).path(entry.getKey())),
                                                        (ObjectNode) entry.getValue()));
                    }
                    request.set(CERTIFICATES, certificates);
                }
            }
            case UTILS,
                    "cbom" ->
                request
                        .set(UTILS, SettingsFields
                                .merge(SettingsFields.fields(current.path(UTILS), SettingsFields.UTILS), patch));
            case VALIDATION, REQUEST_ATTRIBUTES, REGISTRATION -> {
                ObjectNode certificates = SettingsFields.object();
                certificates
                        .set(section, SettingsFields
                                .merge(certificate(section, current.path(CERTIFICATES).path(section)), patch));
                request.set(CERTIFICATES, certificates);
            }
            case AUTHENTICATION -> {
                if (patch.has(DISABLE_LOCALHOST_USER)) {
                    request.set(DISABLE_LOCALHOST_USER, patch.get(DISABLE_LOCALHOST_USER));
                } else if (current.path(DISABLE_LOCALHOST_USER).isBoolean()) {
                    request.set(DISABLE_LOCALHOST_USER, current.get(DISABLE_LOCALHOST_USER));
                } else {
                    throw new IllegalArgumentException(
                            "Cannot preserve the current authentication flag; read settings first.");
                }
                if (patch.has(OAUTH2_PROVIDERS)) {
                    request.set(OAUTH2_PROVIDERS, patch.get(OAUTH2_PROVIDERS));
                }
            }
            case LOGGING, EVENTS -> request = SettingsFields.merge(readable(section, current), patch);
            default -> throw new IllegalArgumentException("Unsupported settings section.");
        }
        return request;
    }

    private static void validateReplacement(String section, ObjectNode request) {
        String wireSection = Set.of(UTILS, "cbom", VALIDATION, REQUEST_ATTRIBUTES, REGISTRATION).contains(section)
                ? PLATFORM
                : section;
        SettingsFields.validate(wireSection, request);
        JsonNode validation = request.path(CERTIFICATES).path(VALIDATION);
        if (validation.path("enabled").asBoolean(false)
                && (validation.path("frequency").isNull() || validation.path("expiringThreshold").isNull()
                        || !validation.has("frequency") || !validation.has("expiringThreshold"))) {
            throw new IllegalArgumentException("Enabled validation requires frequency and expiringThreshold.");
        }
    }

    private static ObjectNode readable(String section, JsonNode source) {
        return switch (section) {
            case PLATFORM -> {
                ObjectNode out = SettingsFields.object();
                out.set(UTILS, SettingsFields.metadata(source.path(UTILS), SettingsFields.UTILS));
                ObjectNode certificates = SettingsFields.object();
                for (String name : Set.of(VALIDATION, REQUEST_ATTRIBUTES, REGISTRATION)) {
                    certificates.set(name, safeCertificate(name, source.path(CERTIFICATES).path(name)));
                }
                out.set(CERTIFICATES, certificates);
                yield out;
            }
            case UTILS, "cbom" -> {
                ObjectNode out = SettingsFields.metadata(source.path(UTILS), SettingsFields.UTILS);
                if (section.equals("cbom")) {
                    out.remove("utilsServiceUrl");
                    out.remove("cbomRepositoryUrl");
                }
                yield out;
            }
            case VALIDATION, REQUEST_ATTRIBUTES, REGISTRATION ->
                safeCertificate(section, source.path(CERTIFICATES).path(section));
            case LOGGING -> {
                ObjectNode out = SettingsFields.object();
                if (source.has(AUDIT_LOGS)) {
                    out.set(AUDIT_LOGS, SettingsFields.metadata(source.path(AUDIT_LOGS), SettingsFields.AUDIT));
                }
                if (source.has(EVENT_LOGS)) {
                    out.set(EVENT_LOGS, SettingsFields.metadata(source.path(EVENT_LOGS), SettingsFields.LOG));
                }
                yield out;
            }
            case AUTHENTICATION -> {
                ObjectNode out = SettingsFields.metadata(source, Set.of(DISABLE_LOCALHOST_USER));
                ObjectNode providers = SettingsFields.object();
                JsonNode registrations = source.path(OAUTH2_PROVIDERS);
                if (registrations.isObject()) {
                    for (var entry : registrations.properties()) {
                        providers.set(entry.getKey(), provider(entry.getValue(), false));
                    }
                } else if (registrations.isArray()) {
                    for (JsonNode entry : registrations) {
                        providers.set(entry.path("name").asString(), provider(entry, false));
                    }
                }
                out.set(OAUTH2_PROVIDERS, providers);
                yield out;
            }
            case EVENTS -> SettingsFields.eventMetadata(source);
            default -> throw new IllegalArgumentException("Unsupported settings section.");
        };
    }

    private static ObjectNode certificate(String section, JsonNode source) {
        return SettingsFields.fields(source, switch (section) {
            case VALIDATION -> SettingsFields.VALIDATION;
            case REQUEST_ATTRIBUTES -> SettingsFields.REQUEST;
            case REGISTRATION -> SettingsFields.REGISTRATION;
            default -> Set.of();
        });
    }

    private static ObjectNode safeCertificate(String section, JsonNode source) {
        Set<String> fields = switch (section) {
            case VALIDATION -> SettingsFields.VALIDATION;
            case REGISTRATION -> SettingsFields.REGISTRATION;
            default -> SettingsFields.REQUEST;
        };
        ObjectNode safe = SettingsFields.metadata(certificate(section, source), fields);
        if (source.has(REQUEST_ATTRIBUTES)) {
            safe.set(REQUEST_ATTRIBUTES, SettingsFields.definitionMetadata(source.get(REQUEST_ATTRIBUTES)));
        }
        return safe;
    }

    private static ObjectNode provider(JsonNode source, boolean forWrite) {
        ObjectNode out = forWrite
                ? SettingsFields.fields(source, SettingsFields.OAUTH)
                : SettingsFields.metadata(source, SettingsFields.OAUTH);
        out.remove("clientSecret");
        if (!forWrite) {
            out.remove("jwkSet");
        }
        if (!forWrite && source.path("name").isString()) {
            out.set("name", source.get("name"));
        }
        return out;
    }

    private String unsupported(String section, ObjectNode patch) {
        IlmFeature feature = switch (section) {
            case REQUEST_ATTRIBUTES -> IlmFeature.SETTINGS_REQUEST_ATTRIBUTES;
            case REGISTRATION -> IlmFeature.SETTINGS_CERTIFICATE_REGISTRATION;
            case "cbom" -> IlmFeature.SETTINGS_CBOM_SYNC;
            default -> null;
        };
        if (feature != null) {
            return versions.unsupported(feature).orElse(null);
        }
        if (patch == null) {
            return null;
        }
        String refusal = unsupportedCertificateSettings(patch.path(CERTIFICATES));
        if (refusal != null) {
            return refusal;
        }
        refusal = unsupportedUtilitySettings(section.equals(UTILS) ? patch : patch.path(UTILS));
        if (refusal != null) {
            return refusal;
        }
        return unsupportedProviders(patch);
    }

    private String unsupportedCertificateSettings(JsonNode certificates) {
        if (certificates.has(REQUEST_ATTRIBUTES)) {
            String refusal = versions.unsupported(IlmFeature.SETTINGS_REQUEST_ATTRIBUTES).orElse(null);
            if (refusal != null) {
                return refusal;
            }
        }
        if (certificates.has(REGISTRATION)) {
            return versions.unsupported(IlmFeature.SETTINGS_CERTIFICATE_REGISTRATION).orElse(null);
        }
        return null;
    }

    private String unsupportedUtilitySettings(JsonNode utility) {
        for (var entry : utility.properties()) {
            if (entry.getKey().startsWith("cbomSync")) {
                String refusal = versions.unsupported(IlmFeature.SETTINGS_CBOM_SYNC).orElse(null);
                if (refusal != null) {
                    return refusal;
                }
            }
        }
        return null;
    }

    private String unsupportedProviders(ObjectNode patch) {
        if (!patch.has(OAUTH2_PROVIDERS)) {
            return null;
        }
        for (JsonNode registration : patch.get(OAUTH2_PROVIDERS)) {
            String refusal = oauthVersion(registration);
            if (refusal != null) {
                return refusal;
            }
        }
        return null;
    }

    private String oauthVersion(JsonNode patch) {
        return patch.has("usernameClaim")
                ? versions.unsupported(IlmFeature.SETTINGS_OAUTH_USERNAME_CLAIM).orElse(null)
                : null;
    }

    private static String endpoint(String section) {
        return ROOT + "/" + (Set.of(AUTHENTICATION, LOGGING, EVENTS).contains(section) ? section : PLATFORM);
    }

    private static String section(String selected) {
        String value = ConfigurationSupport.required(selected, "Settings section");
        if (!SECTIONS.contains(value)) {
            throw new IllegalArgumentException(
                    "Unsupported settings section. Choose platform, validation, requestAttributes, registration, utils, cbom, authentication, logging or events.");
        }
        return value;
    }
}
