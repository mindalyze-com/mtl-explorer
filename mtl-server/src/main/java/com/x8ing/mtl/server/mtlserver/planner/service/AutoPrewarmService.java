package com.x8ing.mtl.server.mtlserver.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Triggers on-demand (lazy) segment downloads on the BRouter sidecar.
 *
 * <p>When a routing request fails because a {@code .rd5} segment file is missing,
 * the controller calls {@link #triggerPrewarmForBbox} which POSTs the affected
 * bounding box to the sidecar's {@code /prewarm-urgent} endpoint. The sidecar
 * then downloads the needed tiles with highest priority.
 *
 * <p>There is no startup prewarm — segments are downloaded purely on demand.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
public class AutoPrewarmService {

    private final PlannerProperties properties;
    private final RestClient restClient = RestClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AutoPrewarmService(PlannerProperties properties) {
        this.properties = properties;
    }

    /**
     * Trigger an on-demand download for the segments covering the given bounding box.
     * Called from the controller when a routing request fails because a segment is
     * missing. The sidecar downloads the needed tile(s) with urgent priority.
     *
     * <p>Swallows all errors so a sidecar glitch never breaks the route endpoint.
     */
    public void triggerPrewarmForBbox(double minLng, double minLat, double maxLng, double maxLat) {
        String url = properties.getStatusUrl().replaceFirst("/status$", "/prewarm-urgent");
        try {
            postPrewarm(url, minLng, minLat, maxLng, maxLat);
            log.info("On-demand segment download triggered for bbox=[{},{},{},{}]", minLng, minLat, maxLng, maxLat);
        } catch (Exception e) {
            log.warn("On-demand segment download failed for bbox=[{},{},{},{}]: {}", minLng, minLat, maxLng, maxLat, e.toString());
        }
    }

    private void postPrewarm(String url, double minLng, double minLat, double maxLng, double maxLat)
            throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(Map.of(
                "minLng", minLng, "minLat", minLat,
                "maxLng", maxLng, "maxLat", maxLat
        ));
        log.debug("POST {} body={}", url, json);
        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .toBodilessEntity();
    }
}
