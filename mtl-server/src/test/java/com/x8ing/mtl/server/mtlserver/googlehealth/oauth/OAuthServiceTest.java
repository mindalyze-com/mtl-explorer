package com.x8ing.mtl.server.mtlserver.googlehealth.oauth;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OAuthServiceTest {

    @Test
    void rejectedPkceExchangeCannotPersistOrReuseTheAuthorizationAttempt() {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization pending = stateStore.create(properties.resolvedRedirectUri());
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        GoogleHealthIdentityClient identity = mock(GoogleHealthIdentityClient.class);
        when(tokenClient.exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri()))
                .thenThrow(new OAuthException("Google authorization is no longer valid", true, "invalid_grant"));
        OAuthService service = new OAuthService(properties, stateStore, tokenClient, cipher, repository, identity);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.completeAuthorization("code", pending.state()))
                .isInstanceOf(OAuthException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.completeAuthorization("code", pending.state()))
                .isInstanceOf(OAuthStateException.class);
        verify(tokenClient).exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri());
        verifyNoInteractions(repository, cipher, identity);
    }

    @Test
    void persistsOnlyGrantedCategoriesAfterPartialConsent() {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization pending = stateStore.create(properties.resolvedRedirectUri());
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher tokenCipher = mock(RefreshTokenCipher.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        when(tokenClient.exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri()))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access", "refresh", Duration.ofHours(1), Set.of(OAuthScopes.SLEEP)));
        when(tokenCipher.encrypt("refresh")).thenReturn("encrypted-refresh");
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient, tokenCipher, repository, identityClient());

        OAuthService.CompletionOutcome outcome =
                service.completeAuthorizationWithOutcome("code", pending.state());

        assertThat(outcome.grantedScopes()).containsExactly(OAuthScopes.SLEEP);
        assertThat(outcome.fullReconciliationRequired()).isFalse();
        verify(repository).saveConnected("health-user-1", Set.of(OAuthScopes.SLEEP), "encrypted-refresh", null);
    }

    @Test
    void authorizationUrlUsesOfflinePkceReadOnlyScopes() {
        ConnectorProperties properties = configuredProperties();
        OAuthService service = new OAuthService(
                properties,
                new OAuthStateStore(),
                mock(OAuthTokenClient.class),
                mock(RefreshTokenCipher.class),
                mock(MirrorRepository.class),
                identityClient());

        String url = service.createAuthorizationUrl();

        assertThat(url)
                .startsWith(properties.getAuthorizationUrl() + "?")
                .contains("access_type=offline")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%2Fmtl%2Fapi%2Fgoogle-health%2Foauth%2Fcallback")
                .contains("code_challenge_method=S256")
                .contains("state=")
                .contains("googlehealth.activity_and_fitness.readonly")
                .contains("googlehealth.health_metrics_and_measurements.readonly")
                .contains("googlehealth.sleep.readonly")
                .contains("googlehealth.location.readonly")
                .doesNotContain("nutrition")
                .doesNotContain("write");
    }

    @Test
    void manualAuthorizationUsesTheDeadLoopbackRedirectAndCompletesFromThePastedUrl() {
        ConnectorProperties properties = configuredProperties();
        properties.setRedirectUri(null);
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher tokenCipher = mock(RefreshTokenCipher.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient, tokenCipher, repository, identityClient());

        String authorizationUrl = service.createAuthorizationUrl();
        String state = queryParameter(authorizationUrl, "state");
        OAuthStateStore.PendingAuthorization pending = stateStore.consume(state).orElseThrow();
        OAuthStateStore replacementStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization replacement = replacementStore.create(OAuthPaths.MANUAL_REDIRECT_URI);
        OAuthService completingService = new OAuthService(
                properties, replacementStore, tokenClient, tokenCipher, repository, identityClient());
        when(tokenClient.exchangeAuthorizationCode(
                "returned-code", replacement.codeVerifier(), OAuthPaths.MANUAL_REDIRECT_URI))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access", "refresh", Duration.ofHours(1), Set.of(OAuthScopes.SLEEP)));
        when(tokenCipher.encrypt("refresh")).thenReturn("encrypted-refresh");

        OAuthCompletionResult result = completingService.completeManualAuthorization(
                OAuthPaths.MANUAL_REDIRECT_URI + "?code=returned-code&state=" + replacement.state());

        assertThat(authorizationUrl).contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A1");
        assertThat(pending.redirectUri()).isEqualTo(OAuthPaths.MANUAL_REDIRECT_URI);
        assertThat(result).isEqualTo(OAuthCompletionResult.CONNECTED);
        verify(repository).saveConnected("health-user-1", Set.of(OAuthScopes.SLEEP), "encrypted-refresh", null);
    }

    @Test
    void manualAuthorizationHandlesDenialAndRejectsOtherRedirectAddresses() {
        ConnectorProperties properties = configuredProperties();
        properties.setRedirectUri(null);
        OAuthStateStore deniedStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization denied = deniedStore.create(OAuthPaths.MANUAL_REDIRECT_URI);
        OAuthService deniedService = new OAuthService(
                properties, deniedStore, mock(OAuthTokenClient.class),
                mock(RefreshTokenCipher.class), mock(MirrorRepository.class), identityClient());

        assertThat(deniedService.completeManualAuthorization(
                OAuthPaths.MANUAL_REDIRECT_URI + "?error=access_denied&state=" + denied.state()))
                .isEqualTo(OAuthCompletionResult.DENIED);

        OAuthService invalidService = new OAuthService(
                properties, new OAuthStateStore(), mock(OAuthTokenClient.class),
                mock(RefreshTokenCipher.class), mock(MirrorRepository.class), identityClient());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> invalidService.completeManualAuthorization(
                        "http://localhost:1/?code=code&state=state"))
                .isInstanceOf(OAuthException.class)
                .hasMessage("Paste the complete 127.0.0.1 address returned by Google");
    }

    @Test
    void manualAuthorizationCannotBeCompletedThroughTheAnonymousProxyCallback() {
        ConnectorProperties properties = configuredProperties();
        properties.setRedirectUri(null);
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient,
                mock(RefreshTokenCipher.class), mock(MirrorRepository.class), identityClient());
        String state = queryParameter(service.createAuthorizationUrl(), "state");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.completeAuthorization("code", state))
                .isInstanceOf(OAuthStateException.class);

        assertThat(stateStore.consume(state)).isPresent();
        verifyNoInteractions(tokenClient);
    }

    @Test
    void neverAssumesUnreportedScopesWereGranted() {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization pending = stateStore.create(properties.resolvedRedirectUri());
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher tokenCipher = mock(RefreshTokenCipher.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        when(tokenClient.exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri()))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access", "refresh", Duration.ofHours(1), Set.of()));
        when(tokenCipher.encrypt("refresh")).thenReturn("encrypted-refresh");
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient, tokenCipher, repository, identityClient());

        assertThat(service.completeAuthorization("code", pending.state())).isEmpty();
        verify(repository).saveConnected("health-user-1", Set.of(), "encrypted-refresh", null);
    }

    @Test
    void reconnectReusesTheDecryptedStoredRefreshTokenWhenGoogleOmitsANewOne() {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization pending = stateStore.create(properties.resolvedRedirectUri());
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher tokenCipher = mock(RefreshTokenCipher.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        when(tokenClient.exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri()))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access", null, Duration.ofHours(1), Set.of(OAuthScopes.LOCATION)));
        Instant existingExpiry = Instant.parse("2026-09-08T12:00:00Z");
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(OAuthScopes.SLEEP), "stored-ciphertext",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), existingExpiry, null, Instant.now())));
        when(tokenCipher.decrypt("stored-ciphertext")).thenReturn("stored-refresh");
        when(tokenCipher.encrypt("stored-refresh")).thenReturn("new-ciphertext");
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient, tokenCipher, repository, identityClient());

        OAuthService.CompletionOutcome outcome =
                service.completeAuthorizationWithOutcome("code", pending.state());

        assertThat(outcome.grantedScopes()).containsExactly(OAuthScopes.LOCATION);
        assertThat(outcome.fullReconciliationRequired()).isTrue();
        verify(repository).saveConnected(
                "health-user-1", Set.of(OAuthScopes.LOCATION), "new-ciphertext", existingExpiry);
    }

    @Test
    void reauthorizationRequiresANewRefreshToken() {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization pending = stateStore.create(
                properties.resolvedRedirectUri(), OAuthIntent.REAUTHORIZE);
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        when(tokenClient.exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri()))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access", null, Duration.ofHours(1), Set.of(OAuthScopes.SLEEP)));
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "health-user-1", Set.of(OAuthScopes.SLEEP), "stored-ciphertext",
                AccountConnection.ConnectionState.CONNECTED, null,
                Instant.now(), null, Instant.now())));
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient, mock(RefreshTokenCipher.class), repository, identityClient());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.completeAuthorization("code", pending.state()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("new refresh token");
    }

    @Test
    void storesTheReportedRefreshTokenExpiryAndStableHealthUserId() {
        ConnectorProperties properties = configuredProperties();
        OAuthStateStore stateStore = new OAuthStateStore();
        OAuthStateStore.PendingAuthorization pending = stateStore.create(properties.resolvedRedirectUri());
        OAuthTokenClient tokenClient = mock(OAuthTokenClient.class);
        RefreshTokenCipher tokenCipher = mock(RefreshTokenCipher.class);
        MirrorRepository repository = mock(MirrorRepository.class);
        when(tokenClient.exchangeAuthorizationCode("code", pending.codeVerifier(), pending.redirectUri()))
                .thenReturn(new OAuthTokenClient.TokenResponse(
                        "access", "refresh", Duration.ofHours(1), Duration.ofDays(7), Set.of(OAuthScopes.SLEEP)));
        when(tokenCipher.encrypt("refresh")).thenReturn("encrypted-refresh");
        OAuthService service = new OAuthService(
                properties, stateStore, tokenClient, tokenCipher, repository, identityClient());

        Instant before = Instant.now().plus(Duration.ofDays(7)).minusSeconds(1);
        service.completeAuthorization("code", pending.state());
        Instant after = Instant.now().plus(Duration.ofDays(7)).plusSeconds(1);

        org.mockito.ArgumentCaptor<Instant> expiry = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(repository).saveConnected(
                org.mockito.ArgumentMatchers.eq("health-user-1"),
                org.mockito.ArgumentMatchers.eq(Set.of(OAuthScopes.SLEEP)),
                org.mockito.ArgumentMatchers.eq("encrypted-refresh"),
                expiry.capture());
        assertThat(expiry.getValue()).isBetween(before, after);
    }

    private static ConnectorProperties configuredProperties() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client");
        properties.setClientSecret("secret");
        properties.setRedirectUri("http://localhost/mtl/api/google-health/oauth/callback");
        return properties;
    }

    private static GoogleHealthIdentityClient identityClient() {
        GoogleHealthIdentityClient client = mock(GoogleHealthIdentityClient.class);
        when(client.healthUserId("access")).thenReturn("health-user-1");
        return client;
    }

    private static String queryParameter(String url, String name) {
        return java.util.Arrays.stream(java.net.URI.create(url).getRawQuery().split("&"))
                .map(value -> value.split("=", 2))
                .filter(parts -> java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8)
                        .equals(name))
                .map(parts -> java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }
}
