package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties;
import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.x8ing.mtl.server.mtlserver.web.services.info.release.ReleaseUpdateResponse.Status;

@Slf4j
@Service
public class ReleaseUpdateService {
    private static final Duration CHECK_INTERVAL = Duration.ofHours(24);
    private static final Duration FAILURE_RETRY_INTERVAL = Duration.ofMinutes(15);
    private static final Duration MANUAL_CHECK_COOLDOWN = Duration.ofMinutes(1);
    private static final int LOG_MESSAGE_LIMIT = 160;
    private final ReleaseUpdateProperties properties;
    private final DockerImageInfoService imageInfo;
    private final ServerIdentityService identity;
    private final ReleaseFeedClient client;
    private final ReleaseComponentInventory inventory;
    private Instant inventoryAttemptedAt;
    private final Clock clock;
    private final Executor executor;
    private ReleaseFeedClient.Feed feed;
    private Instant checkedAt;
    private Instant attemptedAt;
    private boolean checking;
    private boolean checkFailed;

    @Autowired
    public ReleaseUpdateService(ReleaseUpdateProperties properties, DockerImageInfoService imageInfo,
                                ServerIdentityService identity, ReleaseFeedClient client,
                                ReleaseComponentInventory inventory) {
        this(properties, imageInfo, identity, client, Clock.systemUTC(),
                Executors.newSingleThreadExecutor(Thread.ofVirtual().name("release-update-", 0).factory()), inventory);
    }

    ReleaseUpdateService(ReleaseUpdateProperties properties, DockerImageInfoService imageInfo,
                         ServerIdentityService identity, ReleaseFeedClient client,
                         Clock clock, Executor executor, ReleaseComponentInventory inventory) {
        this.properties = properties;
        this.imageInfo = imageInfo;
        this.identity = identity;
        this.client = client;
        this.clock = clock;
        this.executor = executor;
        this.inventory = inventory;
    }

    public synchronized ReleaseUpdateResponse getStatus(boolean force) {
        Instant now = clock.instant();
        Duration interval = force ? MANUAL_CHECK_COOLDOWN : checkFailed ? FAILURE_RETRY_INTERVAL : CHECK_INTERVAL;
        boolean feedDue = attemptedAt == null || !now.isBefore(attemptedAt.plus(interval));
        boolean inventoryDue = inventory != null && (inventoryAttemptedAt == null
                                                     || !now.isBefore(inventoryAttemptedAt.plus(MANUAL_CHECK_COOLDOWN)));
        if (properties.isEnabled() && !checking && (feedDue || inventoryDue)) {
            checking = true;
            if (feedDue) attemptedAt = now;
            if (inventoryDue) inventoryAttemptedAt = now;
            try {
                executor.execute(() -> refresh(feedDue, force));
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                checking = false;
                checkFailed = true;
                log.info("MTL Explorer release check unavailable: background worker stopped ({})", exception.getClass().getSimpleName());
            }
        }
        String buildId = imageInfo.getImageInfo().getVersion();
        String latestVersion = feed == null ? null : feed.channels().get(properties.getChannel());
        var latest = latestVersion == null ? null : feed.releases().get(latestVersion);
        var installed = new java.util.ArrayList<ReleaseComponentInventory.Installed>();
        installed.add(new ReleaseComponentInventory.Installed("app", "MTL Explorer", Management.SELF_MANAGED, buildId, false));
        if (inventory != null) installed.addAll(inventory.snapshot());
        String installedVersion = feed == null ? null : feed.releases().entrySet().stream()
                .filter(entry -> matchesBundle(entry.getValue(), installed))
                .map(Map.Entry::getKey).max(ReleaseVersion::compare).orElse(null);
        List<ReleaseComponentUpdate> components = installed.stream()
                .map(component -> componentUpdate(component, latest, latestVersion)).toList();
        Status status;
        if (!properties.isEnabled()) status = Status.DISABLED;
        else if (feed == null) status = checking ? Status.CHECKING : checkFailed ? Status.UNAVAILABLE : Status.UNKNOWN;
        else if (latest == null) status = Status.UNKNOWN;
        else if (components.stream().anyMatch(component -> component.status() == ReleaseComponentUpdate.Status.UPDATE_AVAILABLE))
            status = Status.UPDATE_AVAILABLE;
        else if (components.stream().anyMatch(component -> component.management() == Management.SELF_MANAGED
                                                           && component.targetBuildId() != null && component.status() == ReleaseComponentUpdate.Status.UNKNOWN))
            status = Status.UNKNOWN;
        else status = Status.UP_TO_DATE;
        String componentKey = installed.stream().filter(component -> component.management() == Management.SELF_MANAGED)
                .map(component -> component.id() + "=" + Objects.toString(component.buildId(), "?"))
                .sorted().collect(java.util.stream.Collectors.joining(";"));
        boolean failed = checkFailed || installed.stream().anyMatch(ReleaseComponentInventory.Installed::checkFailed);
        return new ReleaseUpdateResponse(status, identity.getServerId(), properties.getChannel(), installedVersion,
                buildId, latestVersion, latest == null ? null : latest.buildId(), componentKey, components,
                latest == null ? null : latest.summary(), latest == null ? null : latest.releaseNotesUrl(),
                latest == null ? null : latest.updateGuideUrl(), latest == null ? null : latest.publishedOn(),
                checkedAt, checking, failed);
    }

    private boolean matchesBundle(ReleaseFeedClient.Release release, List<ReleaseComponentInventory.Installed> installed) {
        return installed.stream().filter(component -> component.management() == Management.SELF_MANAGED)
                .allMatch(component -> {
                    var artifact = release.artifacts().get(component.id());
                    return artifact == null || Objects.equals(artifact.buildId(), component.buildId());
                });
    }

    private ReleaseComponentUpdate componentUpdate(ReleaseComponentInventory.Installed installed,
                                                   ReleaseFeedClient.Release latest, String latestVersion) {
        var artifact = latest == null ? null : latest.artifacts().get(installed.id());
        var compatibility = ReleaseComponentUpdate.Compatibility.UNKNOWN;
        ReleaseComponentUpdate.Status status;
        if (installed.management() == Management.PROVIDER_MANAGED) status = ReleaseComponentUpdate.Status.PROVIDER_MANAGED;
        else if (installed.management() == Management.DISABLED) status = ReleaseComponentUpdate.Status.DISABLED;
        else if (installed.management() == Management.UNKNOWN) status = ReleaseComponentUpdate.Status.UNKNOWN;
        else if (artifact == null) status = ReleaseComponentUpdate.Status.UNTRACKED;
        else if (Objects.equals(installed.buildId(), artifact.buildId())) {
            status = ReleaseComponentUpdate.Status.CURRENT;
            compatibility = ReleaseComponentUpdate.Compatibility.SUPPORTED;
        } else {
            String knownVersion = feed.releases().entrySet().stream().filter(entry -> {
                var known = entry.getValue().artifacts().get(installed.id());
                return known != null && Objects.equals(known.buildId(), installed.buildId());
            }).map(Map.Entry::getKey).max(ReleaseVersion::compare).orElse(null);
            status = knownVersion == null ? ReleaseComponentUpdate.Status.UNKNOWN
                    : ReleaseVersion.compare(knownVersion, latestVersion) > 0 ? ReleaseComponentUpdate.Status.CURRENT
                    : ReleaseComponentUpdate.Status.UPDATE_AVAILABLE;
            if (artifact.compatibleBuildIds() != null && installed.buildId() != null) {
                if (artifact.compatibleBuildIds().contains(installed.buildId())) compatibility = ReleaseComponentUpdate.Compatibility.SUPPORTED;
                else if (status == ReleaseComponentUpdate.Status.UPDATE_AVAILABLE) compatibility = ReleaseComponentUpdate.Compatibility.UPDATE_REQUIRED;
            }
        }
        return new ReleaseComponentUpdate(installed.id(), installed.label(), installed.management(), installed.buildId(),
                artifact == null ? null : artifact.buildId(), status, compatibility, installed.checkFailed());
    }

    private void refresh(boolean feedDue, boolean force) {
        long start = System.nanoTime();
        try {
            if (feedDue) refreshFeed(start);
            if (inventory != null) inventory.refresh(clock.instant(), force);
        } finally {
            synchronized (this) {
                checking = false;
            }
        }
    }

    private void refreshFeed(long start) {
        log.info("Checking MTL Explorer releases: channel={}", properties.getChannel());
        try {
            var fetched = client.fetch(properties.getFeedUrl());
            synchronized (this) {
                feed = fetched;
                checkedAt = clock.instant();
                checkFailed = false;
            }
            log.info("MTL Explorer release check completed: releases={}, channel={}, target={}, durationMs={}",
                    fetched.releases().size(), properties.getChannel(), fetched.channels().get(properties.getChannel()),
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
        } catch (Exception exception) {
            synchronized (this) {
                checkFailed = true;
            }
            String message = String.valueOf(exception.getMessage()).replaceAll("[\\r\\n]+", " ");
            if (message.length() > LOG_MESSAGE_LIMIT) message = message.substring(0, LOG_MESSAGE_LIMIT);
            log.info("MTL Explorer release check unavailable: channel={}, type={}, message={}, durationMs={}",
                    properties.getChannel(), exception.getClass().getSimpleName(), message,
                    Duration.ofNanos(System.nanoTime() - start).toMillis());
            log.debug("MTL Explorer release check failure", exception);
        }
    }

    @PreDestroy
    public void close() {
        if (executor instanceof ExecutorService service) service.shutdownNow();
        log.info("MTL Explorer release checker stopped");
    }
}
