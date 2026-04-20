package com.x8ing.mtl.server.mtlserver.planner.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the route-planner feature. Bound from {@code mtl.planner.*} in
 * {@code application.yml} and normally overridden by the {@code MTL_PLANNER_*}
 * environment variables in docker-compose.
 *
 * <p>The feature is OFF by default. When {@link #enabled} is {@code false}, the entire
 * planner package (controller, services, client) is skipped via
 * {@code @ConditionalOnProperty} so the server stays lean for users who don't want it.
 */
@Data
@Component
@ConfigurationProperties(prefix = "mtl.planner")
public class PlannerProperties {

    /**
     * Master switch. When false, no planner endpoints are registered, no BRouter
     * connection is attempted, and the client will hide all planner UI (delivered
     * through /api/map/config).
     */
    private boolean enabled = false;

    /**
     * Base URL of the BRouter HTTP server (docker-internal). Only used when
     * {@link #enabled} is true. Default matches the compose service name.
     */
    private String brouterBaseUrl = "http://brouter:17777";

    /**
     * Connect/read timeout for BRouter calls, in milliseconds.
     */
    private int brouterTimeoutMs = 8000;

    /**
     * Routing profiles exposed to the client. Each name must correspond to a
     * {@code .brf} file shipped with BRouter.
     */
    private List<String> profiles = new ArrayList<>(List.of(
            "trekking", "fastbike", "hiking-mountain", "car-eco"
    ));

    /**
     * URL of the BRouter sidecar status endpoint. Returns segment download progress.
     */
    private String statusUrl = "http://brouter:17778/status";

}
