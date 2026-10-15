package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthStateStore {

    static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int RANDOM_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();
    private long lifecycleGeneration;

    public PendingAuthorization create(String redirectUri) {
        return create(redirectUri, OAuthIntent.CONNECT);
    }

    public synchronized PendingAuthorization create(String redirectUri, OAuthIntent intent) {
        purgeExpired();
        String state = randomToken();
        String verifier = randomToken() + randomToken();
        PendingAuthorization authorization = new PendingAuthorization(
                state,
                verifier,
                base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII))),
                redirectUri,
                intent,
                Instant.now().plus(STATE_TTL),
                lifecycleGeneration
        );
        pending.put(state, authorization);
        return authorization;
    }

    public synchronized Optional<PendingAuthorization> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        PendingAuthorization authorization = pending.remove(state);
        if (authorization == null || !authorization.expiresAt().isAfter(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(authorization);
    }

    public synchronized void complete(PendingAuthorization authorization, Runnable persistAuthorization) {
        if (authorization.lifecycleGeneration() != lifecycleGeneration
            || !authorization.expiresAt().isAfter(Instant.now())) {
            throw new OAuthStateException();
        }
        persistAuthorization.run();
        invalidatePendingAuthorizations();
    }

    public synchronized void invalidateAllAndRun(Runnable action) {
        invalidatePendingAuthorizations();
        action.run();
    }

    private void invalidatePendingAuthorizations() {
        lifecycleGeneration++;
        pending.clear();
    }

    int pendingCount() {
        purgeExpired();
        return pending.size();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pending.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private String randomToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record PendingAuthorization(
            String state,
            String codeVerifier,
            String codeChallenge,
            String redirectUri,
            OAuthIntent intent,
            Instant expiresAt,
            long lifecycleGeneration) {
    }
}
