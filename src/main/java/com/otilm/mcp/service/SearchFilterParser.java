package com.otilm.mcp.service;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

public final class SearchFilterParser {

    private SearchFilterParser() {
    }

    public static List<SearchFilterRequestDto> parseFilters(String filtersJson, ObjectMapper objectMapper) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(filtersJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Invalid filters JSON. Expected a JSON array of filter objects with fields: "
                            + "fieldSource (property, meta, custom, data), fieldIdentifier, "
                            + "condition (EQUALS, CONTAINS, GREATER, etc.), value. "
                            + "Example: [{\"fieldSource\":\"property\",\"fieldIdentifier\":\"commonName\","
                            + "\"condition\":\"CONTAINS\",\"value\":\"example.com\"}]. Error: " + e.getMessage(),
                    e);
        }
    }
}
