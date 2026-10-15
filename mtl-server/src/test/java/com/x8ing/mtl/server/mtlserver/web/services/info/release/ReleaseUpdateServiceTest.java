package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import com.x8ing.mtl.server.mtlserver.web.services.info.ImageVersionInfoDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static com.x8ing.mtl.server.mtlserver.web.services.info.release.ReleaseUpdateResponse.Status;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseUpdateServiceTest {
    private final ReleaseUpdateProperties properties = new ReleaseUpdateProperties();
    private final DockerImageInfoService image = mock(DockerImageInfoService.class);
    private final ServerIdentityService identity = mock(ServerIdentityService.class);
    private final ReleaseFeedClient client = mock(ReleaseFeedClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);

    private ReleaseUpdateService service(String build) {
        when(image.getImageInfo()).thenReturn(ImageVersionInfoDto.of(build, "revision", null));
        when(identity.getServerId()).thenReturn("installation");
        return new ReleaseUpdateService(properties, image, identity, client, clock, Runnable::run, null);
    }

    private ReleaseFeedClient.Release release(String build) {
        return new ReleaseFeedClient.Release(build, "example/mtl:" + build, "sha256:" + "a".repeat(64),
                "Changes", "https://mtl-explorer.com/releases/0.9.0/", "https://mtl-explorer.com/docker-install/",
                LocalDate.of(2026, 10, 3));
    }

    private ReleaseFeedClient.Feed feed() {
        return new ReleaseFeedClient.Feed(1, Map.of("stable", "0.10.0"),
                Map.of("0.9.0", release("1.508"), "0.10.0", release("1.520")));
    }

    @Test
    void checksOnceAndComparesPublicVersions() throws Exception {
        when(client.fetch(anyString())).thenReturn(feed());
        var service = service("1.508");
        assertEquals(Status.UPDATE_AVAILABLE, service.getStatus(false).status());
        assertEquals("0.9.0", service.getStatus(false).installedReleaseVersion());
        assertEquals("0.10.0", service.getStatus(true).latestReleaseVersion());
        verify(client, times(1)).fetch(anyString());
    }

    @Test
    void promotionOfInstalledImageNeedsNoUpdate() throws Exception {
        when(client.fetch(anyString())).thenReturn(feed());
        assertEquals(Status.UP_TO_DATE, service("1.520").getStatus(false).status());
    }

    @Test
    void publicReleaseOrderIsIndependentOfImageBuildNumber() throws Exception {
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.10.0"),
                Map.of("0.9.0", release("1.520"), "0.10.0", release("1.508"))));
        var result = service("1.520").getStatus(false);
        assertEquals(Status.UPDATE_AVAILABLE, result.status());
        assertEquals("0.9.0", result.installedReleaseVersion());
        assertEquals("0.10.0", result.latestReleaseVersion());
    }

    @Test
    void sameImageCanBePromotedWithoutRebuilding() throws Exception {
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.10.0"),
                Map.of("0.9.0", release("1.508"), "0.10.0", release("1.508"))));
        var result = service("1.508").getStatus(false);
        assertEquals(Status.UP_TO_DATE, result.status());
        assertEquals("0.10.0", result.installedReleaseVersion());
    }

    @Test
    void unknownBuildAndEmptyFeedDoNotClaimAnUpdate() throws Exception {
        when(client.fetch(anyString())).thenReturn(feed());
        assertEquals(Status.UNKNOWN, service("custom").getStatus(false).status());
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of(), Map.of()));
        assertEquals(Status.UNKNOWN, service("1.508").getStatus(false).status());
    }

    @Test
    void disabledChecksMakeNoNetworkRequest() {
        properties.setEnabled(false);
        var result = service("1.508").getStatus(true);
        assertEquals(Status.DISABLED, result.status());
        assertNull(result.installedReleaseVersion());
        assertEquals("1.508", result.installedBuildId());
        verifyNoInteractions(client);
    }

    @Test
    void failureIsUnavailableAndRetriesAreThrottled() throws Exception {
        when(client.fetch(anyString())).thenThrow(new IOException("offline"));
        var service = service("1.508");
        assertEquals(Status.UNAVAILABLE, service.getStatus(false).status());
        assertTrue(service.getStatus(false).checkFailed());
        assertNull(service.getStatus(false).installedReleaseVersion());
        assertEquals("1.508", service.getStatus(false).installedBuildId());
        verify(client, times(1)).fetch(anyString());
    }

    @Test
    void failedRefreshKeepsLastValidOffer() throws Exception {
        var currentTime = new java.util.concurrent.atomic.AtomicReference<>(clock.instant());
        var advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenAnswer(invocation -> currentTime.get());
        when(image.getImageInfo()).thenReturn(ImageVersionInfoDto.of("1.508", "revision", null));
        when(identity.getServerId()).thenReturn("installation");
        when(client.fetch(anyString())).thenReturn(feed()).thenThrow(new IOException("network blocked"));
        var service = new ReleaseUpdateService(properties, image, identity, client, advancingClock, Runnable::run, null);
        var valid = service.getStatus(false);
        currentTime.set(clock.instant().plusSeconds(86_400));
        var cached = service.getStatus(false);
        assertEquals(Status.UPDATE_AVAILABLE, cached.status());
        assertEquals(valid.latestReleaseVersion(), cached.latestReleaseVersion());
        assertEquals(valid.checkedAt(), cached.checkedAt());
        assertTrue(cached.checkFailed());
        service.getStatus(false);
        verify(client, times(2)).fetch(anyString());
    }

    @Test
    void statusDoesNotWaitForNetworkAndStoppedWorkerDoesNotFailRequests() {
        when(image.getImageInfo()).thenReturn(ImageVersionInfoDto.of("1.508", "revision", null));
        when(identity.getServerId()).thenReturn("installation");
        var queued = new java.util.ArrayList<Runnable>();
        var service = new ReleaseUpdateService(properties, image, identity, client, clock, queued::add, null);
        var checking = service.getStatus(false);
        assertEquals(Status.CHECKING, checking.status());
        assertNull(checking.installedReleaseVersion());
        assertEquals("1.508", checking.installedBuildId());
        assertEquals(1, queued.size());
        verifyNoInteractions(client);
        var stopped = new ReleaseUpdateService(properties, image, identity, client, clock,
                task -> {
                    throw new java.util.concurrent.RejectedExecutionException();
                }, null);
        assertEquals(Status.UNAVAILABLE, stopped.getStatus(false).status());
    }

    @Test
    void betaDoesNotFollowStableTarget() throws Exception {
        properties.setChannel("beta");
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1,
                Map.of("stable", "0.9.0", "beta", "0.10.0-beta.2"),
                Map.of("0.9.0", release("1.508"), "0.10.0-beta.2", release("1.519"))));
        assertEquals("0.10.0-beta.2", service("1.508").getStatus(false).latestReleaseVersion());
    }

    @Test
    void semanticPrecedenceAndFeedValidation() {
        assertTrue(ReleaseVersion.compare("0.10.0", "0.9.0") > 0);
        assertTrue(ReleaseVersion.compare("0.9.0-beta.10", "0.9.0-beta.2") > 0);
        assertTrue(ReleaseVersion.compare("0.9.0", "0.9.0-rc.1") > 0);
        assertFalse(ReleaseVersion.valid("0.9.0-beta.01"));
        assertFalse(ReleaseVersion.valid("1.508"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseFeedClient.safeUrl("javascript:alert(1)"));
    }

    private ReleaseFeedClient.Artifact artifact(String id, String build, java.util.List<String> compatible) {
        return new ReleaseFeedClient.Artifact(build, "example/" + id + ":" + build, "sha256:" + "a".repeat(64), compatible);
    }

    private ReleaseFeedClient.Release bundle(String searchBuild, java.util.List<String> compatible) {
        var app = release("1.508");
        return new ReleaseFeedClient.Release(app.buildId(), app.image(), app.digest(), app.summary(), app.releaseNotesUrl(),
                app.updateGuideUrl(), app.publishedOn(), Map.of("app", artifact("mtl", "1.508", null),
                "location-search", artifact("search", searchBuild, compatible)));
    }

    private ReleaseUpdateService bundledService(ReleaseComponentInventory inventory) {
        service("1.508");
        return new ReleaseUpdateService(properties, image, identity, client, clock, Runnable::run, inventory);
    }

    @Test
    void componentOnlyUpdateClearsWithoutChangingApplicationBuild() throws Exception {
        var inventory = mock(ReleaseComponentInventory.class);
        var self = com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management.SELF_MANAGED;
        when(inventory.snapshot()).thenReturn(java.util.List.of(new ReleaseComponentInventory.Installed("location-search", "Search", self, "2.1", false)))
                .thenReturn(java.util.List.of(new ReleaseComponentInventory.Installed("location-search", "Search", self, "2.2", false)));
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.9.1"),
                Map.of("0.9.0", bundle("2.1", null), "0.9.1", bundle("2.2", java.util.List.of("2.1")))));
        var service = bundledService(inventory);
        var before = service.getStatus(false);
        assertEquals(Status.UPDATE_AVAILABLE, before.status());
        assertEquals("0.9.0", before.installedReleaseVersion());
        assertEquals(ReleaseComponentUpdate.Compatibility.SUPPORTED, before.components().get(1).compatibility());
        var after = service.getStatus(false);
        assertEquals(Status.UP_TO_DATE, after.status());
        assertEquals("0.9.1", after.installedReleaseVersion());
        assertEquals(before.installedBuildId(), after.installedBuildId());
        assertNotEquals(before.installedComponentKey(), after.installedComponentKey());
    }

    @Test
    void providerManagedAndDisabledComponentsDoNotCreateOffers() throws Exception {
        var inventory = mock(ReleaseComponentInventory.class);
        var provider = com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management.PROVIDER_MANAGED;
        var disabled = com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management.DISABLED;
        when(inventory.snapshot()).thenReturn(java.util.List.of(
                new ReleaseComponentInventory.Installed("location-search", "Search", provider, "2.1", false),
                new ReleaseComponentInventory.Installed("brouter", "Routing", disabled, null, false)));
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.9.1"),
                Map.of("0.9.1", bundle("2.2", null))));
        assertEquals(Status.UP_TO_DATE, bundledService(inventory).getStatus(false).status());
    }

    @Test
    void unknownComponentCannotClaimCurrentOrIncompatible() throws Exception {
        var inventory = mock(ReleaseComponentInventory.class);
        var self = com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management.SELF_MANAGED;
        when(inventory.snapshot()).thenReturn(java.util.List.of(new ReleaseComponentInventory.Installed("location-search", "Search", self, "custom", true)));
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.9.1"),
                Map.of("0.9.1", bundle("2.2", java.util.List.of()))));
        var result = bundledService(inventory).getStatus(false);
        assertEquals(Status.UNKNOWN, result.status());
        assertEquals(ReleaseComponentUpdate.Compatibility.UNKNOWN, result.components().get(1).compatibility());
        assertTrue(result.checkFailed());
    }

    @Test
    void unknownOwnershipDoesNotBlockApplicationUpdateCompletion() throws Exception {
        var inventory = mock(ReleaseComponentInventory.class);
        when(inventory.snapshot()).thenReturn(java.util.List.of(new ReleaseComponentInventory.Installed(
                "location-search", "Search", ReleaseUpdateProperties.Management.UNKNOWN, null, false)));
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.9.1"),
                Map.of("0.9.1", bundle("2.2", null))));
        var result = bundledService(inventory).getStatus(false);
        assertEquals(Status.UP_TO_DATE, result.status());
        assertEquals("0.9.1", result.installedReleaseVersion());
        assertEquals(ReleaseComponentUpdate.Status.UNKNOWN, result.components().get(1).status());
        assertEquals(ReleaseComponentUpdate.Compatibility.UNKNOWN, result.components().get(1).compatibility());
        assertFalse(result.checkFailed());
    }

    @Test
    void compatibilityExclusionIsExplicitAndNewerBuildIsNotDowngraded() throws Exception {
        var inventory = mock(ReleaseComponentInventory.class);
        var self = com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management.SELF_MANAGED;
        when(inventory.snapshot()).thenReturn(java.util.List.of(new ReleaseComponentInventory.Installed("location-search", "Search", self, "2.1", false)))
                .thenReturn(java.util.List.of(new ReleaseComponentInventory.Installed("location-search", "Search", self, "2.3", false)));
        when(client.fetch(anyString())).thenReturn(new ReleaseFeedClient.Feed(1, Map.of("stable", "0.9.1"),
                Map.of("0.9.0", bundle("2.1", null), "0.9.1", bundle("2.2", java.util.List.of()), "0.10.0", bundle("2.3", null))));
        var service = bundledService(inventory);
        assertEquals(ReleaseComponentUpdate.Compatibility.UPDATE_REQUIRED, service.getStatus(false).components().get(1).compatibility());
        assertEquals(Status.UP_TO_DATE, service.getStatus(false).status());
    }

}
