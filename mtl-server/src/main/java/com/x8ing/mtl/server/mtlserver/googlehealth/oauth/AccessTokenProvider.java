package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class AccessTokenProvider {

    private static final long EXPIRY_SKEW_SECONDS = 60L;
    private static final Duration TESTING_EXPIRY_CLASSIFICATION_WINDOW = Duration.ofHours(12);
    private static final String INVALID_GRANT = "invalid_grant";
    private static final String LIKELY_TESTING_EXPIRY_MESSAGE =
            "Google authorization likely expired because the OAuth project is in Testing";
    private static final String UNREADABLE_REFRESH_TOKEN_MESSAGE =
            "Google Health must be reconnected because the saved authorization cannot be read";

    private final MirrorRepository repository;
    private final RefreshTokenCipher tokenCipher;
    private final OAuthTokenClient tokenClient;
    private final Object refreshLock = new Object();
    private volatile CachedAccessToken cached;

    public AccessTokenProvider(MirrorRepository repository,
                               RefreshTokenCipher tokenCipher,
                               OAuthTokenClient tokenClient) {
        this.repository = repository;
        this.tokenCipher = tokenCipher;
        this.tokenClient = tokenClient;
    }

    public String accessToken() {
        synchronized (refreshLock) {
            while (true) {
                AccountConnection connection = repository.findConnection()
                        .filter(value -> value.state() == AccountConnection.ConnectionState.CONNECTED)
                        .orElseThrow(() -> new OAuthException("Google Health is not connected", true));
                CachedAccessToken current = cached;
                if (current != null && current.isUsable(connection.authorizationGeneration())) {
                    return current.value();
                }
                cached = null;
                if (connection.refreshTokenCiphertext() == null || connection.refreshTokenCiphertext().isBlank()) {
                    throw new OAuthException("Google Health must be reconnected", true);
                }
                try {
                    log.info("Refreshing the Google Health access token");
                    OAuthTokenClient.TokenResponse response = tokenClient.refresh(
                            decryptRefreshToken(connection.refreshTokenCiphertext()));
                    Instant refreshTokenExpiresAt = response.refreshTokenExpiresIn() == null
                            ? null
                            : Instant.now().plus(response.refreshTokenExpiresIn());
                    if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
                        if (!repository.updateRefreshToken(connection.authorizationGeneration(),
                                tokenCipher.encrypt(response.refreshToken()), refreshTokenExpiresAt)) {
                            log.info("Discarded a rotated Google Health token from a replaced authorization");
                            continue;
                        }
                        log.info("Persisted a rotated Google Health refresh token");
                    } else if (refreshTokenExpiresAt != null
                               && !repository.updateRefreshTokenExpiry(
                            connection.authorizationGeneration(), refreshTokenExpiresAt)) {
                        log.info("Discarded Google Health token expiry from a replaced authorization");
                        continue;
                    }
                    if (!isCurrentAuthorization(connection)) {
                        log.info("Discarded a Google Health access token from a replaced authorization");
                        continue;
                    }
                    CachedAccessToken refreshed = new CachedAccessToken(
                            response.accessToken(),
                            Instant.now().plus(response.expiresIn()).minusSeconds(EXPIRY_SKEW_SECONDS),
                            connection.authorizationGeneration());
                    cached = refreshed;
                    log.info("Google Health access token refreshed: expiresIn={}", response.expiresIn());
                    return refreshed.value();
                } catch (OAuthException exception) {
                    log.warn("Google Health access-token refresh failed: reconnectRequired={}",
                            exception.isReconnectRequired(), exception);
                    if (!isCurrentAuthorization(connection)) {
                        log.info("Ignored the refresh failure from a replaced Google Health authorization");
                        continue;
                    }
                    if (exception.isReconnectRequired()) {
                        String safeError = likelyTestingExpiry(connection, exception)
                                ? LIKELY_TESTING_EXPIRY_MESSAGE
                                : exception.getMessage();
                        if (!repository.markReconnectRequired(connection.authorizationGeneration(), safeError)
                            && !isCurrentAuthorization(connection)) {
                            log.info("Ignored a reconnect request for a replaced Google Health authorization");
                            continue;
                        }
                    }
                    throw exception;
                }
            }
        }
    }

    private boolean isCurrentAuthorization(AccountConnection expected) {
        return repository.findConnection()
                .filter(connection -> connection.state() == AccountConnection.ConnectionState.CONNECTED)
                .map(connection -> connection.authorizationGeneration() == expected.authorizationGeneration())
                .orElse(false);
    }

    public void invalidate() {
        cached = null;
    }

    private String decryptRefreshToken(String ciphertext) {
        try {
            return tokenCipher.decrypt(ciphertext);
        } catch (IllegalStateException exception) {
            throw new OAuthException(UNREADABLE_REFRESH_TOKEN_MESSAGE, true, exception);
        }
    }

    private static boolean likelyTestingExpiry(AccountConnection connection, OAuthException exception) {
        Instant expiresAt = connection.refreshTokenExpiresAt();
        return INVALID_GRANT.equals(exception.getProviderErrorCode())
               && expiresAt != null
               && !Instant.now().isBefore(expiresAt.minus(TESTING_EXPIRY_CLASSIFICATION_WINDOW));
    }

    private record CachedAccessToken(String value, Instant expiresAt, long authorizationGeneration) {
        boolean isUsable(long expectedGeneration) {
            return authorizationGeneration == expectedGeneration && expiresAt.isAfter(Instant.now());
        }
    }
}
