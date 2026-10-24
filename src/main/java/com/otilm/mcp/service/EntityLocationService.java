package com.otilm.mcp.service;

public interface EntityLocationService {
    String getEntity(String uuid);

    String getEntityAttributes(String connectorUuid, String kind, String values);

    String createEntity(String configuration);

    String updateEntity(String uuid, String configuration);

    String deleteEntity(String uuid);

    String searchLocations(String filters, Integer itemsPerPage, Integer pageNumber);

    String getLocationSearchableFields();

    String getLocation(String entityUuid, String locationUuid);

    String getLocationAttributes(String entityUuid, String values);

    String validateLocationAttributes(String entityUuid, String attributes);

    String createLocation(String entityUuid, String configuration);

    String updateLocation(String entityUuid, String locationUuid, String configuration);

    String deleteLocation(String entityUuid, String locationUuid);

    String enableLocation(String entityUuid, String locationUuid);

    String disableLocation(String entityUuid, String locationUuid);

    String getLocationPushAttributes(String entityUuid, String locationUuid, String values);

    String getLocationCsrAttributes(String entityUuid, String locationUuid, String values);

    String pushCertificateToLocation(String entityUuid, String locationUuid, String certificateUuid, String attributes);

    String removeCertificateFromLocation(String entityUuid, String locationUuid, String certificateUuid);

    String renewCertificateInLocation(String entityUuid, String locationUuid, String certificateUuid);

    String syncLocation(String entityUuid, String locationUuid);

    String issueCertificateToLocation(String entityUuid, String locationUuid, String configuration);
}
