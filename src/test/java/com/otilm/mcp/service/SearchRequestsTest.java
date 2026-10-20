package com.otilm.mcp.service;

import com.otilm.api.model.client.certificate.CertificateSearchRequestDto;
import com.otilm.api.model.client.certificate.SearchRequestDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRequestsTest {

    @Test
    void appliesPagingAndParsesFilters() {
        SearchRequestDto request = SearchRequests
                .of(new SearchRequestDto(),
                        """
                                [{"fieldSource":"property","fieldIdentifier":"CRYPTO_ASSET_PQC_VERDICT","condition":"EQUALS","value":"notReady"}]
                                """,
                        25, 3);

        assertThat(request.getItemsPerPage()).isEqualTo(25);
        assertThat(request.getPageNumber()).isEqualTo(3);
        assertThat(request.getFilters())
                .singleElement()
                .satisfies(filter -> assertThat(filter.getFieldIdentifier()).isEqualTo("CRYPTO_ASSET_PQC_VERDICT"));
    }

    @Test
    void keepsRequestDefaultsWhenPagingIsMissing() {
        SearchRequestDto defaults = new SearchRequestDto();

        SearchRequestDto request = SearchRequests.of(new CertificateSearchRequestDto(), null, null, null);

        assertThat(request).isInstanceOf(CertificateSearchRequestDto.class);
        assertThat(request.getItemsPerPage()).isEqualTo(defaults.getItemsPerPage());
        assertThat(request.getPageNumber()).isEqualTo(defaults.getPageNumber());
        assertThat(request.getFilters()).isEmpty();
    }
}
