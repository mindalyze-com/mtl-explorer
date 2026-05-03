package com.x8ing.mtl.server.mtlserver.web.services.map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chooses whether PMTiles requests should use the local docker-maps sidecar or
 * the hosted PMTiles upstream. The decision is lazy on first map use and is
 * refreshed in the background only while tile traffic was seen recently.
 */
@Slf4j
@Service
public class MapUpstreamResolver {

    private record CachedDecision(MapUpstreamSource source, Instant expiresAt) {
    }

    private final MapServerProperties properties;
    private final RestClient probeClient;
    private final AtomicLong lastTileRequestEpochMs = new AtomicLong(0);

    private volatile CachedDecision cachedDecision;

    public MapUpstreamResolver(MapServerProperties properties) {
        this.properties = properties;
        this.probeClient = buildProbeClient(properties);
        // Expired placeholder: first use probes before falling back to PUBLIC.
        this.cachedDecision = new CachedDecision(MapUpstreamSource.PUBLIC, Instant.EPOCH);
    }

    public void markTileRequestActivity() {
        lastTileRequestEpochMs.set(System.currentTimeMillis());
    }

    public MapUpstream resolveUpstream() {
        CachedDecision current = cachedDecision;
        if (Instant.now().isBefore(current.expiresAt())) {
            return upstreamFor(current.source());
        }
        return upstreamFor(probeAndCache(false));
    }

    public MapUpstream upstreamFor(MapUpstreamSource source) {
        String baseUrl = source == MapUpstreamSource.LOCAL
                ? properties.getTileUpstreamUrl()
                : properties.getPublicUpstreamUrl();
        return new MapUpstream(source, MapUrlUtils.trimTrailingSlashes(baseUrl));
    }

    @Scheduled(fixedDelayString = "#{@mapServerProperties.localProbeIntervalSeconds * 1000L}")
    public void refreshLocalAvailabilityWhenActive() {
        if (!hasRecentTileActivity()) {
            log.debug("No recent map tile activity, skipping local map sidecar probe");
            return;
        }
        probeAndCache(true);
    }

    private synchronized MapUpstreamSource probeAndCache(boolean force) {
        CachedDecision current = cachedDecision;
        if (!force && Instant.now().isBefore(current.expiresAt())) {
            return current.source();
        }

        MapUpstreamSource source = isLocalSidecarHealthy()
                ? MapUpstreamSource.LOCAL
                : MapUpstreamSource.PUBLIC;
        cachedDecision = new CachedDecision(source, Instant.now().plusSeconds(properties.getUpstreamDecisionCacheTtlSeconds()));
        if (current.source() != source) {
            log.info("Map upstream changed from {} to {}", current.source().cacheValue(), source.cacheValue());
        }
        log.debug("Resolved map upstream to {}", source.cacheValue());
        return source;
    }

    public boolean hasRecentTileActivity() {
        long lastActivity = lastTileRequestEpochMs.get();
        if (lastActivity == 0) {
            return false;
        }
        long activeWindowMs = Duration.ofSeconds(properties.getLocalProbeActiveWindowSeconds()).toMillis();
        return System.currentTimeMillis() - lastActivity <= activeWindowMs;
    }

    private boolean isLocalSidecarHealthy() {
        String probeUrl = localProbeUrl();
        if (probeUrl == null || probeUrl.isBlank()) {
            return false;
        }

        try {
            return probeClient.get()
                    .uri(probeUrl)
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Local map sidecar probe failed at {}: {}", probeUrl, e.getMessage());
            return false;
        }
    }

    private String localProbeUrl() {
        if (properties.getLocalProbeUrl() != null && !properties.getLocalProbeUrl().isBlank()) {
            return properties.getLocalProbeUrl();
        }
        String localUpstream = MapUrlUtils.trimTrailingSlashes(properties.getTileUpstreamUrl());
        if (localUpstream == null || localUpstream.isBlank()) {
            return null;
        }
        return localUpstream + "/health";
    }

    private static RestClient buildProbeClient(MapServerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.getLocalProbeTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
