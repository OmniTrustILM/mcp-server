package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.SearchRequestDto;

public final class SearchRequests {

    private SearchRequests() {
    }

    public static <T extends SearchRequestDto> T of(T request, String filters, Integer itemsPerPage,
            Integer pageNumber) {
        if (itemsPerPage != null) {
            request.setItemsPerPage(itemsPerPage);
        }
        if (pageNumber != null) {
            request.setPageNumber(pageNumber);
        }
        request.setFilters(SearchFilterParser.parseFilters(filters));
        return request;
    }
}
