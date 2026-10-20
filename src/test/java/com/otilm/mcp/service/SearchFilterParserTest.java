package com.otilm.mcp.service;

import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.core.search.FilterConditionOperator;
import com.czertainly.api.model.core.search.FilterFieldSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchFilterParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "[]"})
    void shouldReturnEmptyListForNullBlankOrEmptyInput(String input) {
        List<SearchFilterRequestDto> result = SearchFilterParser.parseFilters(input, objectMapper);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldParseSingleFilter() {
        String json = """
                [{"fieldSource":"property","fieldIdentifier":"commonName","condition":"CONTAINS","value":"example.com"}]
                """;
        List<SearchFilterRequestDto> result = SearchFilterParser.parseFilters(json, objectMapper);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldSource()).isEqualTo(FilterFieldSource.PROPERTY);
        assertThat(result.get(0).getFieldIdentifier()).isEqualTo("commonName");
        assertThat(result.get(0).getCondition()).isEqualTo(FilterConditionOperator.CONTAINS);
        assertThat(result.get(0).getValue()).isEqualTo("example.com");
    }

    @Test
    void shouldParseMultipleFilters() {
        String json = """
                [
                    {"fieldSource":"property","fieldIdentifier":"state","condition":"EQUALS","value":"issued"},
                    {"fieldSource":"property","fieldIdentifier":"keySize","condition":"GREATER","value":2048}
                ]
                """;
        List<SearchFilterRequestDto> result = SearchFilterParser.parseFilters(json, objectMapper);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFieldIdentifier()).isEqualTo("state");
        assertThat(result.get(1).getFieldIdentifier()).isEqualTo("keySize");
    }

    @Test
    void shouldThrowOnMalformedJson() {
        assertThatThrownBy(() -> SearchFilterParser.parseFilters("not json", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filters JSON");
    }
}
