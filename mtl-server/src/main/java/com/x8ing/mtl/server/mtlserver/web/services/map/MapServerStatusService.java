package com.x8ing.mtl.server.mtlserver.web.services.map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Fetches the map-server status with a simple TTL cache.
 * If the map-server is unreachable, returns a safe fallback DTO
 * so callers never get an exception.
 */
@Slf4j
@Service
public class MapServerStatusService {

    private static final long CACHE_TTL_SECONDS = 10;

    private final RestClient restClient;
    private final MapServerProperties properties;

    private MapServerStatusDto cachedStatus;
    private Instant cacheExpiry = Instant.EPOCH;

    public MapServerStatusService(MapServerProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public MapServerStatusDto getStatus() {
        if (Instant.now().isBefore(cacheExpiry) && cachedStatus != null) {
            return cachedStatus;
        }
        try {
            cachedStatus = restClient.get()
                    .uri(properties.getStatusUrl())
                    .retrieve()
                    .body(MapServerStatusDto.class);
            cacheExpiry = Instant.now().plusSeconds(CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.debug("Map server unreachable at {}: {}", properties.getStatusUrl(), e.getMessage());
            cachedStatus = MapServerStatusDto.unreachable();
            // Short TTL so we retry quickly when the server comes up
            cacheExpiry = Instant.now().plusSeconds(3);
        }
        return cachedStatus;
    }
}
