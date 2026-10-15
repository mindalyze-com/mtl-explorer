package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccessTokenProviderTest {

    @Test
    void keepsAccessTokenOnlyInMemoryAndRefreshesAfterInvalidation() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(OAuthScopes.SLEEP), "encrypted-refresh",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        when(cipher.decrypt("encrypted-refresh")).thenReturn("refresh");
        when(tokenClient.refresh("refresh"))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access-1", null, Duration.ofHours(1), Set.of()))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access-2", null, Duration.ofHours(1), Set.of()));
        AccessTokenProvider provider = new AccessTokenProvider(
                repository, cipher, tokenClient);

        assertThat(provider.accessToken()).isEqualTo("access-1");
        assertThat(provider.accessToken()).isEqualTo("access-1");
        provider.invalidate();
        assertThat(provider.accessToken()).isEqualTo("access-2");
        verify(tokenClient, times(2)).refresh("refresh");
    }

    @Test
    void permanentRefreshFailureMovesTheConnectionToReconnectRequired() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(OAuthScopes.SLEEP), "encrypted-refresh",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        when(cipher.decrypt("encrypted-refresh")).thenReturn("refresh");
        when(tokenClient.refresh("refresh"))
                .thenThrow(new OAuthException("Google authorization is no longer valid", true));
        AccessTokenProvider provider = new AccessTokenProvider(
                repository, cipher, tokenClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(provider::accessToken)
                .isInstanceOf(OAuthException.class);
        verify(repository).markReconnectRequired(0L, "Google authorization is no longer valid");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unreadableRefreshTokenMovesTheConnectionToReconnectRequired(CapturedOutput output) {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(OAuthScopes.SLEEP), "encrypted-with-old-key",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        when(cipher.decrypt("encrypted-with-old-key"))
                .thenThrow(new IllegalStateException("Could not decrypt the Google Health refresh token"));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(provider::accessToken)
                .isInstanceOf(OAuthException.class)
                .hasMessage("Google Health must be reconnected because the saved authorization cannot be read")
                .hasCauseInstanceOf(IllegalStateException.class);
        verify(repository).markReconnectRequired(0L,
                "Google Health must be reconnected because the saved authorization cannot be read");
        verify(tokenClient, never()).refresh(org.mockito.ArgumentMatchers.anyString());
        assertThat(output).contains("Google Health access-token refresh failed: reconnectRequired=true")
                .contains("IllegalStateException: Could not decrypt the Google Health refresh token")
                .doesNotContain("encrypted-with-old-key");
    }

    @Test
    void concurrentCallersShareOneRefresh() throws Exception {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(OAuthScopes.SLEEP), "encrypted-refresh",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        when(cipher.decrypt("encrypted-refresh")).thenReturn("refresh");
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        when(tokenClient.refresh("refresh")).thenAnswer(invocation -> {
            refreshStarted.countDown();
            assertThat(releaseRefresh.await(1, TimeUnit.SECONDS)).isTrue();
            return new OAuthTokenClient.TokenResponse(
                    "access", null, Duration.ofHours(1), Set.of());
        });
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = callers.submit(provider::accessToken);
            assertThat(refreshStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<String> second = callers.submit(provider::accessToken);
            releaseRefresh.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("access");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("access");
            verify(tokenClient).refresh("refresh");
        } finally {
            releaseRefresh.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void persistsARotatedRefreshTokenWhileHoldingTheRefreshLock() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "health-user-1", Set.of(OAuthScopes.SLEEP), "encrypted-old",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        when(cipher.decrypt("encrypted-old")).thenReturn("old-refresh");
        when(cipher.encrypt("rotated-refresh")).thenReturn("encrypted-rotated");
        when(repository.updateRefreshToken(org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq("encrypted-rotated"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        when(tokenClient.refresh("old-refresh")).thenReturn(new OAuthTokenClient.TokenResponse(
                "access", "rotated-refresh", Duration.ofHours(1), Duration.ofDays(7), Set.of()));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);

        Instant before = Instant.now().plus(Duration.ofDays(7)).minusSeconds(1);
        assertThat(provider.accessToken()).isEqualTo("access");
        Instant after = Instant.now().plus(Duration.ofDays(7)).plusSeconds(1);

        org.mockito.ArgumentCaptor<Instant> expiry = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).updateRefreshToken(
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.eq("encrypted-rotated"), expiry.capture());
        assertThat(expiry.getValue()).isBetween(before, after);
    }

    @Test
    void classifiesInvalidGrantNearTheRecordedExpiryAsLikelyTestingExpiration() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "health-user-1", Set.of(OAuthScopes.SLEEP), "encrypted-refresh",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now().minus(Duration.ofDays(7)), Instant.now().plus(Duration.ofMinutes(5)),
                null, Instant.now())));
        when(cipher.decrypt("encrypted-refresh")).thenReturn("refresh");
        when(tokenClient.refresh("refresh"))
                .thenThrow(new OAuthException("Google authorization is no longer valid", true, "invalid_grant"));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(provider::accessToken)
                .isInstanceOf(OAuthException.class);
        verify(repository).markReconnectRequired(0L,
                "Google authorization likely expired because the OAuth project is in Testing");
    }

    @Test
    void invalidGrantWellBeforeRecordedExpiryKeepsTheProviderError() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "health-user-1", Set.of(OAuthScopes.SLEEP), "encrypted-refresh",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now().minus(Duration.ofDays(6)), Instant.now().plus(Duration.ofDays(1)),
                null, Instant.now())));
        when(cipher.decrypt("encrypted-refresh")).thenReturn("refresh");
        when(tokenClient.refresh("refresh"))
                .thenThrow(new OAuthException("Google authorization is no longer valid", true, "invalid_grant"));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(provider::accessToken)
                .isInstanceOf(OAuthException.class);
        verify(repository).markReconnectRequired(0L, "Google authorization is no longer valid");
    }

    @Test
    void differentProviderErrorNearRecordedExpiryIsNotClassifiedAsTestingExpiration() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "health-user-1", Set.of(OAuthScopes.SLEEP), "encrypted-refresh",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now().minus(Duration.ofDays(7)), Instant.now().plus(Duration.ofMinutes(5)),
                null, Instant.now())));
        when(cipher.decrypt("encrypted-refresh")).thenReturn("refresh");
        when(tokenClient.refresh("refresh"))
                .thenThrow(new OAuthException("Google authorization is no longer valid", true, "invalid_client"));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);

        org.assertj.core.api.Assertions.assertThatThrownBy(provider::accessToken)
                .isInstanceOf(OAuthException.class);
        verify(repository).markReconnectRequired(0L, "Google authorization is no longer valid");
    }
}
