package com.otilm.mcp.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import com.otilm.mcp.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class UserToolTest {

    private UserTool tool;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        tool = new UserTool(new UserServiceImpl(IlmTestSupport.client(wmInfo)));
    }

    @Test
    void toolsDelegate() {
        stubFor(get(urlEqualTo("/v1/users")).willReturn(okJson(fixture("users"))));
        stubFor(get(urlEqualTo("/v1/roles")).willReturn(okJson(fixture("roles"))));

        assertThat(tool.listUsers()).contains("Users (3)");
        assertThat(tool.listRoles()).contains("Roles (3)");
    }

    @Test
    void bothToolsRead() {
        assertThat(ToolNames.allowed(AccessLevel.READ_ONLY, new UserTool(null)))
                .containsExactlyInAnyOrder("list_users", "list_roles");
    }
}
