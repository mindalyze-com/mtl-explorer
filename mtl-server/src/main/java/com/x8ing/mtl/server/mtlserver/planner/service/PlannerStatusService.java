package com.x8ing.mtl.server.mtlserver.planner.service;

import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin proxy to the BRouter sidecar's {@code /status} endpoint. The sidecar
 * reports segment download progress (how many 5°×5° rd5 files are cached, which
 * are currently downloading, etc.). Only purpose of this service is to shield
 * the frontend from a direct connection to the sidecar.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
public class PlannerStatusService {

    private final PlannerProperties properties;
    private final RestClient restClient = RestClient.builder().build();

    public PlannerStatusService(PlannerProperties properties) {
        this.properties = properties;
    }

    /**
     * @return BRouter sidecar status as a generic map, or a synthetic
     * "unavailable" map when the sidecar is down.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchStatus() {
        try {
            Map<String, Object> raw = restClient.get()
                    .uri(properties.getStatusUrl())
                    .retrieve()
                    .body(Map.class);
            return raw == null ? Map.of("available", false, "reason", "empty-response") : raw;
        } catch (Exception e) {
            log.debug("BRouter sidecar status unavailable: {}", e.toString());
            return Map.of("available", false, "reason", e.getMessage() == null ? "unreachable" : e.getMessage());
        }
    }
}
