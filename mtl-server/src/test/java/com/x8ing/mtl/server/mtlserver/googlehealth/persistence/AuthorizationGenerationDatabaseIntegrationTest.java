package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.*;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
@Transactional
class AuthorizationGenerationDatabaseIntegrationTest {

    @Autowired
    private MirrorRepository repository;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    void newAuthorizationRejectsEveryOldRefreshMutation() {
        repository.disconnect();
        repository.saveConnected("synthetic-user", Set.of(OAuthScopes.SLEEP), "old-refresh", null);
        AccountConnection old = repository.findConnection().orElseThrow();
        Instant freshExpiry = Instant.parse("2045-09-12T12:00:00Z");
        repository.saveConnected("synthetic-user", Set.of(OAuthScopes.SLEEP), "fresh-refresh", freshExpiry);
        AccountConnection fresh = repository.findConnection().orElseThrow();

        assertThat(fresh.authorizationGeneration()).isGreaterThan(old.authorizationGeneration());
        assertThat(repository.updateRefreshToken(old.authorizationGeneration(), "stale-rotation", null)).isFalse();
        assertThat(repository.updateRefreshTokenExpiry(old.authorizationGeneration(), Instant.EPOCH)).isFalse();
        assertThat(repository.markReconnectRequired(old.authorizationGeneration(), "Old refresh failed")).isFalse();
        assertThat(repository.findConnection()).contains(fresh);
    }

    @Test
    void disconnectedAndDeletedMirrorCannotBeRevivedByAnOldRefresh() {
        repository.disconnect();
        repository.saveConnected("synthetic-user", Set.of(OAuthScopes.SLEEP), "old-refresh", null);
        long oldGeneration = repository.findConnection().orElseThrow().authorizationGeneration();
        repository.disconnect();
        repository.deleteMirrorRows();
        AccountConnection disconnected = repository.findConnection().orElseThrow();

        assertThat(disconnected.authorizationGeneration()).isGreaterThan(oldGeneration);
        assertThat(repository.updateRefreshToken(oldGeneration, "stale-rotation", null)).isFalse();
        assertThat(repository.updateRefreshTokenExpiry(oldGeneration, Instant.EPOCH)).isFalse();
        assertThat(repository.markReconnectRequired(oldGeneration, "Old refresh failed")).isFalse();
        assertThat(repository.findConnection()).contains(disconnected);
        assertThat(disconnected.refreshTokenCiphertext()).isNull();
        assertThat(disconnected.state()).isEqualTo(AccountConnection.ConnectionState.DISCONNECTED);
    }

    @Test
    void currentGenerationCanRotateExpireAndRequestReauthorization() {
        repository.disconnect();
        repository.saveConnected("synthetic-user", Set.of(OAuthScopes.SLEEP), "old-refresh", null);
        long generation = repository.findConnection().orElseThrow().authorizationGeneration();
        Instant expiry = Instant.parse("2045-09-12T12:00:00Z");

        assertThat(repository.updateRefreshToken(generation, "rotated-refresh", expiry)).isTrue();
        assertThat(repository.findConnection().orElseThrow().refreshTokenCiphertext()).isEqualTo("rotated-refresh");
        assertThat(repository.updateRefreshTokenExpiry(generation, expiry.plusSeconds(1))).isTrue();
        assertThat(repository.findConnection().orElseThrow().refreshTokenExpiresAt()).isEqualTo(expiry.plusSeconds(1));
        assertThat(repository.markReconnectRequired(generation, "Current grant expired")).isTrue();
        assertThat(repository.findConnection().orElseThrow().state())
                .isEqualTo(AccountConnection.ConnectionState.RECONNECT_REQUIRED);
        assertThat(repository.updateRefreshToken(generation, "late-rotation", null)).isFalse();
    }

    @Test
    void expiredOldGrantDoesNotInvalidateANewAuthorizationCommittedDuringRefresh() {
        repository.disconnect();
        repository.saveConnected("synthetic-user", Set.of(OAuthScopes.SLEEP), "old-refresh", null);
        RefreshTokenCipher cipher = mock(RefreshTokenCipher.class);
        OAuthTokenClient client = mock(OAuthTokenClient.class);
        when(cipher.decrypt("old-refresh")).thenReturn("old-refresh");
        when(cipher.decrypt("fresh-refresh")).thenReturn("fresh-refresh");
        when(client.refresh("old-refresh")).thenAnswer(call -> {
            repository.saveConnected("synthetic-user", Set.of(OAuthScopes.SLEEP), "fresh-refresh", null);
            throw new OAuthException("Synthetic old grant expired", true);
        });
        when(client.refresh("fresh-refresh")).thenReturn(new OAuthTokenClient.TokenResponse(
                "fresh-access", null, Duration.ofHours(1), Set.of(OAuthScopes.SLEEP)));

        AccessTokenProvider provider = new AccessTokenProvider(repository, cipher, client);
        assertThat(provider.accessToken()).isEqualTo("fresh-access");
        AccountConnection fresh = repository.findConnection().orElseThrow();
        assertThat(fresh.state()).isEqualTo(AccountConnection.ConnectionState.CONNECTED);
        assertThat(fresh.refreshTokenCiphertext()).isEqualTo("fresh-refresh");
        assertThat(fresh.lastError()).isNull();
    }
}
