package com.x8ing.mtl.server.mtlserver.planner.service;

import com.x8ing.mtl.server.mtlserver.planner.client.BRouterClient;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates multi-waypoint route computation. For each adjacent waypoint pair
 * it either returns a cached leg result or calls BRouter. Only instantiated when
 * the planner feature flag is on.
 *
 * <p>Caching key = (profile, from-lng, from-lat, to-lng, to-lat) with coordinates
 * snapped to {@value PlannerConstants#COORDINATE_SNAP_DECIMALS} decimals so minor
 * floating-point differences still hit. Bounded LRU with TTL.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
public class PlannerService {

    private final BRouterClient brouterClient;
    private final PlannerProperties properties;
    private final Map<String, CachedLeg> legCache;

    public PlannerService(BRouterClient brouterClient, PlannerProperties properties) {
        this.brouterClient = brouterClient;
        this.properties = properties;
        this.legCache = Collections.synchronizedMap(new LinkedHashMap<>(
                PlannerConstants.SEGMENT_CACHE_MAX_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedLeg> eldest) {
                return size() > PlannerConstants.SEGMENT_CACHE_MAX_SIZE;
            }
        });
    }

    /**
     * Compute the full route. Throws {@link IllegalArgumentException} on bad input.
     */
    public RouteResponseDto computeRoute(RouteRequestDto req) {
        List<WaypointDto> wps = req.getWaypoints();
        if (wps == null || wps.size() < 2) {
            throw new IllegalArgumentException("At least two waypoints are required");
        }
        if (wps.size() > PlannerConstants.MAX_WAYPOINTS) {
            throw new IllegalArgumentException("Too many waypoints (max " + PlannerConstants.MAX_WAYPOINTS + ")");
        }

        String profile = resolveProfile(req.getProfile());

        log.info("Computing route: {} waypoints, profile={}", wps.size(), profile);

        List<LegResultDto> legs = new ArrayList<>(wps.size() - 1);
        for (int i = 0; i < wps.size() - 1; i++) {
            WaypointDto a = wps.get(i);
            WaypointDto b = wps.get(i + 1);
            if (distanceMetersApprox(a, b) < PlannerConstants.MIN_WAYPOINT_DIST_M) {
                // Skip zero-length legs — don't waste a BRouter call.
                continue;
            }
            legs.add(getLegCached(a, b, profile));
        }

        RouteResponseDto resp = new RouteResponseDto();
        resp.setLegs(legs);
        resp.setProfile(profile);
        resp.setStats(aggregate(legs));
        return resp;
    }

    private LegResultDto getLegCached(WaypointDto a, WaypointDto b, String profile) {
        String key = cacheKey(a, b, profile);
        CachedLeg hit = legCache.get(key);
        if (hit != null && !hit.isExpired()) {
            log.debug("Leg cache HIT [{},{}]→[{},{}] profile={}", a.getLat(), a.getLng(), b.getLat(), b.getLng(), profile);
            LegResultDto copy = cloneLeg(hit.leg);
            copy.setCached(true);
            return copy;
        }
        log.debug("Leg cache MISS [{},{}]→[{},{}] profile={} — calling BRouter", a.getLat(), a.getLng(), b.getLat(), b.getLng(), profile);
        LegResultDto fresh = brouterClient.computeLeg(a, b, profile);
        fresh.setCached(false);
        legCache.put(key, new CachedLeg(cloneLeg(fresh), Instant.now()));
        log.debug("Leg computed [{},{}]→[{},{}] distance={}m ascent={}m", a.getLat(), a.getLng(), b.getLat(), b.getLng(),
                String.format("%.0f", fresh.getDistanceM()), String.format("%.0f", fresh.getAscentM()));
        return fresh;
    }

    private LegResultDto cloneLeg(LegResultDto src) {
        LegResultDto c = new LegResultDto();
        c.setCoordinates(src.getCoordinates());  // coords list is effectively immutable for our use
        c.setDistanceM(src.getDistanceM());
        c.setAscentM(src.getAscentM());
        c.setDescentM(src.getDescentM());
        c.setDurationSec(src.getDurationSec());
        c.setCached(src.isCached());
        return c;
    }

    private LiveStatsDto aggregate(List<LegResultDto> legs) {
        LiveStatsDto s = new LiveStatsDto();
        for (LegResultDto l : legs) {
            s.setDistanceM(s.getDistanceM() + l.getDistanceM());
            s.setAscentM(s.getAscentM() + l.getAscentM());
            s.setDescentM(s.getDescentM() + l.getDescentM());
            s.setDurationSec(s.getDurationSec() + l.getDurationSec());
            if (l.isCached()) s.setAnyLegCached(true);
        }
        s.setLegCount(legs.size());
        return s;
    }

    private String resolveProfile(String requested) {
        if (requested != null && properties.getProfiles().contains(requested)) {
            return requested;
        }
        return PlannerConstants.DEFAULT_PROFILE;
    }

    private String cacheKey(WaypointDto a, WaypointDto b, String profile) {
        return profile + "|" + snap(a.getLng()) + "," + snap(a.getLat())
               + "|" + snap(b.getLng()) + "," + snap(b.getLat());
    }

    private double snap(double v) {
        double factor = Math.pow(10, PlannerConstants.COORDINATE_SNAP_DECIMALS);
        return Math.round(v * factor) / factor;
    }

    /**
     * Haversine-free rough distance check, good enough for the "skip zero leg" guard.
     */
    private double distanceMetersApprox(WaypointDto a, WaypointDto b) {
        double dLat = (b.getLat() - a.getLat()) * 111_320.0;
        double meanLat = Math.toRadians((a.getLat() + b.getLat()) / 2);
        double dLng = (b.getLng() - a.getLng()) * 111_320.0 * Math.cos(meanLat);
        return Math.sqrt(dLat * dLat + dLng * dLng);
    }

    /**
     * Exposed for /status endpoint / tests.
     */
    public int getCacheSize() {
        return legCache.size();
    }

    private record CachedLeg(LegResultDto leg, Instant storedAt) {
        boolean isExpired() {
            return Duration.between(storedAt, Instant.now()).toMinutes() >= PlannerConstants.SEGMENT_CACHE_TTL_MIN;
        }
    }
}
