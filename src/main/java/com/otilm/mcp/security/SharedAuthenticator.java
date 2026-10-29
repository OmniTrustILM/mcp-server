package com.otilm.mcp.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;

/** Authenticates each HTTP request and exchanges once, without retaining user credentials. */
public final class SharedAuthenticator {
    private static final Logger logger = LoggerFactory.getLogger(SharedAuthenticator.class);
    private static final String EXCHANGE = "token exchange";
    private static final String ACTOR = "actor-token request";
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
        Map<String, Object> source = validate(token, properties.getResource(), false);
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
            form.add("actor_token", accessToken(post(ACTOR, actorForm), false));
            form.add("actor_token_type", ACCESS_TOKEN);
        }
        String exchanged = accessToken(post(EXCHANGE, form), true);
        if (token.equals(exchanged)) {
            throw warn(401, "The exchanged token equals the subject token");
        }
        Map<String, Object> downstream = validate(exchanged, properties.getApiAudience(), true);
        if (!source.get("sub").equals(downstream.get("sub"))) {
            throw warn(401, "The exchanged token is for another subject");
        }
        if (hasAudience(downstream.get("aud"), properties.getResource())) {
            throw warn(401, "The exchanged token still carries the MCP resource audience");
        }
        Object actor = downstream.get("act");
        if (standard
                ? downstream.containsKey("act") || downstream.containsKey(MAY_ACT)
                : !(actor instanceof Map<?, ?> signedActor)
                        || !properties.getClientId().equals(signedActor.get(CLIENT_ID))) {
            throw warn(401, "The exchanged token's actor claim does not fit the exchange mode");
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

    private Map<String, Object> validate(String token, String audience, boolean exchanged) {
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
        } catch (Exception failure) {
            // Never preserve the raw JWT/JWK exception or its cause (HTTP bodies can contain credentials). A failure of
            // the user's token is its sender's doing and stays silent, except a key set retrieval error, which the
            // sender cannot cause. A request that gave up waiting for another one's retrieval made no attempt and
            // stays silent too.
            if (failure.getCause() instanceof RemoteKeySourceException) {
                throw warn(401, "The key set could not be retrieved or read ({})", causes(failure));
            }
            if (exchanged && !(failure.getCause() instanceof KeySourceException)) {
                throw warn(401,
                        "The exchanged token failed validation of its signature, issuer, audience, times or claims");
            }
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

    private Map<?, ?> post(String request, MultiValueMap<String, String> form) {
        try {
            return tokenClient
                    .post()
                    .uri(properties.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((outgoing, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw warn(response.getStatusCode().is5xxServerError() ? 503 : 401,
                                    "The token endpoint answered HTTP {} to the {}", response.getStatusCode().value(),
                                    request);
                        }
                        return response.bodyTo(Map.class);
                    });
        } catch (AuthenticationFailure safe) {
            throw safe;
        } catch (Exception failure) {
            throw warn(503, "The token endpoint could not be reached or its answer could not be read for the {} ({})",
                    request, causes(failure));
        }
    }

    private static String accessToken(Map<?, ?> response, boolean exchanged) {
        if (response == null || !(response.get("token_type") instanceof String type) || !"Bearer".equalsIgnoreCase(type)
                || !(response.get("access_token") instanceof String token) || token.isBlank()
                || exchanged && !ACCESS_TOKEN.equals(response.get("issued_token_type"))) {
            throw warn(401, "The token endpoint's answer to the {} is not a Bearer access token of the expected type",
                    exchanged ? EXCHANGE : ACTOR);
        }
        return token;
    }

    /**
     * Logs a failure at WARN and returns it to throw. Only fixed text, statuses and class names may be passed:
     * exception messages and response bodies can hold credentials.
     */
    private static AuthenticationFailure warn(int status, String message, Object... arguments) {
        logger.warn(message, arguments);
        return new AuthenticationFailure(status);
    }

    /**
     * Names a failure and its causes by class, outermost first, because their messages can hold credentials. It adds
     * the status of an error response among them, because Spring names an exception for only some statuses.
     */
    private static String causes(Throwable failure) {
        List<String> names = new ArrayList<>();
        String status = "";
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            names.add(cause.getClass().getSimpleName());
            if (cause instanceof RestClientResponseException response) {
                status = ", HTTP " + response.getStatusCode().value();
            }
        }
        return String.join(" < ", names) + status;
    }
}
