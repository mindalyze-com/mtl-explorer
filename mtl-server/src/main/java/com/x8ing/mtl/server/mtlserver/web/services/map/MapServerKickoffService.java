package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends a one-shot PUT to the map-server kickoff URL on application startup.
 * <p>
 * The map-server Python orchestrator blocks its download worker until it receives
 * a PUT to /kickoff/{scope} (routed through nginx to the internal kickoff server on 8082).
 * The prod scope tells the orchestrator which directory to operate on.
 * Without this signal the map-server waits indefinitely.
 * <p>
 * The kickoff is skipped silently when:
 * - tile-mode is not "local"
 * - kickoff-url is blank (e.g. local dev without a map-server sidecar)
 * - the map-server is unreachable (already downloading / already done — both fine)
 */
@Slf4j
@Service
@JsonPropertyOrder({
        "properties",
        "upstreamResolver"
})
public class MapServerKickoffService {

    private final MapServerProperties properties;
    private final MapUpstreamResolver upstreamResolver;

    public MapServerKickoffService(MapServerProperties properties,
                                   MapUpstreamResolver upstreamResolver) {
        this.properties = properties;
        this.upstreamResolver = upstreamResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void kickoffMapDownload() {
        sendKickoff();
    }

    @Scheduled(fixedDelayString = "#{@mapServerProperties.localProbeIntervalSeconds * 1000L}")
    public void kickoffWhenLocalSidecarAppears() {
        if (!upstreamResolver.hasRecentTileActivity()) {
            return;
        }
        if (upstreamResolver.resolveUpstream().source() == MapUpstreamSource.LOCAL) {
            sendKickoff();
        }
    }

    private void sendKickoff() {
        if (!MapProxyConstants.TILE_MODE_LOCAL.equalsIgnoreCase(properties.getTileMode())) {
            log.debug("Map tile-mode is '{}' — skipping map-server kickoff", properties.getTileMode());
            return;
        }
        String upstreamUrl = properties.getTileUpstreamUrl();
        if (upstreamUrl == null || upstreamUrl.isBlank()) {
            log.debug("No tile-upstream-url configured — skipping map-server kickoff");
            return;
        }
        if (upstreamResolver.resolveUpstream().source() != MapUpstreamSource.LOCAL) {
            log.debug("No local map sidecar available — using hosted PMTiles and skipping map-server kickoff");
            return;
        }

        String scope = MapProxyConstants.SCOPE_PROD;
        String kickoffUrl = MapUrlUtils.trimTrailingSlashes(upstreamUrl) + "/kickoff/" + scope;
        log.info("Sending map-server kickoff to {} (scope: {})", kickoffUrl, scope);
        try {
            RestClient.create()
                    .put()
                    .uri(kickoffUrl)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Map-server kickoff accepted — tiles will be prepared in the background. scope={}", scope);
        } catch (Exception e) {
            // Not fatal: map might already be ready, or it's unreachable in local dev
            log.warn("Map-server kickoff failed ({}): {} — tiles may not be available until manually triggered",
                    kickoffUrl, e.getMessage());
        }
    }
}
