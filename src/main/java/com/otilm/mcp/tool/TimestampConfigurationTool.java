package com.otilm.mcp.tool;

import com.otilm.mcp.service.TimestampConfigurationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class TimestampConfigurationTool {
    private final TimestampConfigurationService service;

    public TimestampConfigurationTool(TimestampConfigurationService service) {
        this.service = service;
    }

    @McpTool(name = "get_tsp_profile",
            description = "Read TSP profile metadata, signing/vault references and allowed authentication; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTspProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.getTspProfile(uuid);
    }

    @McpTool(name = "create_tsp_profile",
            description = "Create TSP profile; ILM 2.18+. Configuration: name required; description, defaultSigningProfileUuid, customAttributes optional. vaultProfileUuid and allowedAuthenticationMethods require 2.19+; basicPassword requires a vault profile.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createTspProfile(@McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.createTspProfile(configuration);
    }

    @McpTool(name = "update_tsp_profile",
            description = "Update TSP profile with omitted readable fields preserved. Configuration matches create_tsp_profile. Authentication and vault fields require ILM 2.19+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTspProfile(@McpToolParam(description = "Object UUID.") String uuid, @McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.updateTspProfile(uuid, configuration);
    }

    @McpTool(name = "delete_tsp_profile",
            description = "Delete TSP profile with dependency checks; ILM 2.18+ and full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTspProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.deleteTspProfile(uuid);
    }

    @McpTool(name = "enable_tsp_profile", description = "Enable TSP profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableTspProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.enableTspProfile(uuid);
    }

    @McpTool(name = "disable_tsp_profile", description = "Disable TSP profile; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableTspProfile(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.disableTspProfile(uuid);
    }

    @McpTool(name = "list_tsp_basic_credentials",
            description = "List TSP Basic credential usernames and mapped-user metadata only; ILM 2.19+. Passwords never returned.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String listTspBasicCredentials(@McpToolParam(description = "TSP profile UUID.") String tspUuid) {
        return service.listTspBasicCredentials(tspUuid);
    }

    @McpTool(name = "get_tsp_basic_credential",
            description = "Read TSP Basic credential username and mapped user only; ILM 2.19+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTspBasicCredential(@McpToolParam(description = "TSP profile UUID.") String tspUuid,
            @McpToolParam(description = "Object UUID.") String uuid) {
        return service.getTspBasicCredential(tspUuid, uuid);
    }

    @McpTool(name = "create_tsp_basic_credential",
            description = "Create a TSP Basic credential; ILM 2.19+. Configuration: username, password and mappedUserUuid required. Sends a supplied password and returns metadata only.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createTspBasicCredential(@McpToolParam(description = "TSP profile UUID.") String tspUuid,
            @McpToolParam(
                    description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.createTspBasicCredential(tspUuid, configuration);
    }

    @McpTool(name = "update_tsp_basic_credential",
            description = "Update TSP Basic credential; ILM 2.19+. Configuration: username, password, mappedUserUuid. Omitted metadata retained; blank/omitted password retains the existing secret, but changing username requires a fresh password. Never returns passwords.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTspBasicCredential(@McpToolParam(description = "TSP profile UUID.") String tspUuid,
            @McpToolParam(description = "Object UUID.") String uuid, @McpToolParam(
                    description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.updateTspBasicCredential(tspUuid, uuid, configuration);
    }

    @McpTool(name = "delete_tsp_basic_credential",
            description = "Delete TSP Basic credential; ILM 2.19+ and full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTspBasicCredential(@McpToolParam(description = "TSP profile UUID.") String tspUuid,
            @McpToolParam(description = "Object UUID.") String uuid) {
        return service.deleteTspBasicCredential(tspUuid, uuid);
    }

    @McpTool(name = "search_time_quality_configurations",
            description = "Search NTP time quality configurations; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String searchTimeQualityConfigurations(
            @McpToolParam(description = "Optional JSON array of search filters.", required = false) String filters,
            @McpToolParam(description = "Page size, default 10.", required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number, default 1.", required = false) Integer pageNumber) {
        return service.searchTimeQualityConfigurations(filters, itemsPerPage, pageNumber);
    }

    @McpTool(name = "get_time_quality_configuration",
            description = "Read NTP accuracy, servers, sampling and clock-drift configuration; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTimeQualityConfiguration(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.getTimeQualityConfiguration(uuid);
    }

    @McpTool(name = "get_time_quality_signing_profiles",
            description = "List signing profiles referencing this time quality configuration; ILM 2.18+.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getTimeQualitySigningProfiles(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.getTimeQualitySigningProfiles(uuid);
    }

    @McpTool(name = "create_time_quality_configuration",
            description = "Create time quality configuration; ILM 2.18+. Configuration: name, accuracy, ntpServers, ntpCheckInterval, ntpCheckTimeout, maxClockDrift required; ntpSamplesPerServer, ntpServersMinReachable, leapSecondGuard, customAttributes optional. Durations use positive ISO 8601 strings.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createTimeQualityConfiguration(@McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.createTimeQualityConfiguration(configuration);
    }

    @McpTool(name = "update_time_quality_configuration",
            description = "Update time quality configuration; ILM 2.18+. Same fields as create_time_quality_configuration; omitted fields retained and all duration/count relationships validated.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateTimeQualityConfiguration(@McpToolParam(description = "Object UUID.") String uuid, @McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.updateTimeQualityConfiguration(uuid, configuration);
    }

    @McpTool(name = "delete_time_quality_configuration",
            description = "Delete time quality configuration with dependency checks; ILM 2.18+ and full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteTimeQualityConfiguration(@McpToolParam(description = "Object UUID.") String uuid) {
        return service.deleteTimeQualityConfiguration(uuid);
    }

}
