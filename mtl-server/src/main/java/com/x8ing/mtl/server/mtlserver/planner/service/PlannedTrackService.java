package com.x8ing.mtl.server.mtlserver.planner.service;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.planner.constants.PlannerConstants;
import com.x8ing.mtl.server.mtlserver.planner.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Persists planner-generated routes as GpsTrack rows flagged with
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
    private static final int PLANNER_ROUTE_SCHEMA_VERSION = 1;
    private static final String PLANNER_ROUTE_SCHEMA_VERSION_FIELD = "schemaVersion";
    private static final String PLANNER_ROUTE_LEGS_FIELD = "legs";
    private static final String PLANNER_ROUTE_STATS_FIELD = "stats";
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID_WGS84);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GpsTrackRepository gpsTrackRepository;
    private final GpsTrackDataRepository gpsTrackDataRepository;
    private final GpsTrackEventRepository gpsTrackEventRepository;

    public PlannedTrackService(GpsTrackRepository gpsTrackRepository,
                               GpsTrackDataRepository gpsTrackDataRepository,
                               GpsTrackEventRepository gpsTrackEventRepository) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.gpsTrackDataRepository = gpsTrackDataRepository;
        this.gpsTrackEventRepository = gpsTrackEventRepository;
    }

    @Transactional
    public GpsTrack save(SavePlannedTrackDto dto) {
        List<LegResultDto> plannedLegs = nonEmptyLegs(dto.getLegs());
        List<double[]> coords = plannedLegs.isEmpty()
                ? dto.getCoordinates()
                : flattenLegCoordinates(plannedLegs);
        if (coords == null || coords.size() < 2) {
            throw new IllegalArgumentException("At least two coordinates are required to save a planned track");
        }

        LiveStatsDto stats = dto.getStats() != null
                ? dto.getStats()
                : (plannedLegs.isEmpty() ? null : aggregateLegStats(plannedLegs));

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
        track.setTrackDescription(dto.getDescription() == null ? "Planned route" : dto.getDescription());
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
        double lengthM = stats != null && stats.getDistanceM() > 0.0
                ? stats.getDistanceM()
                : geometryLengthM;
        track.setBboxMinLat(minLat);
        track.setBboxMaxLat(maxLat);
        track.setBboxMinLng(minLng);
        track.setBboxMaxLng(maxLng);
        track.setCenterLat((minLat + maxLat) / 2.0);
        track.setCenterLng((minLng + maxLng) / 2.0);
        track.setTrackLengthInMeter(lengthM);
        if (stats != null) {
            track.setAscentInMeter(stats.getAscentM());
            track.setDescentInMeter(stats.getDescentM());
        }

        // Persist the original user waypoints so the plan is re-loadable.
        if (dto.getWaypoints() != null && !dto.getWaypoints().isEmpty()) {
            try {
                track.setPlannerWaypointsJson(objectMapper.writeValueAsString(dto.getWaypoints()));
            } catch (Exception e) {
                log.warn("Failed to serialize planner waypoints — saving plan without editable waypoints", e);
            }
        }
        if (!plannedLegs.isEmpty() || stats != null) {
            track.setPlannerRouteJson(writePlannerRouteJson(plannedLegs, stats));
        }

        track = gpsTrackRepository.save(track);

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

        log.info("Saved planned track id={} points={} lengthM={}",
                track.getId(), coords.size(), Math.round(lengthM));
        return track;
    }

    public List<PlannedTrackSummaryDto> listPlannedTracks() {
        List<GpsTrack> tracks = gpsTrackRepository.findByTrackSource(GpsTrack.TRACK_SOURCE.PLANNED);
        List<PlannedTrackSummaryDto> out = new ArrayList<>(tracks.size());
        for (GpsTrack t : tracks) {
            PlannedTrackSummaryDto s = new PlannedTrackSummaryDto();
            s.setId(t.getId());
            s.setName(t.getTrackName());
            s.setDescription(t.getTrackDescription());
            s.setDistanceM(t.getTrackLengthInMeter() == null ? 0.0 : t.getTrackLengthInMeter());
            s.setCenterLat(t.getCenterLat());
            s.setCenterLng(t.getCenterLng());
            s.setCreateDate(t.getCreateDate());
            s.setProfile(t.getPlannerProfile());
            out.add(s);
        }
        return out;
    }

    /**
     * Full plan payload for re-hydrating the editor: original waypoints +
     * already-routed geometry + the profile the route was built with.
     *
     * @throws IllegalArgumentException if the track does not exist or is not PLANNED
     */
    public PlannedTrackDetailDto loadDetail(long id) {
        GpsTrack track = gpsTrackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        if (track.getTrackSource() != GpsTrack.TRACK_SOURCE.PLANNED) {
            throw new IllegalArgumentException("Track " + id + " is not a planned route");
        }
        GpsTrackData data = gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(
                id, GpsTrackData.PRECISION_1M);

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

        // Coordinates
        List<double[]> coords = new ArrayList<>();
        if (data != null && data.getTrack() != null) {
            for (org.locationtech.jts.geom.Coordinate c : data.getTrack().getCoordinates()) {
                double ele = Double.isNaN(c.getZ()) ? 0.0 : c.getZ();
                coords.add(new double[]{c.getX(), c.getY(), ele});
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
        out.setCoordinates(coords);
        out.setLegs(legs);
        out.setStats(stats);
        return out;
    }

    /**
     * Returns a GPX 1.1 document for the saved planned track with the given id.
     *
     * @throws IllegalArgumentException if the track does not exist or is not PLANNED
     */
    public String buildGpx(long id) {
        GpsTrack track = gpsTrackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        if (track.getTrackSource() != GpsTrack.TRACK_SOURCE.PLANNED) {
            throw new IllegalArgumentException("Track " + id + " is not a planned route");
        }
        GpsTrackData data = gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(
                id, GpsTrackData.PRECISION_1M);
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
        LiveStatsDto stats = new LiveStatsDto();
        for (LegResultDto leg : legs) {
            stats.setDistanceM(stats.getDistanceM() + leg.getDistanceM());
            stats.setAscentM(stats.getAscentM() + leg.getAscentM());
            stats.setDescentM(stats.getDescentM() + leg.getDescentM());
            stats.setDurationSec(stats.getDurationSec() + leg.getDurationSec());
            if (leg.isCached()) stats.setAnyLegCached(true);
        }
        stats.setLegCount(legs.size());
        return stats;
    }

    private String writePlannerRouteJson(List<LegResultDto> legs, LiveStatsDto stats) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put(PLANNER_ROUTE_SCHEMA_VERSION_FIELD, PLANNER_ROUTE_SCHEMA_VERSION);
            root.set(PLANNER_ROUTE_LEGS_FIELD, objectMapper.valueToTree(legs == null ? List.of() : legs));
            if (stats != null) {
                root.set(PLANNER_ROUTE_STATS_FIELD, objectMapper.valueToTree(stats));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to serialize planner route payload — saving plan without exact route metadata", e);
            return null;
        }
    }

    private SavedPlannerRoute readPlannerRoute(String json, long id) {
        if (json == null || json.isBlank()) return new SavedPlannerRoute(List.of(), null);
        try {
            JsonNode root = objectMapper.readTree(json);
            List<LegResultDto> legs = root.has(PLANNER_ROUTE_LEGS_FIELD)
                    ? objectMapper.readValue(root.get(PLANNER_ROUTE_LEGS_FIELD).traverse(),
                    new TypeReference<List<LegResultDto>>() {
                    })
                    : List.of();
            LiveStatsDto stats = root.has(PLANNER_ROUTE_STATS_FIELD)
                    ? objectMapper.treeToValue(root.get(PLANNER_ROUTE_STATS_FIELD), LiveStatsDto.class)
                    : null;
            return new SavedPlannerRoute(legs == null ? List.of() : legs, stats);
        } catch (Exception e) {
            log.warn("Failed to deserialize planner route payload for plan id={} — falling back to flattened geometry", id, e);
            return new SavedPlannerRoute(List.of(), null);
        }
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
        ElevationTotals elevationTotals = computeAscentDescent(coords);
        LiveStatsDto stats = new LiveStatsDto();
        stats.setDistanceM(track.getTrackLengthInMeter() == null ? geometryLength(coords) : track.getTrackLengthInMeter());
        stats.setAscentM(track.getAscentInMeter() == null ? elevationTotals.ascentM() : track.getAscentInMeter());
        stats.setDescentM(track.getDescentInMeter() == null ? elevationTotals.descentM() : track.getDescentInMeter());
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

    private static ElevationTotals computeAscentDescent(List<double[]> coords) {
        double ascent = 0.0;
        double descent = 0.0;
        for (int i = 1; i < coords.size(); i++) {
            double dz = coords.get(i)[2] - coords.get(i - 1)[2];
            if (dz > PlannerConstants.MIN_ELEVATION_DELTA_M) {
                ascent += dz;
            } else if (dz < -PlannerConstants.MIN_ELEVATION_DELTA_M) {
                descent += -dz;
            }
        }
        return new ElevationTotals(ascent, descent);
    }

    @JsonPropertyOrder({
            "ascentM",
            "descentM"
    })
    private record ElevationTotals(double ascentM, double descentM) {
    }

    @JsonPropertyOrder({
            "legs",
            "stats"
    })
    private record SavedPlannerRoute(List<LegResultDto> legs, LiveStatsDto stats) {
    }
}
