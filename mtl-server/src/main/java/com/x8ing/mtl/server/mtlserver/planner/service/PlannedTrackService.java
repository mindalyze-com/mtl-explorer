package com.x8ing.mtl.server.mtlserver.planner.service;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.planner.PlannerGeometryMetrics;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.*;
import com.x8ing.mtl.server.mtlserver.utils.GeoCoordinateUtils;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Persists planner maps (route, points of interest, or both) as GpsTrack rows flagged with
 * {@link GpsTrack.TRACK_SOURCE#PLANNED}. The row gets pre-set "terminal" state
 * values so no background job ever picks it up:
 * <ul>
 *   <li>{@link GpsTrack.LOAD_STATUS#SUCCESS} — skips load pipeline.</li>
 *   <li>{@link GpsTrack.DUPLICATE_CHECK_STATUS#EXCLUDED} — skips duplicate detector.</li>
 *   <li>{@link GpsTrack.EXPLORATION_STATUS#NOT_SCHEDULED} — skips exploration scorer.</li>
 *   <li>{@code activityTypeSource = USER_SET} — skips activity classifier.</li>
 * </ul>
 * The central SmartBaseFilter (migration 024) additionally hides PLANNED from
 * every default filter-driven listing.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "mtl.planner", name = "enabled", havingValue = "true")
@JsonPropertyOrder({
        "geometryFactory",
        "objectMapper",
        "gpsTrackRepository",
        "gpsTrackDataRepository",
        "gpsTrackEventRepository"
})
public class PlannedTrackService {

    private static final int SRID_WGS84 = 4326;
    private static final int PLANNER_ROUTE_SCHEMA_VERSION = 2;
    private static final String PLANNER_ROUTE_SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String PLANNER_ROUTE_LEGS_FIELD = "legs";
    private static final String PLANNER_ROUTE_STATS_FIELD = "stats";
    private static final String PLANNER_POINTS_OF_INTEREST_FIELD = "pointsOfInterest";
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID_WGS84);
    private final ObjectMapper objectMapper;

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataRepository gpsTrackDataRepository;
    private final GpsTrackEventRepository gpsTrackEventRepository;

    public PlannedTrackService(ObjectMapper objectMapper,
                               GpsTrackRepository gpsTrackRepository,
                               GpsTrackDataRepository gpsTrackDataRepository,
                               GpsTrackEventRepository gpsTrackEventRepository) {
        this.objectMapper = objectMapper;
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataRepository = gpsTrackDataRepository;
        this.gpsTrackEventRepository = gpsTrackEventRepository;
    }

    @Transactional
    public GpsTrack save(SavePlannedTrackDto dto) {
        long startedAt = System.nanoTime();
        if (dto == null) {
            throw new IllegalArgumentException("Plan is required");
        }
        List<PointOfInterestDto> pointsOfInterest = validatedPointsOfInterest(dto.getPointsOfInterest());
        List<LegResultDto> plannedLegs = nonEmptyLegs(dto.getLegs());
        List<double[]> coords = plannedLegs.isEmpty()
                ? dto.getCoordinates()
                : flattenLegCoordinates(plannedLegs);
        if (coords == null) coords = List.of();
        validateRouteCoordinates(coords);
        if (dto.getWaypoints() != null) {
            if (dto.getWaypoints().size() > PlannerConstants.MAX_WAYPOINTS) {
                throw new IllegalArgumentException("Too many waypoints");
            }
            for (WaypointDto waypoint : dto.getWaypoints()) {
                if (waypoint == null) throw new IllegalArgumentException("Waypoint is required");
                GeoCoordinateUtils.requireValidLatitude(waypoint.getLat());
                GeoCoordinateUtils.requireValidLongitude(waypoint.getLng());
            }
        }
        boolean hasRoute = coords.size() >= 2;
        if ((!hasRoute && !coords.isEmpty()) || (!hasRoute && pointsOfInterest.isEmpty())) {
            throw new IllegalArgumentException("A plan needs a route or at least one point of interest");
        }
        log.info("Saving plan: routePoints={} pointsOfInterest={}", coords.size(), pointsOfInterest.size());

        LiveStatsDto stats = dto.getStats() != null
                ? dto.getStats()
                : (plannedLegs.isEmpty() ? null : aggregateLegStats(plannedLegs));
        if (!hasRoute || stats == null) {
            stats = new LiveStatsDto();
            stats.setLegCount(hasRoute ? 1 : 0);
        }
        // Elevation is server-owned, including saves submitted by older clients.
        PlannerGeometryMetrics.updateElevation(stats, coords);
        updateLegElevations(plannedLegs);

        GpsTrack track = new GpsTrack();
        track.setTrackSource(GpsTrack.TRACK_SOURCE.PLANNED);
        track.setLoadStatus(GpsTrack.LOAD_STATUS.SUCCESS);
        track.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.EXCLUDED);
        track.setExplorationStatus(GpsTrack.EXPLORATION_STATUS.NOT_SCHEDULED);
        track.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);

        String name = (dto.getName() == null || dto.getName().isBlank())
                ? PlannerConstants.DEFAULT_PLANNED_TRACK_NAME
                : dto.getName();
        track.setTrackName(name);
        track.setMetaName(name);
        track.setTrackDescription(dto.getDescription() == null ? "Planned map" : dto.getDescription());
        track.setPlannerProfile(dto.getProfile());
        track.setCreateDate(new Date());
        track.setStartDate(new Date());
        track.setEndDate(new Date());
        track.setNumberOfTrackPoints(coords.size());

        // Bbox + center + fallback geometry length
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY, maxLng = Double.NEGATIVE_INFINITY;
        double geometryLengthM = 0.0;
        for (int i = 0; i < coords.size(); i++) {
            double[] c = coords.get(i);
            double lng = c[0], lat = c[1];
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLng = Math.min(minLng, lng);
            maxLng = Math.max(maxLng, lng);
            if (i > 0) geometryLengthM += haversine(coords.get(i - 1)[1], coords.get(i - 1)[0], lat, lng);
        }
        for (PointOfInterestDto point : pointsOfInterest) {
            minLat = Math.min(minLat, point.getLat());
            maxLat = Math.max(maxLat, point.getLat());
            minLng = Math.min(minLng, point.getLng());
            maxLng = Math.max(maxLng, point.getLng());
        }
        double lengthM = stats.getDistanceM() > 0.0
                ? stats.getDistanceM()
                : geometryLengthM;
        stats.setDistanceM(lengthM);
        track.setBboxMinLat(minLat);
        track.setBboxMaxLat(maxLat);
        track.setBboxMinLng(minLng);
        track.setBboxMaxLng(maxLng);
        track.setCenterLat((minLat + maxLat) / 2.0);
        track.setCenterLng((minLng + maxLng) / 2.0);
        track.setTrackLengthInMeter(lengthM);
        track.setAscentInMeter(stats.getAscentM());
        track.setDescentInMeter(stats.getDescentM());

        // Persist the original user waypoints so the plan is re-loadable.
        if (dto.getWaypoints() != null && !dto.getWaypoints().isEmpty()) {
            try {
                track.setPlannerWaypointsJson(objectMapper.writeValueAsString(dto.getWaypoints()));
            } catch (Exception e) {
                log.error("Failed to serialize plan waypoints: type={} message={}",
                        e.getClass().getSimpleName(), e.getMessage(), e);
                throw new IllegalStateException("Could not serialize plan waypoints", e);
            }
        }
        track.setPlannerRouteJson(writePlannerRouteJson(plannedLegs, stats, pointsOfInterest));

        track = gpsTrackRepository.save(track);

        // A POI-only plan has no track geometry and cannot be exported as GPX.
        if (hasRoute) {
            // Persist geometry as SIMPLIFIED/1m — that's the variant clients load.
            Coordinate[] jtsCoords = new Coordinate[coords.size()];
            for (int i = 0; i < coords.size(); i++) {
                double[] c = coords.get(i);
                jtsCoords[i] = new Coordinate(c[0], c[1], c.length >= 3 ? c[2] : Double.NaN);
            }
            LineString line = geometryFactory.createLineString(jtsCoords);
            line.setSRID(SRID_WGS84);

            GpsTrackData data = GpsTrackData.builder()
                    .gpsTrackId(track.getId())
                    .createDate(new Date())
                    .trackType(GpsTrackData.TRACK_TYPE.SIMPLIFIED_SHAPE)
                    .precisionInMeter(GpsTrackData.PRECISION_1M)
                    .track(line)
                    .build();
            gpsTrackDataRepository.save(data);
        }

        log.info("Saved plan id={} routePoints={} pointsOfInterest={} lengthM={} durationMs={}",
                track.getId(), coords.size(), pointsOfInterest.size(), Math.round(lengthM),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return track;
    }

    public List<PlannedTrackSummaryDto> listPlannedTracks() {
        long startedAt = System.nanoTime();
        List<GpsTrack> tracks = gpsTrackRepository.findByTrackSource(GpsTrack.TRACK_SOURCE.PLANNED);
        List<PlannedTrackSummaryDto> out = new ArrayList<>(tracks.size());
        for (GpsTrack t : tracks) {
            PlannedTrackSummaryDto s = new PlannedTrackSummaryDto();
            s.setId(t.getId());
            s.setName(t.getTrackName());
            s.setDescription(t.getTrackDescription());
            s.setDistanceM(t.getTrackLengthInMeter() == null ? 0.0 : t.getTrackLengthInMeter());
            s.setHasRoute(t.getNumberOfTrackPoints() != null && t.getNumberOfTrackPoints() >= 2);
            s.setPointOfInterestCount(pointOfInterestCount(t.getPlannerRouteJson(), t.getId()));
            s.setCenterLat(t.getCenterLat());
            s.setCenterLng(t.getCenterLng());
            s.setCreateDate(t.getCreateDate());
            s.setProfile(t.getPlannerProfile());
            out.add(s);
        }
        log.info("Listed saved plans: count={} durationMs={}", out.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return out;
    }

    /**
     * Listing summaries must remain available even if one document cannot be opened.
     */
    private int pointOfInterestCount(String json, long id) {
        if (json == null || json.isBlank()) return 0;
        try {
            JsonNode points = objectMapper.readTree(json).get(PLANNER_POINTS_OF_INTEREST_FIELD);
            return points != null && points.isArray() ? points.size() : 0;
        } catch (Exception e) {
            log.warn("Could not read POI count for plan id={}: type={} message={}",
                    id, e.getClass().getSimpleName(), e.getMessage());
            log.debug("Plan summary read failure id={}", id, e);
            return 0;
        }
    }

    /**
     * Full plan payload for re-hydrating the editor: original waypoints +
     * already-routed geometry + the profile the route was built with.
     *
     * @throws IllegalArgumentException if the track does not exist or is not PLANNED
     */
    public PlannedTrackDetailDto loadDetail(long id) {
        GpsTrack track = requirePlannedTrack(id);
        GpsTrackData data = loadPlanGeometry(id);

        PlannedTrackDetailDto out = new PlannedTrackDetailDto();
        out.setId(track.getId());
        out.setName(track.getTrackName());
        out.setDescription(track.getTrackDescription());
        out.setDistanceM(track.getTrackLengthInMeter() == null ? 0.0 : track.getTrackLengthInMeter());
        out.setProfile(track.getPlannerProfile());

        // Waypoints (legacy plans saved before 026 may have none — return empty list)
        List<WaypointDto> wps = new ArrayList<>();
        if (track.getPlannerWaypointsJson() != null && !track.getPlannerWaypointsJson().isBlank()) {
            try {
                wps = objectMapper.readValue(track.getPlannerWaypointsJson(),
                        new TypeReference<List<WaypointDto>>() {
                        });
            } catch (Exception e) {
                log.warn("Failed to deserialize planner waypoints for plan id={} — returning empty list", id, e);
            }
        }
        out.setWaypoints(wps);

        SavedPlannerRoute savedRoute = readPlannerRoute(track.getPlannerRouteJson(), id);
        List<LegResultDto> legs = savedRoute.legs();
        LiveStatsDto stats = savedRoute.stats();
        out.setPointsOfInterest(savedRoute.pointsOfInterest());

        // Coordinates
        List<double[]> coords = new ArrayList<>();
        if (data != null && data.getTrack() != null) {
            for (org.locationtech.jts.geom.Coordinate c : data.getTrack().getCoordinates()) {
                double ele = c.getZ();
                coords.add(Double.isFinite(ele) ? new double[]{c.getX(), c.getY(), ele}
                        : new double[]{c.getX(), c.getY()});
            }
        }
        if (coords.isEmpty() && !legs.isEmpty()) {
            coords = flattenLegCoordinates(legs);
        }
        if (legs.isEmpty() && coords.size() >= 2) {
            legs = List.of(legacySingleLeg(track, coords));
        }
        if (stats == null) {
            stats = legacyStats(track, legs, coords);
        }
        updateLegElevations(legs);
        PlannerGeometryMetrics.updateElevation(stats, legs.isEmpty() ? coords : flattenLegCoordinates(legs));
        out.setCoordinates(coords);
        out.setLegs(legs);
        out.setStats(stats);
        log.info("Loaded plan id={} routePoints={} pointsOfInterest={}", id, coords.size(),
                savedRoute.pointsOfInterest().size());
        return out;
    }

    /**
     * Returns a GPX 1.1 document for the saved planned track with the given id.
     *
     * @throws IllegalArgumentException if the track does not exist or is not PLANNED
     */
    public String buildGpx(long id) {
        GpsTrack track = requirePlannedTrack(id);
        GpsTrackData data = loadPlanGeometry(id);
        if (data == null || data.getTrack() == null) {
            throw new IllegalArgumentException("No geometry found for plan: " + id);
        }
        org.locationtech.jts.geom.Coordinate[] coords = data.getTrack().getCoordinates();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"MTL Explorer Planner\"\n");
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("  <trk>\n");
        sb.append("    <name>").append(escapeXml(track.getTrackName())).append("</name>\n");
        sb.append("    <trkseg>\n");
        for (org.locationtech.jts.geom.Coordinate c : coords) {
            sb.append("      <trkpt lat=\"").append(c.getY()).append("\" lon=\"").append(c.getX()).append("\">");
            if (!Double.isNaN(c.getZ())) {
                sb.append("<ele>").append(String.format("%.1f", c.getZ())).append("</ele>");
            }
            sb.append("</trkpt>\n");
        }
        sb.append("    </trkseg>\n");
        sb.append("  </trk>\n");
        sb.append("</gpx>");
        return sb.toString();
    }

    private GpsTrack requirePlannedTrack(long id) {
        GpsTrack track = gpsTrackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        if (track.getTrackSource() != GpsTrack.TRACK_SOURCE.PLANNED) {
            throw new IllegalArgumentException("Track " + id + " is not a planned route");
        }
        return track;
    }

    private GpsTrackData loadPlanGeometry(long id) {
        return gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(
                id, GpsTrackData.PRECISION_1M);
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Transactional
    public void delete(long id) {
        gpsTrackRepository.findById(id).ifPresent(track -> {
            if (track.getTrackSource() != GpsTrack.TRACK_SOURCE.PLANNED) {
                throw new IllegalArgumentException("Refusing to delete non-PLANNED track via planner API: id=" + id);
            }
            gpsTrackEventRepository.deleteByGpsTrackId(id);
            gpsTrackDataRepository.deleteByGpsTrackId(id);
            gpsTrackRepository.delete(track);
        });
    }

    /**
     * Haversine, metres.
     */
    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                   + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                     * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static void validateRouteCoordinates(List<double[]> coordinates) {
        for (double[] coordinate : coordinates) {
            if (coordinate == null || coordinate.length < 2) {
                throw new IllegalArgumentException("Route coordinate needs longitude and latitude");
            }
            GeoCoordinateUtils.requireValidLongitude(coordinate[0]);
            GeoCoordinateUtils.requireValidLatitude(coordinate[1]);
        }
    }

    private static List<LegResultDto> nonEmptyLegs(List<LegResultDto> legs) {
        if (legs == null || legs.isEmpty()) return List.of();
        List<LegResultDto> out = new ArrayList<>(legs.size());
        for (LegResultDto leg : legs) {
            if (leg != null && leg.getCoordinates() != null && leg.getCoordinates().size() >= 2) {
                out.add(leg);
            }
        }
        return out;
    }

    private static List<double[]> flattenLegCoordinates(List<LegResultDto> legs) {
        List<double[]> out = new ArrayList<>();
        for (LegResultDto leg : legs) {
            if (leg.getCoordinates() == null) continue;
            out.addAll(leg.getCoordinates());
        }
        return out;
    }

    private static LiveStatsDto aggregateLegStats(List<LegResultDto> legs) {
        return PlannerGeometryMetrics.routeStats(legs);
    }

    private static void updateLegElevations(List<LegResultDto> legs) {
        for (LegResultDto leg : legs) {
            var totals = PlannerGeometryMetrics.elevationTotals(leg.getCoordinates());
            leg.setAscentM(totals.ascentM());
            leg.setDescentM(totals.descentM());
        }
    }

    private String writePlannerRouteJson(List<LegResultDto> legs, LiveStatsDto stats,
                                         List<PointOfInterestDto> pointsOfInterest) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put(PLANNER_ROUTE_SCHEMA_VERSION_FIELD, PLANNER_ROUTE_SCHEMA_VERSION);
            root.set(PLANNER_ROUTE_LEGS_FIELD, objectMapper.valueToTree(legs == null ? List.of() : legs));
            if (stats != null) {
                root.set(PLANNER_ROUTE_STATS_FIELD, objectMapper.valueToTree(stats));
            }
            root.set(PLANNER_POINTS_OF_INTEREST_FIELD, objectMapper.valueToTree(pointsOfInterest));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to serialize plan payload: type={} message={}",
                    e.getClass().getSimpleName(), e.getMessage(), e);
            throw new IllegalStateException("Could not serialize plan payload", e);
        }
    }

    private SavedPlannerRoute readPlannerRoute(String json, long id) {
        if (json == null || json.isBlank()) return new SavedPlannerRoute(List.of(), null, List.of());
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Plan metadata must be an object");
            }
            if (root.path(PLANNER_ROUTE_SCHEMA_VERSION_FIELD).asInt() > PLANNER_ROUTE_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Plan was saved by a newer version of MTL Explorer");
            }
            List<LegResultDto> legs = root.has(PLANNER_ROUTE_LEGS_FIELD)
                    ? objectMapper.treeToValue(root.get(PLANNER_ROUTE_LEGS_FIELD),
                    new TypeReference<List<LegResultDto>>() {
                    })
                    : List.of();
            LiveStatsDto stats = root.has(PLANNER_ROUTE_STATS_FIELD)
                    ? objectMapper.treeToValue(root.get(PLANNER_ROUTE_STATS_FIELD), LiveStatsDto.class)
                    : null;
            List<PointOfInterestDto> pointsOfInterest = root.has(PLANNER_POINTS_OF_INTEREST_FIELD)
                    ? objectMapper.treeToValue(root.get(PLANNER_POINTS_OF_INTEREST_FIELD),
                    new TypeReference<List<PointOfInterestDto>>() {
                    })
                    : List.of();
            return new SavedPlannerRoute(legs == null ? List.of() : legs, stats,
                    validatedPointsOfInterest(pointsOfInterest));
        } catch (Exception e) {
            log.warn("Could not read plan payload id={}: type={} message={}",
                    id, e.getClass().getSimpleName(), e.getMessage());
            log.debug("Plan payload read failure id={}", id, e);
            throw new IllegalArgumentException("Could not read saved plan metadata", e);
        }
    }

    private static List<PointOfInterestDto> validatedPointsOfInterest(List<PointOfInterestDto> points) {
        if (points == null) return List.of();
        if (points.size() > PlannerConstants.MAX_POINTS_OF_INTEREST) {
            throw new IllegalArgumentException("Too many points of interest");
        }
        HashSet<String> ids = new HashSet<>();
        for (PointOfInterestDto point : points) {
            if (point == null || point.getId() == null || point.getLat() == null || point.getLng() == null) {
                throw new IllegalArgumentException("Point of interest needs an id and coordinates");
            }
            if (point.getIconType() == null) point.setIconType(PointOfInterestIconType.PIN);
            if (!UUID.fromString(point.getId()).toString().equals(point.getId()) || !ids.add(point.getId())) {
                throw new IllegalArgumentException("Point of interest ids must be unique UUIDs");
            }
            GeoCoordinateUtils.requireValidLatitude(point.getLat());
            GeoCoordinateUtils.requireValidLongitude(point.getLng());
            if (point.getName() == null || point.getName().isBlank()
                || point.getName().length() > PlannerConstants.MAX_POINT_OF_INTEREST_NAME_LENGTH) {
                throw new IllegalArgumentException("Point of interest needs a name of at most "
                                                   + PlannerConstants.MAX_POINT_OF_INTEREST_NAME_LENGTH + " characters");
            }
            if (point.getNotes() != null
                && point.getNotes().length() > PlannerConstants.MAX_POINT_OF_INTEREST_NOTES_LENGTH) {
                throw new IllegalArgumentException("Point of interest notes are too long");
            }
        }
        return points;
    }

    private static LegResultDto legacySingleLeg(GpsTrack track, List<double[]> coords) {
        LiveStatsDto stats = legacyStats(track, List.of(), coords);
        LegResultDto leg = new LegResultDto();
        leg.setCoordinates(coords);
        leg.setDistanceM(stats.getDistanceM());
        leg.setAscentM(stats.getAscentM());
        leg.setDescentM(stats.getDescentM());
        leg.setDurationSec(stats.getDurationSec());
        leg.setCached(true);
        return leg;
    }

    private static LiveStatsDto legacyStats(GpsTrack track, List<LegResultDto> legs, List<double[]> coords) {
        if (legs != null && !legs.isEmpty()) {
            return aggregateLegStats(legs);
        }
        PlannerGeometryMetrics.ElevationTotals elevationTotals = PlannerGeometryMetrics.elevationTotals(coords);
        LiveStatsDto stats = new LiveStatsDto();
        stats.setDistanceM(track.getTrackLengthInMeter() == null ? geometryLength(coords) : track.getTrackLengthInMeter());
        stats.setAscentM(elevationTotals.ascentM());
        stats.setDescentM(elevationTotals.descentM());
        stats.setDurationSec(0.0);
        stats.setLegCount(coords.size() >= 2 ? 1 : 0);
        stats.setAnyLegCached(true);
        return stats;
    }

    private static double geometryLength(List<double[]> coords) {
        double lengthM = 0.0;
        for (int i = 1; i < coords.size(); i++) {
            lengthM += haversine(coords.get(i - 1)[1], coords.get(i - 1)[0], coords.get(i)[1], coords.get(i)[0]);
        }
        return lengthM;
    }

    @JsonPropertyOrder({
            "legs",
            "stats",
            "pointsOfInterest"
    })
    private record SavedPlannerRoute(List<LegResultDto> legs, LiveStatsDto stats,
                                     List<PointOfInterestDto> pointsOfInterest) {
    }
}
