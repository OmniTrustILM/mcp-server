package com.otilm.mcp.security;

import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

/** Authenticates each HTTP request and exchanges once, without retaining user credentials. */
public final class SharedAuthenticator {
    private static final String MAY_ACT = "may_act";
    private static final String SCOPE = "scope";
    private static final String CLIENT_ID = "client_id";
    private static final String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    private final SharedSecurityProperties properties;
    private final NimbusJwtDecoder decoder;
    private final RestClient tokenClient;

    public SharedAuthenticator(SharedSecurityProperties properties, RestOperations jwkClient, RestClient tokenClient) {
        this.properties = properties;
        this.tokenClient = tokenClient;
        this.decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.getJwkSetUri())
                .restOperations(jwkClient)
                .validateType(false)
                .jwsAlgorithms(algorithms -> {
                    algorithms.clear();
                    properties.getJwsAlgorithms().forEach(name -> algorithms.add(SignatureAlgorithm.from(name)));
                })
                .build();
        // Required claims, issuer, audience, timestamps and skew are checked below on their original JSON types.
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    public RequestCredential authenticate(String token) {
        Map<String, Object> source = validate(token, properties.getResource());
        boolean standard = properties.getExchangeMode() == SharedSecurityProperties.ExchangeMode.STANDARD;
        authorizeSource(source, standard);
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.add("subject_token", token);
        form.add("subject_token_type", ACCESS_TOKEN);
        form.add("requested_token_type", ACCESS_TOKEN);
        form.add("audience", properties.getApiAudience());
        if (properties.getExchangeScope() != null && !properties.getExchangeScope().isBlank()) {
            form.add(SCOPE, properties.getExchangeScope());
        }
        if (!standard) {
            MultiValueMap<String, String> actorForm = clientForm();
            actorForm.add("grant_type", "client_credentials");
            form.add("actor_token", accessToken(post(actorForm), false));
            form.add("actor_token_type", ACCESS_TOKEN);
        }
        String exchanged = accessToken(post(form), true);
        if (token.equals(exchanged)) {
            throw new AuthenticationFailure(401);
        }
        Map<String, Object> downstream = validate(exchanged, properties.getApiAudience());
        if (!source.get("sub").equals(downstream.get("sub"))
                || hasAudience(downstream.get("aud"), properties.getResource())) {
            throw new AuthenticationFailure(401);
        }
        Object actor = downstream.get("act");
        if (standard
                ? downstream.containsKey("act") || downstream.containsKey(MAY_ACT)
                : !(actor instanceof Map<?, ?> signedActor)
                        || !properties.getClientId().equals(signedActor.get(CLIENT_ID))) {
            throw new AuthenticationFailure(401);
        }
        return new RequestCredential(exchanged);
    }

    private void authorizeSource(Map<String, Object> source, boolean standard) {
        if (hasAudience(source.get("aud"), properties.getApiAudience())) {
            throw new AuthenticationFailure(401);
        }
        if (standard && (source.containsKey("act") || source.containsKey(MAY_ACT))) {
            throw new AuthenticationFailure(401);
        }
        Object scope = source.get(SCOPE);
        if (scope != null && !(scope instanceof String)) {
            throw new AuthenticationFailure(401);
        }
        if (!(scope instanceof String scopes)
                || !Arrays.asList(scopes.split(" ")).contains(properties.getRequiredScope())) {
            throw new AuthenticationFailure(403);
        }
    }

    private Map<String, Object> validate(String token, String audience) {
        try {
            Map<String, Object> claims = SignedJWT.parse(token).getPayload().toJSONObject();
            if (claims == null || !properties.getIssuer().equals(claims.get("iss"))
                    || !(claims.get("sub") instanceof String subject) || subject.isBlank()
                    || !hasAudience(claims.get("aud"), audience)) {
                throw new AuthenticationFailure(401);
            }
            Instant now = Instant.now();
            Instant expiry = timestamp(claims.get("exp"));
            if (!expiry.plus(properties.getClockSkew()).isAfter(now) || claims.containsKey("nbf")
                    && timestamp(claims.get("nbf")).minus(properties.getClockSkew()).isAfter(now)) {
                throw new AuthenticationFailure(401);
            }
            validateClaimTypes(claims);
            decoder.decode(token);
            return claims;
        } catch (Exception ignored) {
            // Never preserve the raw JWT/JWK exception or its cause (HTTP bodies can contain credentials).
            throw new AuthenticationFailure(401);
        }
    }

    private static void validateClaimTypes(Map<String, Object> claims) {
        if (claims.containsKey(SCOPE) && !(claims.get(SCOPE) instanceof String)) {
            throw new AuthenticationFailure(401);
        }
        for (String name : List.of("act", MAY_ACT)) {
            if (claims.containsKey(name)) {
                validateActor(claims.get(name));
            }
        }
    }

    private static void validateActor(Object value) {
        if (!(value instanceof Map<?, ?> actor)) {
            throw new AuthenticationFailure(401);
        }
        for (String identity : List.of(CLIENT_ID, "sub")) {
            if (actor.containsKey(identity) && (!(actor.get(identity) instanceof String member) || member.isBlank())) {
                throw new AuthenticationFailure(401);
            }
        }
    }

    private static boolean hasAudience(Object value, String expected) {
        if (value instanceof String audience) {
            return expected.equals(audience);
        }
        return value instanceof List<?> audiences && audiences.stream().allMatch(String.class::isInstance)
                && audiences.contains(expected);
    }

    private static Instant timestamp(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new AuthenticationFailure(401);
        }
        return Instant.ofEpochSecond(number.longValue());
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(CLIENT_ID, properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        return form;
    }

    private Map<?, ?> post(MultiValueMap<String, String> form) {
        try {
            return tokenClient
                    .post()
                    .uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new AuthenticationFailure(response.getStatusCode().is5xxServerError() ? 503 : 401);
                        }
                        return response.bodyTo(Map.class);
                    });
        } catch (AuthenticationFailure safe) {
            throw safe;
        } catch (Exception ignored) {
            throw new AuthenticationFailure(503);
        }
    }

    private static String accessToken(Map<?, ?> response, boolean exchanged) {
        if (response == null || !(response.get("token_type") instanceof String type) || !"Bearer".equalsIgnoreCase(type)
                || !(response.get("access_token") instanceof String token) || token.isBlank()
                || exchanged && !ACCESS_TOKEN.equals(response.get("issued_token_type"))) {
            throw new AuthenticationFailure(401);
        }
        return token;
    }
}
