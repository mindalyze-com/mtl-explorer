package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.AccountConnection;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SyncSchedulerTest {

    @Test
    void exposesTheNextActualRegularSchedulerCheck() {
        ConnectorProperties properties = configuredProperties();
        properties.setSyncInterval(Duration.ofMinutes(42));
        MirrorRepository repository = mock(MirrorRepository.class);
        SyncCoordinator coordinator = mock(SyncCoordinator.class);
        when(repository.findConnection()).thenReturn(Optional.of(new AccountConnection(
                "me", Set.of(), "encrypted", AccountConnection.ConnectionState.CONNECTED,
                null, Instant.now(), null, Instant.now())));
        SyncScheduler scheduler = new SyncScheduler(properties, new MtlAppProperties(), repository, coordinator);

        Instant before = Instant.now();
        scheduler.recurringSync();
        Instant after = Instant.now();

        verify(coordinator).request(ArgumentMatchers.eq(SyncCoordinator.JobKind.REGULAR), ArgumentMatchers.any());
        assertThat(scheduler.nextRegularSync())
                .isBetween(before.plus(Duration.ofMinutes(43)).minusSeconds(1), after.plus(Duration.ofMinutes(43)));
    }

    @Test
    void unconfiguredConnectorDoesNotReadConnectionsOrStartJobs() {
        ConnectorProperties properties = new ConnectorProperties();
        MirrorRepository repository = mock(MirrorRepository.class);
        SyncCoordinator coordinator = mock(SyncCoordinator.class);
        SyncScheduler scheduler = new SyncScheduler(properties, new MtlAppProperties(), repository, coordinator);

        scheduler.recurringSync();

        verify(repository, never()).findConnection();
        verify(coordinator, never()).request(ArgumentMatchers.any());
    }

    @Test
    void demoModeDoesNotReadConnectionsOrStartJobsEvenWhenConfigured() {
        ConnectorProperties properties = configuredProperties();
        MtlAppProperties appProperties = new MtlAppProperties();
        appProperties.setDemoMode(true);
        MirrorRepository repository = mock(MirrorRepository.class);
        SyncCoordinator coordinator = mock(SyncCoordinator.class);
        SyncScheduler scheduler = new SyncScheduler(properties, appProperties, repository, coordinator);

        scheduler.recurringSync();

        verify(repository, never()).findConnection();
        verify(coordinator, never()).request(ArgumentMatchers.any());
    }

    private static ConnectorProperties configuredProperties() {
        ConnectorProperties properties = new ConnectorProperties();
        properties.setClientId("client");
        properties.setClientSecret("secret");
        properties.setRedirectUri("http://localhost/mtl/api/google-health/oauth/callback");
        return properties;
    }
}
