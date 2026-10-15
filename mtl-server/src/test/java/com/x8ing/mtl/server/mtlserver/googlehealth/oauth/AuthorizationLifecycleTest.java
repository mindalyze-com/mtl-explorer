package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthAdministrationService;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizationLifecycleTest {

    @ParameterizedTest
    @CsvSource({"REAUTHORIZE,false", "REAUTHORIZE,true", "DISCONNECT,false", "DISCONNECT,true",
            "DELETE,false", "DELETE,true"})
    void oldRefreshCannotPublishTokensOrErrorsAfterALifecycleChange(String action, boolean refreshFails) {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        AtomicReference<AccountConnection> saved = new AtomicReference<>(connected("old-token", 1L));
        when(repository.findConnection()).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(cipher.decrypt(anyString())).thenAnswer(call -> call.getArgument(0));
        when(cipher.encrypt(anyString())).thenAnswer(call -> call.getArgument(0));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);
        when(repository.updateRefreshToken(anyLong(), anyString(), any())).thenAnswer(call -> {
            // Same conditional write as the repository; an obsolete rotation is rejected.
            return saved.get() != null && saved.get().authorizationGeneration() == call.<Long>getArgument(0)
                   && saved.get().state() == AccountConnection.ConnectionState.CONNECTED;
        });
        when(tokenClient.refresh("fresh-token")).thenReturn(tokenResponse("fresh-access", null));
        when(tokenClient.refresh("old-token")).thenAnswer(call -> {
            if (action.equals("REAUTHORIZE")) {
                saved.set(connected("fresh-token", 2L));
            } else if (action.equals("DISCONNECT")) {
                saved.set(new AccountConnection("synthetic-user", Set.of(), null,
                        AccountConnection.ConnectionState.DISCONNECTED, null, null, null, null,
                        Instant.now(), 2L));
            } else {
                saved.set(null);
            }
            provider.invalidate();
            if (refreshFails) {
                throw new OAuthException("Synthetic expired old grant", true);
            }
            return tokenResponse("obsolete-access", "obsolete-rotated-refresh");
        });

        if (action.equals("REAUTHORIZE")) {
            assertThat(provider.accessToken()).isEqualTo("fresh-access");
            assertThat(provider.accessToken()).isEqualTo("fresh-access");
            assertThat(saved.get().refreshTokenCiphertext()).isEqualTo("fresh-token");
            assertThat(saved.get().state()).isEqualTo(AccountConnection.ConnectionState.CONNECTED);
        } else {
            assertThatThrownBy(provider::accessToken).isInstanceOf(OAuthException.class)
                    .hasMessage("Google Health is not connected");
            assertThatThrownBy(provider::accessToken).isInstanceOf(OAuthException.class);
        }
        verify(repository, never()).markReconnectRequired(anyLong(), anyString());
    }

    @Test
    void aPreviouslyCachedTokenCannotBeUsedByANewerConnectionEvenBeforeExplicitInvalidation() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        AtomicReference<AccountConnection> saved = new AtomicReference<>(connected("old-token", 1L));
        when(repository.findConnection()).thenAnswer(call -> Optional.of(saved.get()));
        when(cipher.decrypt(anyString())).thenAnswer(call -> call.getArgument(0));
        when(tokenClient.refresh("old-token")).thenReturn(tokenResponse("old-access", null));
        when(tokenClient.refresh("new-token")).thenReturn(tokenResponse("new-access", null));
        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);

        assertThat(provider.accessToken()).isEqualTo("old-access");
        saved.set(connected("new-token", 2L));
        assertThat(provider.accessToken()).isEqualTo("new-access");
    }

    @Test
    void aNewAuthorizationCommittedBetweenTheRefreshFailureCheckAndUpdateIsRetained() {
        MirrorRepository repository = mock(MirrorRepository.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        AtomicReference<AccountConnection> saved = new AtomicReference<>(connected("old-token", 1L));
        when(repository.findConnection()).thenAnswer(call -> Optional.of(saved.get()));
        when(cipher.decrypt(anyString())).thenAnswer(call -> call.getArgument(0));
        when(tokenClient.refresh("old-token")).thenThrow(new OAuthException("Old grant expired", true));
        when(tokenClient.refresh("new-token")).thenReturn(tokenResponse("new-access", null));
        when(repository.markReconnectRequired(1L, "Old grant expired")).thenAnswer(call -> {
            saved.set(connected("new-token", 2L));
            return false;
        });

        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, tokenClient);
        assertThat(provider.accessToken()).isEqualTo("new-access");
        assertThat(saved.get().state()).isEqualTo(AccountConnection.ConnectionState.CONNECTED);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void disconnectAndDeletionInvalidatePendingAndAlreadyConsumedCallbacks(boolean delete) {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore states = new OAuthStateStore();
        MirrorRepository repository = mock(MirrorRepository.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        GoogleHealthIdentityClient identity = mock(GoogleHealthIdentityClient.class);
        OAuthService oauth = new OAuthService(properties, states, tokenClient, cipher, repository, identity);
        AccessTokenProvider tokens = new AccessTokenProvider(repository, cipher, tokenClient);
        GoogleHealthAdministrationService admin = administration(properties, oauth, repository, tokens);
        var callback = states.create(properties.resolvedRedirectUri());
        var otherPending = states.create(properties.resolvedRedirectUri());
        when(tokenClient.exchangeAuthorizationCode("code", callback.codeVerifier(), callback.redirectUri()))
                .thenAnswer(call -> {
                    if (delete) {
                        admin.deleteMirror("DELETE");
                    } else {
                        admin.disconnect();
                    }
                    return tokenResponse("access", "refresh");
                });
        when(cipher.encrypt("refresh")).thenReturn("ciphertext");
        when(identity.healthUserId("access")).thenReturn("synthetic-user");

        assertThatThrownBy(() -> oauth.completeAuthorization("code", callback.state()))
                .isInstanceOf(OAuthStateException.class);
        assertThat(states.consume(otherPending.state())).isEmpty();
        verify(repository, never()).saveConnected(anyString(), any(), anyString(), any());
        verify(repository).disconnect();
        if (delete) {
            verify(repository).deleteMirrorRows();
        }
    }

    @Test
    void disabledConnectorCanDisconnectAndDeleteWithoutContactingGoogle() {
        ConnectorProperties properties = configuredProperties();
        properties.setEnabled(false);
        MirrorRepository repository = mock(MirrorRepository.class);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        GoogleHealthIdentityClient identity = mock(GoogleHealthIdentityClient.class);
        OAuthStateStore states = new OAuthStateStore();
        var pending = states.create(properties.resolvedRedirectUri());
        OAuthService oauth = new OAuthService(properties, states, tokenClient, cipher, repository, identity);
        AtomicReference<AccountConnection> saved = new AtomicReference<>(connected("encrypted", 1L));
        when(repository.findConnection()).thenAnswer(call -> Optional.ofNullable(saved.get()));
        doAnswer(call -> {
            saved.set(null);
            return null;
        }).when(repository).disconnect();
        GoogleHealthAdministrationService admin = administration(properties, oauth, repository,
                new AccessTokenProvider(repository, cipher, tokenClient));

        admin.disconnect();
        admin.deleteMirror("DELETE");

        assertThat(states.consume(pending.state())).isEmpty();
        verify(repository).deleteMirrorRows();
        verifyNoInteractions(tokenClient, identity, cipher);
    }

    private static GoogleHealthAdministrationService administration(
            ConnectorProperties properties, OAuthService oauth, MirrorRepository repository,
            AccessTokenProvider tokens) {
        SyncCoordinator coordinator = mock(SyncCoordinator.class);
        when(coordinator.runExclusivelyAfterCancelling(any(), any())).thenAnswer(call -> {
            call.<Runnable>getArgument(1).run();
            return true;
        });
        return new GoogleHealthAdministrationService(properties, oauth, coordinator, repository,
                mock(ExerciseRouteService.class), tokens, mock(DataSourceResolver.class),
                mock(GoogleHealthMirrorCache.class));
    }

    private static AccountConnection connected(String token, long generation) {
        return new AccountConnection("synthetic-user", Set.of(OAuthScopes.SLEEP), token,
                AccountConnection.ConnectionState.CONNECTED, null, Instant.now(), null, null,
                Instant.now(), generation);
    }

    private static OAuthTokenClient.TokenResponse tokenResponse(String access, String refresh) {
        return new OAuthTokenClient.TokenResponse(access, refresh, Duration.ofHours(1), Set.of(OAuthScopes.SLEEP));
    }

    private static ConnectorProperties configuredProperties() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("synthetic-client");
        properties.setClientSecret("synthetic-secret");
        properties.setRedirectUri("http://localhost/mtl/api/google-health/oauth/callback");
        return properties;
    }
}
