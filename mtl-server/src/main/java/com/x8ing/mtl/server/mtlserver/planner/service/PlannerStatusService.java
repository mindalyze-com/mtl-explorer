package com.x8ing.mtl.server.mtlserver.planner.service;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.dto.BRouterStatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Thin proxy to the BRouter sidecar's {@code /status} endpoint. The sidecar
 * reports segment download progress (how many 5°×5° rd5 files are cached, which
 * are currently downloading, etc.). Only purpose of this service is to shield
 * the frontend from a direct connection to the sidecar.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
@JsonPropertyOrder({
        "properties",
        "restClient"
})
public class PlannerStatusService {

    private final PlannerProperties properties;
    private final RestClient restClient;

    public PlannerStatusService(PlannerProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.getBrouterTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /**
     * @return BRouter sidecar status, or a synthetic "unavailable" status when
     * the sidecar is down.
     */
    public BRouterStatusDto fetchStatus() {
        try {
            BRouterStatusDto raw = restClient.get()
                    .uri(properties.getStatusUrl())
                    .retrieve()
                    .body(BRouterStatusDto.class);
            return raw == null ? BRouterStatusDto.unavailable("empty-response") : raw;
        } catch (Exception e) {
            log.debug("BRouter sidecar status unavailable: {}", e.toString());
            return BRouterStatusDto.unavailable(e.getMessage() == null ? "unreachable" : e.getMessage());
        }
    }
}
