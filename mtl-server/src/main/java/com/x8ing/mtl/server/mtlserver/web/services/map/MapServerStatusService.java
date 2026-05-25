package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Fetches the map-server status with a simple TTL cache.
 * If the map-server is unreachable, returns a safe fallback DTO
 * so callers never get an exception.
 */
@Slf4j
@Service
@JsonPropertyOrder({
        "restClient",
        "properties",
        "upstreamResolver",
        "statusRefreshLock",
        "cachedStatus",
        "cacheExpiry",
        "refreshInProgress"
})
public class MapServerStatusService {

    private static final long CACHE_TTL_SECONDS = 10;
    private static final long CACHE_RETRY_TTL_SECONDS = 3;

    @JsonPropertyOrder({
            "status",
            "ttlSeconds"
    })
    private record LocalStatusRefresh(MapServerStatusDto status, long ttlSeconds) {
    }

    private final RestClient restClient;
    private final MapServerProperties properties;
    private final MapUpstreamResolver upstreamResolver;
    private final Object statusRefreshLock = new Object();

    private volatile MapServerStatusDto cachedStatus;
    private volatile Instant cacheExpiry = Instant.EPOCH;
    private boolean refreshInProgress;

    public MapServerStatusService(MapServerProperties properties, MapUpstreamResolver upstreamResolver) {
        this.properties = properties;
        this.upstreamResolver = upstreamResolver;
        this.restClient = buildStatusClient(properties);
    }

    private static RestClient buildStatusClient(MapServerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.getLocalProbeTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public MapServerStatusDto getStatus() {
        if (upstreamResolver.resolveUpstream().source() == MapUpstreamSource.PUBLIC) {
            return MapServerStatusDto.publicFallback(publicArchiveId());
        }
        return getLocalStatus();
    }

    public String archiveIdFor(MapUpstreamSource source) {
        if (source == MapUpstreamSource.PUBLIC) {
            return publicArchiveId();
        }
        MapServerStatusDto status = getLocalStatus();
        if (status.getArchiveId() != null && !status.getArchiveId().isBlank()) {
            return status.getArchiveId();
        }
        return localArchiveId();
    }

    private MapServerStatusDto getLocalStatus() {
        MapServerStatusDto current = cachedStatus;
        if (isCacheFresh(current, Instant.now())) {
            return current;
        }

        synchronized (statusRefreshLock) {
            current = cachedStatus;
            Instant now = Instant.now();
            if (isCacheFresh(current, now)) {
                return current;
            }
            if (refreshInProgress) {
                if (current != null) {
                    return current;
                }
                return waitForInitialStatus();
            }
            refreshInProgress = true;
            if (current != null) {
                cacheExpiry = now.plusSeconds(CACHE_TTL_SECONDS);
            }
        }

        LocalStatusRefresh refresh = refreshLocalStatus();
        synchronized (statusRefreshLock) {
            cachedStatus = refresh.status();
            cacheExpiry = Instant.now().plusSeconds(refresh.ttlSeconds());
            refreshInProgress = false;
            statusRefreshLock.notifyAll();
            return cachedStatus;
        }
    }

    private LocalStatusRefresh refreshLocalStatus() {
        try {
            MapServerStatusDto status = restClient.get()
                    .uri(properties.getStatusUrl())
                    .retrieve()
                    .body(MapServerStatusDto.class);
            applyLocalMetadata(status);
            return new LocalStatusRefresh(status, CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.debug("Map server unreachable at {}: {}", properties.getStatusUrl(), e.getMessage());
            return new LocalStatusRefresh(MapServerStatusDto.unreachable(), CACHE_RETRY_TTL_SECONDS);
        }
    }

    private MapServerStatusDto waitForInitialStatus() {
        while (refreshInProgress && cachedStatus == null) {
            try {
                statusRefreshLock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MapServerStatusDto.unreachable();
            }
        }
        return cachedStatus == null ? MapServerStatusDto.unreachable() : cachedStatus;
    }

    private boolean isCacheFresh(MapServerStatusDto status, Instant now) {
        return status != null && now.isBefore(cacheExpiry);
    }

    private void applyLocalMetadata(MapServerStatusDto status) {
        if (status == null) {
            return;
        }
        status.setTileSource(MapProxyConstants.SOURCE_LOCAL);
        if (status.getArchiveId() == null || status.getArchiveId().isBlank()) {
            status.setArchiveId(localArchiveId());
        }
    }

    private String publicArchiveId() {
        return nonBlank(properties.getPublicArchiveId(), MapProxyConstants.UNKNOWN_ARCHIVE_ID);
    }

    private String localArchiveId() {
        return nonBlank(properties.getLocalArchiveId(), MapProxyConstants.UNKNOWN_ARCHIVE_ID);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
