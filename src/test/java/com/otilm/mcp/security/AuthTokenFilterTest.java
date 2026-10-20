package com.otilm.mcp.security;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenFilterTest {
    private final AuthTokenFilter filter = new AuthTokenFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        AuthTokenHolder.clear();
    }

    @Test
    void shouldExtractBearerToken() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer my-test-token");
        filter
                .doFilterInternal(request, response,
                        (req, res) -> assertThat(AuthTokenHolder.getToken()).isEqualTo("my-test-token"));
    }

    @Test
    void shouldClearTokenAfterFilterChain() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer my-test-token");
        filter.doFilterInternal(request, response, (req, res) -> {
        });
        assertThat(AuthTokenHolder.getToken()).isNull();
    }

    @Test
    void shouldNotSetTokenWhenNoAuthHeader() throws ServletException, IOException {
        filter.doFilterInternal(request, response, (req, res) -> assertThat(AuthTokenHolder.getToken()).isNull());
    }

    @Test
    void shouldNotSetTokenForNonBearerAuth() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        filter.doFilterInternal(request, response, (req, res) -> assertThat(AuthTokenHolder.getToken()).isNull());
    }
}
