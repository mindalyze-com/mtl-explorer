package com.x8ing.mtl.server.mtlserver.googlehealth.application;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.ExerciseRouteService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.AccessTokenProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthCompletionResult;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthIntent;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthService;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataSourceResolver;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncWindowPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@Slf4j
public class GoogleHealthAdministrationService {

    private static final String DELETE_CONFIRMATION = "DELETE";
    private static final Duration DESTRUCTIVE_ACTION_SYNC_STOP_TIMEOUT = Duration.ofSeconds(35);

    private final ConnectorProperties properties;
    private final OAuthService oauthService;
    private final SyncCoordinator syncCoordinator;
    private final MirrorRepository repository;
    private final ExerciseRouteService exerciseRouteService;
    private final AccessTokenProvider accessTokenProvider;
    private final DataSourceResolver dataSourceResolver;
    private final GoogleHealthMirrorCache mirrorCache;

    public GoogleHealthAdministrationService(
            ConnectorProperties properties,
            OAuthService oauthService,
            SyncCoordinator syncCoordinator,
            MirrorRepository repository,
            ExerciseRouteService exerciseRouteService,
            AccessTokenProvider accessTokenProvider,
            DataSourceResolver dataSourceResolver,
            GoogleHealthMirrorCache mirrorCache) {
        this.properties = properties;
        this.oauthService = oauthService;
        this.syncCoordinator = syncCoordinator;
        this.repository = repository;
        this.exerciseRouteService = exerciseRouteService;
        this.accessTokenProvider = accessTokenProvider;
        this.dataSourceResolver = dataSourceResolver;
        this.mirrorCache = mirrorCache;
    }

    public AuthorizationStart startAuthorization(boolean reauthorize) {
        requireConfigured();
        OAuthIntent intent = reauthorize ? OAuthIntent.REAUTHORIZE : OAuthIntent.CONNECT;
        return new AuthorizationStart(oauthService.createAuthorizationUrl(intent), intent);
    }

    public OAuthService.CompletionOutcome completeManualAuthorization(String redirectUrl) {
        OAuthService.CompletionOutcome outcome = oauthService.completeManualAuthorizationWithOutcome(redirectUrl);
        requestPostAuthorizationSync(outcome, "manual OAuth");
        return outcome;
    }

    public OAuthService.CompletionOutcome completeAuthorization(String code, String state) {
        OAuthService.CompletionOutcome outcome = oauthService.completeAuthorizationWithOutcome(code, state);
        requestPostAuthorizationSync(outcome, "OAuth callback");
        return outcome;
    }

    public boolean cancelAuthorization(String state) {
        return oauthService.cancelAuthorization(state);
    }

    public SyncCoordinator.JobRequest requestRegularSync() {
        requireConnected();
        return syncCoordinator.request(SyncCoordinator.JobKind.REGULAR);
    }

    public SyncCoordinator.JobRequest requestFullReconciliation() {
        requireConnected();
        return requestReload(null, null, false);
    }

    public SyncCoordinator.JobRequest requestReload(LocalDate start, LocalDate end, boolean failedOnly) {
        requireConnected();
        var connection = repository.findConnection().orElseThrow();
        Instant now = Instant.now();
        LocalDate first = start == null ? properties.getHistoryStartDate() : start;
        LocalDate last = end == null ? now.plus(SyncWindowPolicy.CIVIL_UPPER_PADDING)
                .atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1) : end;
        if (first.isBefore(properties.getHistoryStartDate()) || !first.isBefore(last)
            || last.isAfter(now.plus(SyncWindowPolicy.CIVIL_UPPER_PADDING)
                .atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reload requires whole days inside the configured history");
        }
        runAfterStoppingSync(failedOnly ? "retry failed ranges" : "reload", () -> {
            repository.synchronizationGeneration(connection.googleUserId());
            repository.reloadDays(connection.googleUserId(), DataTypePolicy.allowedBy(connection.grantedScopes()),
                    first, last, SyncWindowPolicy.cutoff(properties, now), failedOnly);
            mirrorCache.clear();
        });
        return syncCoordinator.request(SyncCoordinator.JobKind.REGULAR);
    }

    public void disconnect() {
        runAfterStoppingSync("disconnect", () -> oauthService.runWithAuthorizationInvalidated(() -> {
            repository.disconnect();
            accessTokenProvider.invalidate();
        }));
    }

    public void deleteMirror(String confirmation) {
        if (!DELETE_CONFIRMATION.equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter DELETE to confirm local mirror deletion");
        }
        requireDisconnectedForMirrorDeletion();
        runAfterStoppingSync("delete local mirror", () -> oauthService.runWithAuthorizationInvalidated(() -> {
            requireDisconnectedForMirrorDeletion();
            repository.disconnect();
            accessTokenProvider.invalidate();
            exerciseRouteService.removeAllManagedRoutes();
            exerciseRouteService.removeOrphanedManagedRoutes();
            repository.deleteMirrorRows();
            dataSourceResolver.clearCache();
            mirrorCache.clear();
        }));
    }

    private void requestPostAuthorizationSync(OAuthService.CompletionOutcome outcome, String source) {
        if (outcome.result() != OAuthCompletionResult.CONNECTED) {
            return;
        }
        accessTokenProvider.invalidate();
        SyncCoordinator.JobKind syncKind = SyncCoordinator.JobKind.REGULAR;
        syncCoordinator.request(syncKind);
        log.info("Google Health {} completed; a {} sync was requested", source, syncKind);
    }

    private void runAfterStoppingSync(String actionName, Runnable action) {
        log.info("Google Health {} requested; stopping any active sync first", actionName);
        boolean completed;
        try {
            completed = syncCoordinator.runExclusivelyAfterCancelling(
                    DESTRUCTIVE_ACTION_SYNC_STOP_TIMEOUT, action);
        } catch (RuntimeException exception) {
            log.error("Google Health {} failed: {}", actionName, safeError(exception), exception);
            throw exception;
        }
        if (!completed) {
            log.warn("Google Health {} could not start because the active sync did not stop within {}",
                    actionName, DESTRUCTIVE_ACTION_SYNC_STOP_TIMEOUT);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The Google Health sync is still stopping. Try again shortly");
        }
        log.info("Google Health {} completed", actionName);
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    properties.isEnabled()
                            ? "Google Health server setup is incomplete"
                            : "Google Health is disabled for this installation");
        }
    }

    private void requireConnected() {
        requireConfigured();
        boolean connected = repository.findConnection()
                .map(AccountConnection::state)
                .filter(AccountConnection.ConnectionState.CONNECTED::equals)
                .isPresent();
        if (!connected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Google Health is not connected");
        }
    }

    private void requireDisconnectedForMirrorDeletion() {
        boolean connectionNotDisconnected = repository.findConnection()
                .map(AccountConnection::state)
                .filter(state -> state != AccountConnection.ConnectionState.DISCONNECTED)
                .isPresent();
        if (connectionNotDisconnected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Disconnect Google Health before deleting the local mirror");
        }
    }

    private static String safeError(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    public record AuthorizationStart(String authorizationUrl, OAuthIntent intent) {
    }
}
