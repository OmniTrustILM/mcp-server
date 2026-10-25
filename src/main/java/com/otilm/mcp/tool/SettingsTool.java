package com.otilm.mcp.tool;

import com.otilm.mcp.service.SettingsService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SettingsTool {
    private static final String PLATFORM_SECTION = "platform (default), utils, validation, requestAttributes (ILM 2.19+), registration (ILM 2.19+) or cbom (ILM 2.20+)";
    private static final String PROVIDER = "OAuth provider registration name (letters, digits, dot, underscore or hyphen)";
    private static final String OAUTH_SCHEMA = "JSON object: issuerUrl, clientId, clientSecret (fresh value only), authorizationUrl, tokenUrl, jwkSetUrl, jwkSet, scope/string array, logoutUrl, postLogoutUrl, userInfoUrl, audiences/string array, skew/nonnegative integer, sessionMaxInactiveInterval/nonnegative integer, usernameClaim (ILM 2.18+), allowedDelegates (ILM 2.21+, array of nonblank strings, each at most 255 characters and unique after trimming, case sensitive; [] disables delegation), delegateClaim (ILM 2.21+, nonblank string). Supplied arrays replace their complete lists. Omitted readable values are preserved; omitted clientSecret retains the stored secret.";
    private static final String LOG_SCHEMA = "JSON object with auditLogs and/or eventLogs groups. Each accepts logAllModules/logAllResources booleans and loggedModules/ignoredModules/loggedResources/ignoredResources code arrays. auditLogs also accepts output (NONE, ALL, CONSOLE, DATABASE) and verbose boolean. Omitted groups and fields are preserved.";
    private final SettingsService service;

    public SettingsTool(SettingsService service) {
        this.service = service;
    }

    @McpTool(name = "get_platform_settings",
            description = "Read supported platform settings by section, with request-attribute definition metadata only. Secrets, file/default content and branding are omitted. Request defaults/registration require ILM 2.19; CBOM sync requires ILM 2.20.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getPlatformSettings(@McpToolParam(description = PLATFORM_SECTION, required = false) String section) {
        return service.getPlatformSettings(section == null ? "platform" : section);
    }

    @McpTool(name = "update_platform_settings",
            description = "Merge supported platform settings with their current readable fields, preserving omitted URLs and certificate groups. Input null explicitly resets nullable fields. validation: enabled/frequency/expiringThreshold; requestAttributes: v3 data definitions with fieldMapping and externalCsrValidationStrict (2.19+); registration: defaultIssuanceWindowDays/maxFailedAttempts (2.19+); utils: utilsServiceUrl/cbomRepositoryUrl and flat cbomSync* fields (2.20+). cbom accepts only cbomSync* tunables. platform wraps utils and/or certificates groups. Unsupported fields are refused before writes.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updatePlatformSettings(@McpToolParam(description = PLATFORM_SECTION) String section, @McpToolParam(
            description = "JSON object containing changed fields in the selected section; omitted fields are preserved. requestAttributes arrays replace the complete ordered definition list.") String settings) {
        return service.updatePlatformSettings(section, settings);
    }

    @McpTool(name = "get_authentication_settings",
            description = "Read authentication flags and OAuth provider metadata with client secrets and embedded key material omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getAuthenticationSettings() {
        return service.getPlatformSettings("authentication");
    }

    @McpTool(name = "update_authentication_settings",
            description = "Update the disableLocalhostUser authentication flag while preserving all OAuth providers. Provider-list replacement is refused; use the full-access replace_oauth_providers tool for an intentional replacement.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateAuthenticationSettings(@McpToolParam(
            description = "JSON object containing disableLocalhostUser boolean. Use update_oauth_provider_settings for one provider.") String settings) {
        return service.updatePlatformSettings("authentication", settings);
    }

    @McpTool(name = "get_oauth_provider_settings",
            description = "Read one OAuth provider's nonsecret settings. Client secrets and embedded key material are omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getOAuthProviderSettings(@McpToolParam(description = PROVIDER) String providerName) {
        return service.getOAuthProviderSettings(providerName);
    }

    @McpTool(name = "update_oauth_provider_settings",
            description = "Merge one existing OAuth provider with supplied values, preserving omitted URLs, delegation settings and stored client secret. Explicit replacement credentials must be fresh. usernameClaim requires ILM 2.18+; allowedDelegates and delegateClaim changes require ILM 2.21+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateOAuthProviderSettings(@McpToolParam(description = PROVIDER) String providerName,
            @McpToolParam(description = OAUTH_SCHEMA) String settings) {
        return service.updateOAuthProviderSettings(providerName, settings);
    }

    @McpTool(name = "replace_oauth_providers",
            description = "Replace the entire OAuth provider registration list. Omitted providers and their stored credentials are removed. Every included provider needs fresh credentials; an empty list removes all providers. usernameClaim requires ILM 2.18+; allowedDelegates and delegateClaim require ILM 2.21+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String replaceOAuthProviders(@McpToolParam(
            description = "JSON object with oauth2Providers: complete provider objects including name, fresh clientSecret and JWK Set URL or encoded JWK Set. allowedDelegates (ILM 2.21+) is an array of nonblank strings, each at most 255 characters and unique after trimming, case sensitive; [] disables delegation. delegateClaim (ILM 2.21+) is a nonblank string. [] for oauth2Providers removes all providers. The existing localhost flag is preserved.") String settings) {
        return service.replaceOAuthProviders(settings);
    }

    @McpTool(name = "remove_oauth_provider",
            description = "Remove one OAuth provider registration from platform authentication settings.")
    public String removeOAuthProvider(@McpToolParam(description = PROVIDER) String providerName) {
        return service.removeOAuthProvider(providerName);
    }

    @McpTool(name = "get_logging_settings", description = "Read platform audit and event logging configuration.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getLoggingSettings() {
        return service.getPlatformSettings("logging");
    }

    @McpTool(name = "update_logging_settings",
            description = "Merge logging settings, preserving omitted fields. Supplied module/resource arrays replace their respective complete lists.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateLoggingSettings(@McpToolParam(description = LOG_SCHEMA) String settings) {
        return service.updatePlatformSettings("logging", settings);
    }

    @McpTool(name = "get_event_settings", description = "Read platform event-to-trigger associations.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getEventSettings() {
        return service.getPlatformSettings("events");
    }

    @McpTool(name = "update_event_settings",
            description = "Replace trigger UUID lists for selected platform events while preserving other events. All included associations use the caller's execution identity after the update.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateEventSettings(@McpToolParam(
            description = "JSON object: eventsMapping maps supported ResourceEvent codes to complete ordered arrays of trigger UUIDs; [] clears one event.") String settings) {
        return service.updatePlatformSettings("events", settings);
    }
}
