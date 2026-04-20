package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Sends a one-shot PUT to the map-server kickoff URL on application startup.
 * <p>
 * The map-server Python orchestrator blocks its download worker until it receives
 * a PUT to /kickoff/{scope} (routed through nginx to the internal kickoff server on 8082).
 * The scope (demo or prod) tells the orchestrator which directory to operate on.
 * Without this signal the map-server waits indefinitely.
 * <p>
 * The kickoff is skipped silently when:
 * - tile-mode is not "local"
 * - kickoff-url is blank (e.g. local dev without a map-server sidecar)
 * - the map-server is unreachable (already downloading / already done — both fine)
 */
@Slf4j
@Service
public class MapServerKickoffService {

    private final MapServerProperties properties;
    private final MtlAppProperties appProperties;

    public MapServerKickoffService(MapServerProperties properties, MtlAppProperties appProperties) {
        this.properties = properties;
        this.appProperties = appProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void kickoffMapDownload() {
        if (!"local".equalsIgnoreCase(properties.getTileMode())) {
            log.debug("Map tile-mode is '{}' — skipping map-server kickoff", properties.getTileMode());
            return;
        }
        String upstreamUrl = properties.getTileUpstreamUrl();
        if (upstreamUrl == null || upstreamUrl.isBlank()) {
            log.debug("No tile-upstream-url configured — skipping map-server kickoff");
            throw new RuntimeException("No tile-upstream-url configured — skipping map-server kickoff");
        }

        String scope = appProperties.isDemoMode() ? "demo" : "prod";
        String kickoffUrl = upstreamUrl.stripTrailing().replaceAll("/+$", "") + "/kickoff/" + scope;
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
