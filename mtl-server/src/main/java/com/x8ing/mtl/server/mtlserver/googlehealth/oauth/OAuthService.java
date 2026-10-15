package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.GoogleHealthAccountMismatchException;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class OAuthService {

    private final ConnectorProperties properties;
    private final OAuthStateStore stateStore;
    private final OAuthTokenClient tokenClient;
    private final RefreshTokenCipher tokenCipher;
    private final MirrorRepository repository;
    private final GoogleHealthIdentityClient identityClient;

    public OAuthService(ConnectorProperties properties,
                        OAuthStateStore stateStore,
                        OAuthTokenClient tokenClient,
                        RefreshTokenCipher tokenCipher,
                        MirrorRepository repository,
                        GoogleHealthIdentityClient identityClient) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.tokenClient = tokenClient;
        this.tokenCipher = tokenCipher;
        this.repository = repository;
        this.identityClient = identityClient;
    }

    public String createAuthorizationUrl() {
        return createAuthorizationUrl(OAuthIntent.CONNECT);
    }

    public String createAuthorizationUrl(OAuthIntent intent) {
        requireConfigured();
        String redirectUri = properties.resolvedRedirectUri();
        OAuthStateStore.PendingAuthorization pendingAuthorization = stateStore.create(redirectUri, intent);
        log.info("Google Health OAuth authorization started: intent={}, requestedScopeCount={}, stateTtl={}",
                intent, OAuthScopes.REQUESTED.size(), OAuthStateStore.STATE_TTL);
        StringJoiner query = new StringJoiner("&");
        add(query, "client_id", properties.getClientId());
        add(query, "redirect_uri", redirectUri);
        add(query, "response_type", "code");
        add(query, "access_type", "offline");
        add(query, "include_granted_scopes", "true");
        add(query, "prompt", "consent");
        add(query, "scope", String.join(" ", OAuthScopes.REQUESTED_ORDERED));
        add(query, "state", pendingAuthorization.state());
        add(query, "code_challenge", pendingAuthorization.codeChallenge());
        add(query, "code_challenge_method", "S256");
        return properties.getAuthorizationUrl() + "?" + query;
    }

    public Set<String> completeAuthorization(String code, String state) {
        return completeAuthorizationWithOutcome(code, state).grantedScopes();
    }

    public CompletionOutcome completeAuthorizationWithOutcome(String code, String state) {
        requireConfigured();
        if (properties.oauthMode() != ConnectorProperties.OAuthMode.PROXY) {
            throw new OAuthStateException();
        }
        return completeAuthorization(code, state, properties.resolvedRedirectUri());
    }

    public OAuthCompletionResult completeManualAuthorization(String redirectUrl) {
        return completeManualAuthorizationWithOutcome(redirectUrl).result();
    }

    public CompletionOutcome completeManualAuthorizationWithOutcome(String redirectUrl) {
        requireConfigured();
        ManualAuthorizationResponse response = parseManualAuthorizationResponse(redirectUrl);
        if (response.error() != null && !response.error().isBlank()) {
            if (!cancelAuthorization(response.state(), OAuthPaths.MANUAL_REDIRECT_URI)) {
                throw new OAuthStateException();
            }
            OAuthCompletionResult result = "access_denied".equals(response.error())
                    ? OAuthCompletionResult.DENIED
                    : OAuthCompletionResult.ERROR;
            return new CompletionOutcome(result, Set.of(), false);
        }
        return completeAuthorization(response.code(), response.state(), OAuthPaths.MANUAL_REDIRECT_URI);
    }

    private CompletionOutcome completeAuthorization(String code, String state, String expectedRedirectUri) {
        requireConfigured();
        log.info("Google Health OAuth callback received; validating the single-use state and exchanging the code");
        OAuthStateStore.PendingAuthorization pendingAuthorization = consumeAuthorization(state, expectedRedirectUri);
        if (code == null || code.isBlank()) {
            throw new OAuthException("Google did not return an authorization code", false);
        }
        OAuthTokenClient.TokenResponse tokens =
                tokenClient.exchangeAuthorizationCode(
                        code, pendingAuthorization.codeVerifier(), pendingAuthorization.redirectUri());
        AccountConnection existingConnection = repository.findConnection().orElse(null);
        boolean freshRefreshTokenRequired = pendingAuthorization.intent() == OAuthIntent.REAUTHORIZE
                                            || existingConnection != null
                                               && existingConnection.state()
                                                  == AccountConnection.ConnectionState.RECONNECT_REQUIRED;
        String refreshToken = tokens.refreshToken();
        boolean reusedExistingRefreshToken = false;
        if (refreshToken == null || refreshToken.isBlank()) {
            if (freshRefreshTokenRequired) {
                throw new OAuthException(
                        "Google did not issue a new refresh token. Start reauthorization again and grant consent.",
                        true);
            }
            reusedExistingRefreshToken = true;
            refreshToken = Optional.ofNullable(existingConnection)
                    .map(AccountConnection::refreshTokenCiphertext)
                    .filter(value -> !value.isBlank())
                    .map(tokenCipher::decrypt)
                    .orElseThrow(() -> new OAuthException(
                            "Google did not issue an offline refresh token; reconnect and grant consent", true));
        }
        Set<String> grantedScopes = tokens.scopes();
        boolean fullReconciliationRequired = existingConnection != null
                                             && existingConnection.googleUserId() != null
                                             && !existingConnection.googleUserId().isBlank()
                                             && !existingConnection.grantedScopes().contains(OAuthScopes.LOCATION)
                                             && grantedScopes.contains(OAuthScopes.LOCATION);
        String healthUserId = identityClient.healthUserId(tokens.accessToken());
        Instant refreshTokenExpiresAt;
        if (tokens.refreshTokenExpiresIn() != null) {
            refreshTokenExpiresAt = Instant.now().plus(tokens.refreshTokenExpiresIn());
        } else if (reusedExistingRefreshToken && existingConnection != null) {
            refreshTokenExpiresAt = existingConnection.refreshTokenExpiresAt();
        } else {
            refreshTokenExpiresAt = null;
        }
        try {
            String encryptedRefreshToken = tokenCipher.encrypt(refreshToken);
            stateStore.complete(pendingAuthorization, () -> repository.saveConnected(
                    healthUserId, grantedScopes, encryptedRefreshToken, refreshTokenExpiresAt));
        } catch (GoogleHealthAccountMismatchException exception) {
            throw new OAuthException(
                    "This Google account differs from the existing Google Health mirror. "
                    + "Reconnect the original account or delete the local mirror first.",
                    false);
        }
        log.info("Google Health OAuth completed: grantedScopeCount={}, requestedScopeCount={}, "
                 + "activityAndFitness={}, healthMetricsAndMeasurements={}, sleep={}, location={}, "
                 + "refreshTokenReused={}",
                grantedScopes.size(), OAuthScopes.REQUESTED.size(),
                grantedScopes.contains(OAuthScopes.ACTIVITY_AND_FITNESS),
                grantedScopes.contains(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS),
                grantedScopes.contains(OAuthScopes.SLEEP),
                grantedScopes.contains(OAuthScopes.LOCATION),
                reusedExistingRefreshToken);
        return new CompletionOutcome(
                OAuthCompletionResult.CONNECTED,
                grantedScopes,
                fullReconciliationRequired);
    }

    public void runWithAuthorizationInvalidated(Runnable action) {
        stateStore.invalidateAllAndRun(action);
    }

    public boolean cancelAuthorization(String state) {
        if (!properties.isConfigured() || properties.oauthMode() != ConnectorProperties.OAuthMode.PROXY) {
            return false;
        }
        return cancelAuthorization(state, properties.resolvedRedirectUri());
    }

    private boolean cancelAuthorization(String state, String expectedRedirectUri) {
        return stateStore.consume(state)
                .filter(pending -> expectedRedirectUri.equals(pending.redirectUri()))
                .isPresent();
    }

    private OAuthStateStore.PendingAuthorization consumeAuthorization(String state, String expectedRedirectUri) {
        OAuthStateStore.PendingAuthorization pendingAuthorization = stateStore.consume(state)
                .orElseThrow(OAuthStateException::new);
        if (expectedRedirectUri != null && !expectedRedirectUri.equals(pendingAuthorization.redirectUri())) {
            throw new OAuthStateException();
        }
        return pendingAuthorization;
    }

    private static ManualAuthorizationResponse parseManualAuthorizationResponse(String redirectUrl) {
        if (redirectUrl == null || redirectUrl.isBlank()) {
            throw new OAuthException("Paste the complete address from the browser", false);
        }
        try {
            URI uri = new URI(redirectUrl.trim());
            String path = uri.getRawPath();
            String expectedPath = URI.create(OAuthPaths.MANUAL_REDIRECT_URI).getRawPath();
            if (!"http".equalsIgnoreCase(uri.getScheme())
                || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort() != 1
                || !expectedPath.equals(path)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
                throw new OAuthException("Paste the complete 127.0.0.1 address returned by Google", false);
            }
            Map<String, String> parameters = parseQuery(uri.getRawQuery());
            String code = parameters.get("code");
            String state = parameters.get("state");
            String error = parameters.get("error");
            if (state == null || state.isBlank()) {
                throw new OAuthException("The pasted browser address has no authorization state", false);
            }
            boolean hasCode = code != null && !code.isBlank();
            boolean hasError = error != null && !error.isBlank();
            if (hasCode == hasError) {
                throw new OAuthException("The pasted browser address has no valid Google authorization result", false);
            }
            return new ManualAuthorizationResponse(code, state, error);
        } catch (URISyntaxException exception) {
            throw new OAuthException("The pasted browser address is not a valid URL", false);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new OAuthException("The pasted browser address has no Google authorization result", false);
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String name = decode(parts[0]);
            String value = parts.length == 2 ? decode(parts[1]) : "";
            if (parameters.putIfAbsent(name, value) != null) {
                throw new OAuthException("The pasted browser address contains duplicate parameters", false);
            }
        }
        return parameters;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Google Health is not fully configured");
        }
    }

    private static void add(StringJoiner query, String name, String value) {
        query.add(encode(name) + "=" + encode(value));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private record ManualAuthorizationResponse(String code, String state, String error) {
    }

    public record CompletionOutcome(
            OAuthCompletionResult result,
            Set<String> grantedScopes,
            boolean fullReconciliationRequired) {
    }
}
