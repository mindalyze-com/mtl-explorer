package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.Instant;
import java.util.Set;

public record AccountConnection(
        String googleUserId,
        Set<String> grantedScopes,
        String refreshTokenCiphertext,
        ConnectionState state,
        String lastError,
        Instant connectedAt,
        Instant refreshTokenExpiresAt,
        Instant disconnectedAt,
        Instant updatedAt,
        long authorizationGeneration
) {
    public AccountConnection(
            String googleUserId,
            Set<String> grantedScopes,
            String refreshTokenCiphertext,
            ConnectionState state,
            String lastError,
            Instant connectedAt,
            Instant refreshTokenExpiresAt,
            Instant disconnectedAt,
            Instant updatedAt) {
        this(googleUserId, grantedScopes, refreshTokenCiphertext, state, lastError,
                connectedAt, refreshTokenExpiresAt, disconnectedAt, updatedAt, 0L);
    }

    public AccountConnection(
            String googleUserId,
            Set<String> grantedScopes,
            String refreshTokenCiphertext,
            ConnectionState state,
            String lastError,
            Instant connectedAt,
            Instant disconnectedAt,
            Instant updatedAt) {
        this(googleUserId, grantedScopes, refreshTokenCiphertext, state, lastError,
                connectedAt, null, disconnectedAt, updatedAt);
    }

    public enum ConnectionState {
        DISCONNECTED,
        CONNECTED,
        RECONNECT_REQUIRED
    }
}
