package com.otilm.mcp.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.otilm.mcp.IlmTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.otilm.mcp.IlmTestSupport.fixture;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class UserServiceTest {

    private UserService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        service = new UserServiceImpl(IlmTestSupport.client(wmInfo));
    }

    @Test
    void listsUsers() {
        stubFor(get(urlEqualTo("/v1/users")).willReturn(okJson(fixture("users"))));

        assertThat(service.listUsers())
                .contains("Users (3)", "- username-59 [", "Name: firstName-60 lastName-61", "Email: email-66",
                        "Enabled: true")
                .doesNotContain("System User");
    }

    @Test
    void listsRoles() {
        stubFor(get(urlEqualTo("/v1/roles")).willReturn(okJson(fixture("roles"))));

        assertThat(service.listRoles())
                .contains("Roles (3)", "- name-72 [", "Description: description-73", "System Role: true", "name-77");
    }
}
