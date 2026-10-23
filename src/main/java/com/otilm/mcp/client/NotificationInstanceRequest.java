package com.otilm.mcp.client;

import com.otilm.api.model.core.notification.AttributeMappingDto;
import java.util.List;

/**
 * A new notification instance as ILM takes it, with its fields as attribute content in ILM's version 2 form.
 */
public record NotificationInstanceRequest(String name, String description, String connectorUuid, String kind,
        List<Field> attributes, List<AttributeMappingDto> attributeMappings) {

    public record Field(String uuid, String name, String contentType, List<Object> content) {
    }
}
