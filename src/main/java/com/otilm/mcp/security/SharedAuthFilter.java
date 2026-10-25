package com.otilm.mcp.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/** Default protection covers every servlet route and dispatch, with explicit local public exceptions. */
public final class SharedAuthFilter implements Filter {
    private static final Set<String> PUBLIC_HEALTH = Set
            .of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness");
    private static final Set<String> CORS_HEADERS = Set
            .of("authorization", "content-type", "accept", "mcp-protocol-version");
    private final SharedSecurityProperties properties;
    private final SharedAuthenticator authenticator;
    private final String metadataUrl;
    private final String metadata;
    private final String authorizationScope;

    public SharedAuthFilter(SharedSecurityProperties properties, SharedAuthenticator authenticator) {
        this.properties = properties;
        this.authenticator = authenticator;
        metadataUrl = URI
                .create(properties.getResource())
                .resolve("/.well-known/oauth-protected-resource/mcp")
                .toString();
        Set<String> scopes = new java.util.LinkedHashSet<>();
        scopes.add(properties.getRequiredScope());
        scopes.addAll(properties.getAuthorizationScopes());
        authorizationScope = String.join(" ", scopes);
        metadata = JsonMapper
                .builder()
                .build()
                .writeValueAsString(Map
                        .of("resource", properties.getResource(), "authorization_servers",
                                List.of(properties.getIssuer()), "scopes_supported", List.copyOf(scopes),
                                "bearer_methods_supported", List.of("header")));
    }

    @Override
    public void doFilter(ServletRequest incoming, ServletResponse outgoing, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) incoming;
        HttpServletResponse response = (HttpServletResponse) outgoing;
        response.setHeader("Cache-Control", "no-store");
        try {
            if (cors(request, response)) {
                return;
            }
            if (publicRead(request, response, chain)) {
                return;
            }
            // An internal redispatch may reuse this request's authenticated credential, never a session or user cache.
            if (request.getAttribute(RequestCredentials.KEY) instanceof RequestCredential) {
                chain.doFilter(request, response);
                return;
            }
            String token = bearer(request);
            RequestCredential credential = authenticator.authenticate(token);
            request.setAttribute(RequestCredentials.KEY, credential);
            try {
                chain.doFilter(request, response);
            } finally {
                request.removeAttribute(RequestCredentials.KEY);
            }
        } catch (AuthenticationFailure failure) {
            response.setStatus(failure.status());
            if (failure.status() == 401 || failure.status() == 403) {
                response
                        .setHeader("WWW-Authenticate",
                                "Bearer resource_metadata=\"" + metadataUrl + "\", error=\""
                                        + (failure.status() == 403 ? "insufficient_scope" : "invalid_token")
                                        + "\", scope=\"" + authorizationScope + "\"");
            }
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + failure.getMessage() + "\"}");
        }
    }

    private boolean publicRead(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
        if (read && (path.equals("/.well-known/oauth-protected-resource/mcp")
                || path.equals("/.well-known/oauth-protected-resource"))) {
            response.setContentType("application/json");
            if (!"HEAD".equals(request.getMethod())) {
                response.getWriter().write(metadata);
            }
            return true;
        }
        if (read && PUBLIC_HEALTH.contains(path)) {
            chain.doFilter(request, response);
            return true;
        }
        return false;
    }

    private boolean cors(HttpServletRequest request, HttpServletResponse response) {
        List<String> origins = Collections.list(request.getHeaders("Origin"));
        if (origins.isEmpty()) {
            return false;
        }
        if (origins.size() != 1 || !properties.getAllowedOrigins().contains(origins.getFirst())) {
            throw new AuthenticationFailure(403);
        }
        response.setHeader("Access-Control-Allow-Origin", origins.getFirst());
        response.addHeader("Vary", "Origin");
        response.setHeader("Access-Control-Expose-Headers", "WWW-Authenticate");
        if (!"OPTIONS".equals(request.getMethod()) || request.getHeader("Access-Control-Request-Method") == null) {
            return false;
        }
        if (!Set.of("GET", "POST").contains(request.getHeader("Access-Control-Request-Method"))) {
            throw new AuthenticationFailure(403);
        }
        String headers = request.getHeader("Access-Control-Request-Headers");
        if (headers != null && Arrays
                .stream(headers.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(header -> !CORS_HEADERS.contains(header))) {
            throw new AuthenticationFailure(403);
        }
        response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        response.setHeader("Access-Control-Allow-Headers", String.join(", ", CORS_HEADERS));
        response.setStatus(204);
        return true;
    }

    private static String bearer(HttpServletRequest request) {
        if (request
                .getParameterMap()
                .keySet()
                .stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(Set.of("access_token", "token", "authorization")::contains)) {
            throw new AuthenticationFailure(401);
        }
        List<String> headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1) {
            throw new AuthenticationFailure(401);
        }
        String header = headers.getFirst();
        if (header.length() > 16384 || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || !header.substring(7).matches("[A-Za-z0-9._~+/-]+=*")) {
            throw new AuthenticationFailure(401);
        }
        return header.substring(7);
    }
}
