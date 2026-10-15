package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties;
import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapProxyConstants;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.UpstreamBuildVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Update discovery uses its own bounded requests; status reads never wait for a service.
 */
@Slf4j
@Component
public class ReleaseComponentInventory {
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);
    private static final Duration FAILURE_RETRY_INTERVAL = Duration.ofMinutes(15);
    private static final int LOG_MESSAGE_LIMIT = 160;
    private final ReleaseUpdateProperties properties;
    private final PlannerProperties planner;
    private final LocationSearchProperties search;
    private final MapServerProperties maps;
    private final ReleaseFeedClient client;
    private final ServerIdentityService identity;
    private final UpstreamBuildVersionService buildVersion;
    private final Map<String, Observation> observations = new HashMap<>();

    public ReleaseComponentInventory(ReleaseUpdateProperties properties, PlannerProperties planner,
                                     LocationSearchProperties search, MapServerProperties maps, ReleaseFeedClient client,
                                     ServerIdentityService identity, UpstreamBuildVersionService buildVersion) {
        this.properties = properties;
        this.planner = planner;
        this.search = search;
        this.maps = maps;
        this.client = client;
        this.identity = identity;
        this.buildVersion = buildVersion;
    }

    public record Observation(String buildId, Instant attemptedAt, boolean checkFailed) {
    }

    public record Installed(String id, String label, Management management, String buildId, boolean checkFailed) {
    }

    private record Source(String id, String label, String url, boolean enabled) {
    }

    private List<Source> sources() {
        return List.of(new Source("brouter", "Routing", planner.getStatusUrl(), planner.isEnabled()),
                new Source("location-search", "Location search", search.getStatusUrl(), search.isEnabled()),
                new Source("map-server", "Maps", maps.getStatusUrl(), true));
    }

    private ReleaseUpdateProperties.ManagedComponent configured(String id) {
        return properties.component(id);
    }

    private Management management(Source source) {
        if (!source.enabled) return Management.DISABLED;
        var configured = configured(source.id);
        return configured == null || configured.getManagement() == null ? Management.UNKNOWN : configured.getManagement();
    }

    public synchronized List<Installed> snapshot() {
        return sources().stream().map(source -> {
            Observation observation = observations.get(source.id);
            return new Installed(source.id, source.label, management(source), observation == null ? null : observation.buildId,
                    observation != null && observation.checkFailed);
        }).toList();
    }

    private String statusUrl(Source source) {
        var configured = configured(source.id);
        String url = configured.getStatusUrl() == null ? source.url : configured.getStatusUrl();
        if (!"location-search".equals(source.id)) return url;
        var builder = UriComponentsBuilder.fromUriString(url)
                .replaceQueryParam(MapProxyConstants.UPSTREAM_AUTH_VERSION_PARAM, buildVersion.getVersion())
                .replaceQueryParam(MapProxyConstants.UPSTREAM_AUTH_SERVER_ID_PARAM, identity.getServerId());
        String build = buildVersion.getBuild();
        if (build != null && !build.isBlank()) builder.replaceQueryParam(MapProxyConstants.UPSTREAM_BUILD_PARAM, build);
        return builder.build().toUriString();
    }

    public void refresh(Instant now, boolean force) {
        if (!properties.isEnabled()) return;
        for (Source source : sources()) {
            if (management(source) != Management.SELF_MANAGED) continue;
            Observation previous;
            synchronized (this) {
                previous = observations.get(source.id);
            }
            Duration interval = !force && previous != null && previous.checkFailed ? FAILURE_RETRY_INTERVAL : REFRESH_INTERVAL;
            if (previous != null && now.isBefore(previous.attemptedAt.plus(interval))) continue;
            try {
                String build = client.fetchBuildId(statusUrl(source));
                synchronized (this) {
                    observations.put(source.id, new Observation(build, now, false));
                }
                log.debug("MTL Explorer update component checked: component={}, build={}", source.id, build);
            } catch (Exception exception) {
                synchronized (this) {
                    observations.put(source.id, new Observation(previous == null ? null : previous.buildId, now, true));
                }
                String message = String.valueOf(exception.getMessage()).replaceAll("[\\r\\n]+", " ");
                if (message.length() > LOG_MESSAGE_LIMIT) message = message.substring(0, LOG_MESSAGE_LIMIT);
                log.info("MTL Explorer update component check unavailable: component={}, type={}, message={}",
                        source.id, exception.getClass().getSimpleName(), message);
                log.debug("MTL Explorer update component check failure: component={}", source.id, exception);
            }
        }
    }
}
