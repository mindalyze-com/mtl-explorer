package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthStateStoreTest {

    @Test
    void successfulAuthorizationInvalidatesOtherConsumedAndPendingStates() {
        OAuthStateStore store = new OAuthStateStore();
        var completing = store.create(OAuthPaths.MANUAL_REDIRECT_URI);
        var earlier = store.create(OAuthPaths.MANUAL_REDIRECT_URI);
        var pending = store.create(OAuthPaths.MANUAL_REDIRECT_URI);
        store.consume(completing.state());
        store.consume(earlier.state());

        store.complete(completing, () -> {
        });

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.complete(earlier, () -> {
                }))
                .isInstanceOf(OAuthStateException.class);
        assertThat(store.consume(pending.state())).isEmpty();
        var next = store.create(OAuthPaths.MANUAL_REDIRECT_URI);
        assertThat(store.consume(next.state())).contains(next);
        store.complete(next, () -> {
        });
    }

    @Test
    void createsValidPkceAndConsumesStateOnlyOnce() throws Exception {
        OAuthStateStore store = new OAuthStateStore();

        OAuthStateStore.PendingAuthorization pending = store.create(OAuthPaths.MANUAL_REDIRECT_URI);
        String expectedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256")
                        .digest(pending.codeVerifier().getBytes(StandardCharsets.US_ASCII)));

        assertThat(pending.codeChallenge()).isEqualTo(expectedChallenge);
        assertThat(pending.redirectUri()).isEqualTo(OAuthPaths.MANUAL_REDIRECT_URI);
        assertThat(pending.expiresAt()).isAfter(java.time.Instant.now());
        assertThat(store.consume(pending.state())).contains(pending);
        assertThat(store.consume(pending.state())).isEmpty();
        assertThat(store.pendingCount()).isZero();
    }
}
