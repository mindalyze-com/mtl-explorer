package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class SyncScheduler {

    private static final Duration INITIAL_REGULAR_SYNC_DELAY = Duration.ofMinutes(1);

    private final ConnectorProperties properties;
    private final MtlAppProperties appProperties;
    private final MirrorRepository repository;
    private final SyncCoordinator coordinator;
    private volatile Instant nextRegularSync;

    public SyncScheduler(ConnectorProperties properties,
                         MtlAppProperties appProperties,
                         MirrorRepository repository,
                         SyncCoordinator coordinator) {
        this.properties = properties;
        this.appProperties = appProperties;
        this.repository = repository;
        this.coordinator = coordinator;
        this.nextRegularSync = Instant.now().plus(INITIAL_REGULAR_SYNC_DELAY);
    }

    @Scheduled(fixedRateString = "${mtl.google-health.sync-interval:PT30M}", initialDelayString = "PT1M")
    public void recurringSync() {
        try {
            requestIfConnected(SyncCoordinator.JobKind.REGULAR);
        } finally {
            nextRegularSync = nextRegularSync.plus(properties.getSyncInterval());
        }
    }

    public Instant nextRegularSync() {
        return nextRegularSync;
    }

    private void requestIfConnected(SyncCoordinator.JobKind kind) {
        if (appProperties.isDemoMode()) {
            log.debug("Skipping scheduled Google Health sync in demo mode: kind={}", kind);
            return;
        }
        if (!properties.isConfigured()) {
            log.debug("Skipping scheduled Google Health sync because the connector is not configured: kind={}", kind);
            return;
        }
        boolean connected = repository.findConnection()
                .map(AccountConnection::state)
                .filter(state -> state == AccountConnection.ConnectionState.CONNECTED)
                .isPresent();
        if (connected) {
            log.info("Scheduled Google Health sync is due: kind={}", kind);
            coordinator.request(kind, nextRegularSync.plus(properties.getSyncInterval()).minus(properties.getCycleDeadlineMargin()));
        } else {
            log.debug("Skipping scheduled Google Health sync because no account is connected: kind={}", kind);
        }
    }
}
