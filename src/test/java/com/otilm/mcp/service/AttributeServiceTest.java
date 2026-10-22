package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AttributeServiceTest {

    private AttributeService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new AttributeServiceImpl(IlmTestSupport.client(wmInfo));
    }

    @Test
    void listsCustomAttributesWithTheirFieldIdentifier() {
        stubFor(get(urlEqualTo("/v1/attributes/custom")).willReturn(okJson(fixture("custom-attributes"))));

        assertThat(service.listCustomAttributes())
                .contains("Custom Attributes (3)", "- name-51 [", "Label: label-52", "Content Type: time",
                        "Resources: certificates", "Field Identifier: name-51|TIME", "name-57");
    }

    @Test
    void createsACustomAttribute() {
        stubFor(post(urlEqualTo("/v1/attributes/custom")).willReturn(okJson("""
                {"uuid":"00000000-0000-4000-8000-000000000200","name":"Category","label":"Category",
                 "contentType":"string","resources":["certificates"],"enabled":true}
                """)));

        String result = service.createCustomAttribute("Category", "Category", "STRING", "certificates", null);

        verify(postRequestedFor(urlEqualTo("/v1/attributes/custom"))
                .withRequestBody(equalToJson(
                        """
                                {"name":"Category","label":"Category","contentType":"string","resources":["certificates"],"visible":true}
                                """,
                        false, true)));
        assertThat(result)
                .isEqualTo("Created custom attribute Category [00000000-0000-4000-8000-000000000200] (string)"
                        + " for certificates. Refer to it as Category|STRING in conditions and setFields.");
    }

    @Test
    void rejectsUnknownContentTypesAndResources() {
        assertThat(service.createCustomAttribute("Category", "Category", "words", "certificates", null))
                .startsWith("Unknown content type 'words'. Valid content types: string, text, ");
        assertThat(service.createCustomAttribute("Category", "Category", "string", "certs", null))
                .isEqualTo("Unknown resource 'certs'.");
        assertThat(service.createCustomAttribute("Category", "Category", "string", " ", null))
                .isEqualTo("Unknown resource ''.");
        assertThat(service.createCustomAttribute(" ", "Category", "string", "certificates", null))
                .isEqualTo("A custom attribute needs a name and a label.");
    }
}
