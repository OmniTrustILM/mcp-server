package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchFilterRequestDto;
import java.util.Collections;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class SearchFilterParser {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private SearchFilterParser() {
    }

    public static List<SearchFilterRequestDto> parseFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<SearchFilterRequestDto> filters = JSON_MAPPER.readValue(filtersJson, new TypeReference<>() {
            });
            return filters == null ? Collections.emptyList() : filters;
        } catch (JacksonException e) {
            throw new IllegalArgumentException(
                    "Invalid filters JSON. Expected a JSON array of filter objects with fields: "
                            + "fieldSource (property, meta, custom, data), fieldIdentifier, "
                            + "condition (EQUALS, CONTAINS, GREATER, etc.), value. "
                            + "Example: [{\"fieldSource\":\"property\",\"fieldIdentifier\":\"commonName\","
                            + "\"condition\":\"CONTAINS\",\"value\":\"example.com\"}]. Error: "
                            + e.getOriginalMessage(),
                    e);
        }
    }
}
