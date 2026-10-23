package com.otilm.mcp.tool;

import com.otilm.mcp.service.EntityLocationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class LocationTool {
    private final EntityLocationService service;

    public LocationTool(EntityLocationService service) {
        this.service = service;
    }

    @McpTool(name = "search_locations",
            description = "Search locations with optional filters and pagination; metadata only.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String searchLocations(
            @McpToolParam(description = "Optional JSON array of search filters.", required = false) String filters,
            @McpToolParam(description = "Page size, default 10.", required = false) Integer itemsPerPage,
            @McpToolParam(description = "Page number, default 1.", required = false) Integer pageNumber) {
        return service.searchLocations(filters, itemsPerPage, pageNumber);
    }

    @McpTool(name = "get_location_searchable_fields", description = "Discover supported location search filters.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getLocationSearchableFields() {
        return service.getLocationSearchableFields();
    }

    @McpTool(name = "get_location",
            description = "Read location metadata, provider capabilities and certificate associations; protected content omitted.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid) {
        return service.getLocation(entityUuid, locationUuid);
    }

    @McpTool(name = "get_location_attributes",
            description = "Discover location registration schema and callbacks for the entity.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getLocationAttributes(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(
                    description = "Optional JSON object of schema attribute names and values for callback resolution.",
                    required = false) String values) {
        return service.getLocationAttributes(entityUuid, values);
    }

    @McpTool(name = "validate_location_attributes",
            description = "Validate schema-driven location attributes through the entity provider before creation.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String validateLocationAttributes(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "JSON object of schema attribute names and values.") String attributes) {
        return service.validateLocationAttributes(entityUuid, attributes);
    }

    @McpTool(name = "create_location",
            description = "Create location. Configuration: name required; description, enabled, attributes and customAttributes optional.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String createLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid, @McpToolParam(
            description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.createLocation(entityUuid, configuration);
    }

    @McpTool(name = "update_location",
            description = "Update location. Configuration: description, enabled, attributes, customAttributes. Full attribute replacements preserve readable omitted content and require fresh protected values.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String updateLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid, @McpToolParam(
                    description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.updateLocation(entityUuid, locationUuid, configuration);
    }

    @McpTool(name = "delete_location",
            description = "Delete a location association using authoritative dependency checks; requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String deleteLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid) {
        return service.deleteLocation(entityUuid, locationUuid);
    }

    @McpTool(name = "enable_location", description = "Enable the location for provider operations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String enableLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid) {
        return service.enableLocation(entityUuid, locationUuid);
    }

    @McpTool(name = "disable_location", description = "Disable the location for provider operations.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String disableLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid) {
        return service.disableLocation(entityUuid, locationUuid);
    }

    @McpTool(name = "get_location_push_attributes",
            description = "Discover provider schema used when pushing a certificate to a location.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getLocationPushAttributes(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid,
            @McpToolParam(
                    description = "Optional JSON object of schema attribute names and values for callback resolution.",
                    required = false) String values) {
        return service.getLocationPushAttributes(entityUuid, locationUuid, values);
    }

    @McpTool(name = "get_location_csr_attributes",
            description = "Discover provider schema used to generate a CSR at the location.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true))
    public String getLocationCsrAttributes(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid,
            @McpToolParam(
                    description = "Optional JSON object of schema attribute names and values for callback resolution.",
                    required = false) String values) {
        return service.getLocationCsrAttributes(entityUuid, locationUuid, values);
    }

    @McpTool(name = "push_certificate_to_location",
            description = "Deploy an existing certificate and supported key material to the provider location. May write provider storage; returns acceptance and metadata.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String pushCertificateToLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid,
            @McpToolParam(description = "Certificate UUID.") String certificateUuid,
            @McpToolParam(description = "JSON object of schema attribute names and values.") String attributes) {
        return service.pushCertificateToLocation(entityUuid, locationUuid, certificateUuid, attributes);
    }

    @McpTool(name = "remove_certificate_from_location",
            description = "Remove a certificate and supported key material from the provider location; may destroy remote material and requires full access.",
            annotations = @McpTool.McpAnnotations(destructiveHint = true))
    public String removeCertificateFromLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid,
            @McpToolParam(description = "Certificate UUID.") String certificateUuid) {
        return service.removeCertificateFromLocation(entityUuid, locationUuid, certificateUuid);
    }

    @McpTool(name = "renew_certificate_in_location",
            description = "Renew a certificate at the provider location through its supported CSR and renewal operations; returns acceptance and actual state.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String renewCertificateInLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid,
            @McpToolParam(description = "Certificate UUID.") String certificateUuid) {
        return service.renewCertificateInLocation(entityUuid, locationUuid, certificateUuid);
    }

    @McpTool(name = "sync_location",
            description = "Synchronize location inventory with the provider; returns acceptance and actual state.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String syncLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid) {
        return service.syncLocation(entityUuid, locationUuid);
    }

    @McpTool(name = "issue_certificate_to_location",
            description = "Generate a CSR, issue and deploy a certificate at the location. Configuration: raProfileUuid required; csrAttributes, issueAttributes, customAttributes and certificateCustomAttributes optional name-value objects. Reports pending processing.",
            annotations = @McpTool.McpAnnotations(destructiveHint = false))
    public String issueCertificateToLocation(@McpToolParam(description = "Entity instance UUID.") String entityUuid,
            @McpToolParam(description = "Location UUID belonging to the entity.") String locationUuid, @McpToolParam(
                    description = "JSON configuration using the documented fields; omitted update values retain readable existing data. Protected replacements require fresh explicit input.") String configuration) {
        return service.issueCertificateToLocation(entityUuid, locationUuid, configuration);
    }

}
