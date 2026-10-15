package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@Slf4j
public class OAuthTokenClient {

    private static final long DEFAULT_TOKEN_LIFETIME_SECONDS = 3_600L;
    private static final long MINIMUM_TOKEN_LIFETIME_SECONDS = 60L;

    private final OkHttpClient httpClient;
    private final ConnectorProperties properties;
    private final ObjectMapper objectMapper;

    public OAuthTokenClient(@Qualifier("googleHealthHttpClient") OkHttpClient httpClient,
                            ConnectorProperties properties,
                            ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TokenResponse exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri) {
        FormBody body = new FormBody.Builder()
                .add("code", code)
                .add("client_id", properties.getClientId())
                .add("client_secret", properties.getClientSecret())
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .add("code_verifier", codeVerifier)
                .build();
        return execute(body);
    }

    public TokenResponse refresh(String refreshToken) {
        FormBody body = new FormBody.Builder()
                .add("client_id", properties.getClientId())
                .add("client_secret", properties.getClientSecret())
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build();
        return execute(body);
    }

    private TokenResponse execute(FormBody body) {
        Request request = new Request.Builder()
                .url(properties.getTokenUrl())
                .header("Accept", "application/json")
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String json = responseBody == null ? "{}" : responseBody.string();
            if (!response.isSuccessful()) {
                boolean permanent = response.code() == 400 || response.code() == 401;
                String providerErrorCode = providerErrorCode(json);
                log.warn("Google Health token request failed: status={}, reconnectRequired={}, providerErrorCode={}",
                        response.code(), permanent, providerErrorCode);
                throw new OAuthException(
                        permanent ? "Google authorization is no longer valid" : "Google token service is temporarily unavailable",
                        permanent,
                        providerErrorCode);
            }
            JsonNode node = objectMapper.readTree(json);
            String accessToken = text(node, "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new OAuthException("Google token response did not include an access token", false);
            }
            long expiresInSeconds = node.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME_SECONDS);
            return new TokenResponse(
                    accessToken,
                    text(node, "refresh_token"),
                    Duration.ofSeconds(Math.max(MINIMUM_TOKEN_LIFETIME_SECONDS, expiresInSeconds)),
                    positiveDuration(node, "refresh_token_expires_in"),
                    parseScopes(text(node, "scope"))
            );
        } catch (OAuthException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new OAuthException("Could not contact the Google token service", false, exception);
        }
    }

    private static Set<String> parseScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(Arrays.asList(value.trim().split("\\s+"))));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String providerErrorCode(String json) {
        try {
            return text(objectMapper.readTree(json), "error");
        } catch (RuntimeException exception) {
            log.warn("Could not parse the Google token error response; using a generic OAuth error: "
                     + "errorType={}, error={}",
                    exception.getClass().getSimpleName(), "invalid JSON");
            return null;
        }
    }

    private static Duration positiveDuration(JsonNode node, String field) {
        long seconds = node.path(field).asLong(0L);
        return seconds > 0L ? Duration.ofSeconds(seconds) : null;
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            Duration expiresIn,
            Duration refreshTokenExpiresIn,
            Set<String> scopes) {

        public TokenResponse(String accessToken, String refreshToken, Duration expiresIn, Set<String> scopes) {
            this(accessToken, refreshToken, expiresIn, null, scopes);
        }
    }
}
