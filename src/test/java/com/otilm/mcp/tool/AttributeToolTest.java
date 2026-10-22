package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.service.AttributeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class AttributeToolTest {

    private AttributeTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        tool = new AttributeTool(new AttributeServiceImpl(IlmTestSupport.client(wmInfo)));
    }

    @Test
    void toolsDelegate() {
        stubFor(get(urlEqualTo("/v1/attributes/custom")).willReturn(okJson(fixture("custom-attributes"))));

        assertThat(tool.listCustomAttributes()).contains("Custom Attributes (3)");
        assertThat(tool.createCustomAttribute("Category", "Category", "words", "certificates", null))
                .startsWith("Unknown content type 'words'.");
    }

    @Test
    void creatingNeedsReadWrite() {
        assertThat(ToolNames.allowed(AccessLevel.READ_ONLY, new AttributeTool(null)))
                .containsExactly("list_custom_attributes");
        assertThat(ToolNames.allowed(AccessLevel.READ_WRITE, new AttributeTool(null)))
                .containsExactlyInAnyOrder("list_custom_attributes", "create_custom_attribute");
    }
}
