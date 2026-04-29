package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.GpsTrackStatistics;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.IWayPointWithDistance;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.*;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsResponse;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterParamResolver;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.GpsTrackSQLFilter;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/tracks")
public class TracksController {

    public static final int DEFAULT_CACHE_TIME_IN_SECONDS = 300;

    private final GpsTrackRepository gpsTrackRepository;

    private final TrackTimeBetweenTwoPoints trackTimeBetweenTwoPoints;

    private final GpsTrackAndDataService trackAndDataService;

    private final GpsTrackDataRepository gpsTrackDataRepository;

    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;

    private final GpsTrackEventRepository gpsTrackEventRepository;

    private final GpsTrackSQLFilter gpsTrackSQLFilter;

    private final FilterParamResolver filterParamResolver;

    private final EnergyService energyService;

    public TracksController(GpsTrackRepository gpsTrackRepository, TrackTimeBetweenTwoPoints trackTimeBetweenTwoPoints, GpsTrackAndDataService trackAndDataService, GpsTrackDataRepository gpsTrackDataRepository, GpsTrackDataPointRepository gpsTrackDataPointRepository, GpsTrackEventRepository gpsTrackEventRepository, GpsTrackSQLFilter gpsTrackSQLFilter, FilterParamResolver filterParamResolver, EnergyService energyService) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.trackTimeBetweenTwoPoints = trackTimeBetweenTwoPoints;
        this.trackAndDataService = trackAndDataService;
        this.gpsTrackDataRepository = gpsTrackDataRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.gpsTrackEventRepository = gpsTrackEventRepository;
        this.gpsTrackSQLFilter = gpsTrackSQLFilter;
        this.filterParamResolver = filterParamResolver;
        this.energyService = energyService;
    }


    @RequestMapping("/get")
    public List<GpsTrack> getTracks() {
        return gpsTrackRepository.findAll();
    }

    @RequestMapping("/get/{gpsTrackId}")
    public ResponseEntity<GpsTrack> getSingleTrack(@PathVariable Long gpsTrackId, @RequestParam(name = "precisionInMeter", defaultValue = "1") BigDecimal precisionInMeter) {
        GpsTrack gpsTrack = gpsTrackRepository.findById(gpsTrackId).orElseThrow();
        GpsTrackData trackData = gpsTrackDataRepository.findFirstByGpsTrackIdAndPrecisionInMeter(gpsTrackId, precisionInMeter);
        if (trackData != null) {
            trackData.setGpsTrackEvents(gpsTrackEventRepository.findAllByGpsTrackIdOrderByStartPointIndexAsc(gpsTrackId));
            gpsTrack.getGpsTracksData().add(trackData);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(DEFAULT_CACHE_TIME_IN_SECONDS, TimeUnit.SECONDS))
                .body(gpsTrack);
    }

    @RequestMapping("/duplicates/{gpsTrackId}")
    public List<Long> getDuplicatesForTrackId(@PathVariable Long gpsTrackId) {
        return gpsTrackRepository.getDuplicatesForGpsTrackId(gpsTrackId);
    }

    @RequestMapping("/related/{gpsTrackId}")
    public RelatedTracks getRelatedTracks(
            @RequestBody(required = false) Map<String, String> params,
            @PathVariable Long gpsTrackId,
            @RequestParam(name = "filterName", required = false) String filterName) {

        QueryResult filterIds = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(filterName, params);

        List<Long> dupIds = gpsTrackRepository.getDuplicatesForGpsTrackId(gpsTrackId);
        List<Long> prevIds = gpsTrackRepository.getRelatedTrackIdsPrevious(gpsTrackId, filterIds.asIdArray());
        List<Long> nextIds = gpsTrackRepository.getRelatedTrackIdsNext(gpsTrackId, filterIds.asIdArray());
        List<Long> siblingIds = gpsTrackRepository.findSegmentSiblingIds(gpsTrackId);

        Map<Long, GpsTrack> allById = new HashMap<>();
        List<Long> allIds = new ArrayList<>();
        allIds.addAll(dupIds);
        allIds.addAll(prevIds);
        allIds.addAll(nextIds);
        allIds.addAll(siblingIds);
        gpsTrackRepository.findAllById(allIds).forEach(t -> allById.put(t.getId(), t));

        // Previous: repo returns desc (most recent first) — preserve that order for the frontend
        List<RelatedTrackInfo> previousTracks = prevIds.stream()
                .filter(allById::containsKey)
                .map(id -> RelatedTrackInfo.from(allById.get(id)))
                .collect(Collectors.toList());

        // Next: repo returns asc (soonest first)
        List<RelatedTrackInfo> nextTracks = nextIds.stream()
                .filter(allById::containsKey)
                .map(id -> RelatedTrackInfo.from(allById.get(id)))
                .collect(Collectors.toList());

        List<RelatedTrackInfo> duplicates = dupIds.stream()
                .filter(allById::containsKey)
                .map(id -> RelatedTrackInfo.from(allById.get(id)))
                .collect(Collectors.toList());

        RelatedTracks relatedTracks = new RelatedTracks();
        relatedTracks.setPreviousTracksInTime(previousTracks);
        relatedTracks.setNextTracksInTime(nextTracks);
        relatedTracks.setDuplicates(duplicates);

        List<RelatedTrackInfo> segmentSiblings = siblingIds.stream()
                .filter(allById::containsKey)
                .map(id -> RelatedTrackInfo.from(allById.get(id)))
                .collect(Collectors.toList());
        relatedTracks.setSegmentSiblings(segmentSiblings);

        return relatedTracks;
    }

    @RequestMapping(value = "/get/{gpsTrackId}/details")
    // TODO:  Check if we can make that smarter
    //@CacheableResponse(durationInSeconds = 180)
    public ResponseEntity<List<GpsTrackDataPoint>> getTrackDetails(
            @PathVariable Long gpsTrackId,
            @RequestParam(name = "precisionInMeter", defaultValue = "1") BigDecimal precisionInMeter,
            @RequestParam(name = "trackType", defaultValue = "SIMPLIFIED_SHAPE") String trackType,
            @RequestParam(name = "maxPoints", required = false) Integer maxPoints
    ) {

        // SIMPLIFIED_FIXED_POINTS is discriminated by max_points (e.g. 750 or
        // 1500), not precision_in_meter. All other variants are keyed by
        // (precision_in_meter, track_type). See GpsTrackData.TRACK_TYPE.
        List<GpsTrackDataPoint> details;
        if ("SIMPLIFIED_FIXED_POINTS".equals(trackType) && maxPoints != null) {
            details = gpsTrackDataPointRepository
                    .getTrackDetailsByGpsTrackIdAndTypeAndMaxPoints(gpsTrackId, trackType, maxPoints);
        } else {
            details = gpsTrackDataPointRepository
                    .getTrackDetailsByGpsTrackIdAndPrecisionAndType(gpsTrackId, precisionInMeter, trackType);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(DEFAULT_CACHE_TIME_IN_SECONDS, TimeUnit.SECONDS))
                .body(details);
    }


    @RequestMapping("/save-track")
    public GpsTrack saveTrack(@RequestBody GpsTrack gpsTrack) {
        // Detect activity-type change vs persisted state — if the user picks a new type,
        // stamp it as USER_SET (so the auto-classifier won't revisit) and trigger an
        // energy recompute afterwards, since the physics depend on the activity type.
        GpsTrack.ACTIVITY_TYPE previousActivityType = gpsTrackRepository.findById(gpsTrack.getId())
                .map(GpsTrack::getActivityType)
                .orElse(null);
        GpsTrack.ACTIVITY_TYPE newActivityType = gpsTrack.getActivityType();
        boolean activityTypeChanged = newActivityType != previousActivityType;
        if (activityTypeChanged) {
            gpsTrack.setActivityTypeSource(GpsTrack.ACTIVITY_TYPE_SOURCE.USER_SET);
        }

        GpsTrack saved = gpsTrackRepository.save(gpsTrack);

        if (activityTypeChanged) {
            try {
                energyService.recalculateEnergyForTrack(saved.getId(), energyService.getDefaultParameters());
            } catch (Exception e) {
                log.warn("Energy recalc after user activity-type change failed for trackId={}: {}",
                        saved.getId(), e.getMessage(), e);
            }
        }
        return saved;
    }

    /**
     * Unified endpoint for fetching tracks.
     * <p>
     * mode=full (default): Returns full track metadata + GPS geometry at the requested precision.
     * Used for the initial bulk load and background precision upgrades.
     * <p>
     * mode=ids: Returns only the matching track IDs with their entity versions + filter group assignments.
     * The client compares each version against its cached copy to detect stale tracks
     * and selectively re-fetches only those that changed.
     */
    @RequestMapping(value = "/get-simplified")
    public TracksSimplifiedResponse getTracksSimplified(
            @RequestBody(required = false) FilterParamsRequest params,
            @RequestParam(name = "precisionInMeter", defaultValue = "10") BigDecimal precisionInMeter,
            @RequestParam(name = "filterName", required = false) String filterName,
            @RequestParam(name = "mode", defaultValue = "full") String mode) {

        Map<String, String> sqlParams = filterParamResolver.expand(params);
        QueryResult filterIds = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(filterName, sqlParams);

        // ── mode=ids: lightweight path — return IDs + versions + filter groups ──
        if ("ids".equalsIgnoreCase(mode)) {
            List<Long> ids = new ArrayList<>();
            Map<Long, String> groups = new HashMap<>();
            if (filterIds != null && filterIds.getResultEntries() != null) {
                for (QueryResult.QueryResultEntry entry : filterIds.getResultEntries()) {
                    ids.add(entry.getId());
                    if (entry.getGroup() != null) {
                        groups.put(entry.getId(), entry.getGroup());
                    }
                }
            }

            // Fetch (id, version) pairs for all matched IDs
            Map<Long, Long> trackVersions = new HashMap<>();
            if (!ids.isEmpty()) {
                List<Object[]> versionRows = gpsTrackRepository.findVersionsByIds(ids);
                for (Object[] row : versionRows) {
                    trackVersions.put((Long) row[0], (Long) row[1]);
                }
            }

            QueryResult standardFilterResult = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(null, Collections.emptyMap());
            long standardFilterCount = (standardFilterResult != null && standardFilterResult.getResultEntries() != null)
                    ? standardFilterResult.getResultEntries().size()
                    : ids.size();
            return TracksSimplifiedResponse.idsOnly(standardFilterCount, ids.size(), trackVersions, groups);
        }

        // ── mode=full (default): return full track data with geometry ──
        Map<Long, QueryResult.QueryResultEntry> mapping = new HashMap<>();
        Map<Long, Long> filterOrderMapping = new HashMap<>();
        boolean hasFilter = filterIds != null && filterIds.getResultEntries() != null;

        if (hasFilter) {
            long filterPos = 0;
            for (QueryResult.QueryResultEntry queryResultEntry : filterIds.getResultEntries()) {
                mapping.put(queryResultEntry.getId(), queryResultEntry);
                filterOrderMapping.put(queryResultEntry.getId(), filterPos++);
            }
        }

        List<Long> effectiveTrackIds = filterIds != null ? filterIds.asIdList() : Collections.emptyList();
        if (params != null && params.getTrackIds() != null && !params.getTrackIds().isEmpty()) {
            Set<Long> allowedIds = new HashSet<>(effectiveTrackIds);
            effectiveTrackIds = params.getTrackIds().stream()
                    .filter(allowedIds::contains)
                    .toList();
        }

        List<GpsTrack> gpsTracks = trackAndDataService.findAllGpsTracksWithData(precisionInMeter, effectiveTrackIds);
        ArrayList<GpsTrackResponse> responses = gpsTracks.stream().map(gpsTrack -> new GpsTrackResponse(gpsTrack, mapping.get(gpsTrack.getId()))).collect(Collectors.toCollection(ArrayList::new));

        if (hasFilter) {
            responses.sort(Comparator.comparing(o -> filterOrderMapping.getOrDefault(o.getGpsTrack().getId(), Long.MAX_VALUE)));
        } else {
            responses.sort(Comparator.comparing(o -> o.getGpsTrack().getId()));
        }

        QueryResult standardFilterResult = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(null, Collections.emptyMap());
        long standardFilterCount = (standardFilterResult != null && standardFilterResult.getResultEntries() != null)
                ? standardFilterResult.getResultEntries().size()
                : responses.size();

        return new TracksSimplifiedResponse(standardFilterCount, responses.size(), responses);
    }

    @RequestMapping(value = "/get-track-ids-within-distance-of-point")
    public List<Long> getTrackIdsWithinDistanceOfPoint(
            @RequestParam(name = "filterName", required = false) String filterName,
            @RequestBody(required = false) Map<String, String> params,
            @RequestParam(name = "longitude") Double longitude,
            @RequestParam(name = "latitude") Double latitude,
            @RequestParam(name = "distanceInMeter") Double distanceInMeter
    ) {
        QueryResult filterIds = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(filterName, params);
        return gpsTrackRepository.getTracksWithinDistanceToPoint(longitude, latitude, distanceInMeter, filterIds.asIdArray());
    }

    @RequestMapping(value = "/get-tracks-within-distance-of-point")
    public List<GpsTrack> getTracksWithinDistanceOfPoint(
            @RequestBody(required = false) Map<String, String> params,
            @RequestParam(name = "longitude") Double longitude,
            @RequestParam(name = "latitude") Double latitude,
            @RequestParam(name = "distanceInMeter") Double distanceInMeter,
            @RequestParam(name = "filterName", required = false) String filterName
    ) {
        List<Long> trackIds = getTrackIdsWithinDistanceOfPoint(filterName, params, longitude, latitude, distanceInMeter);
        List<GpsTrack> tracks = gpsTrackRepository.findAllById(trackIds);
        tracks.sort(Comparator.nullsLast(Comparator.comparing(GpsTrack::getStartDate, (date1, date2) -> {
            if (Objects.isNull(date1) && Objects.isNull(date2)) {
                return 0;
            } else if (Objects.isNull(date1)) {
                return -1;
            } else if (Objects.isNull(date2)) {
                return 1;
            } else {
                return date1.compareTo(date2);
            }
        })));
        return tracks;
    }

    @RequestMapping(value = "/get-closest-point-in-track")
    public List<IWayPointWithDistance> getClosestPointInTrack(@RequestParam(name = "longitude") Double longitude, @RequestParam(name = "latitude") Double latitude, @RequestParam(name = "trackId") Long trackId, @RequestParam(name = "limitRows", defaultValue = "1") Integer limitRows) {
        return gpsTrackRepository.findWithinTrackClosestWaypointToGivenPoint(longitude, latitude, trackId, limitRows);
    }


    @RequestMapping(value = "/get-track-details-for-tracks-crossing-points")
    public CrossingPointsResponse getCrossingPoints(@RequestBody CrossingPointsRequest crossingPointsRequest) {

        FilterRequestBean filter = crossingPointsRequest.getFilter();
        Long[] tracksIdFilterList = null;
        if (filter != null) {
            QueryResult queryResult = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(filter.getFilterName(), filter.getParams());
            tracksIdFilterList = queryResult.asIdArray();
        }
        return trackTimeBetweenTwoPoints.getTrackTimeBetweenPoints(crossingPointsRequest, tracksIdFilterList);
    }

    /**
     * http://localhost:8080/mtl/api/tracks/get-track-statistics?groupByDateFormat=YYYY-WW
     */
    @RequestMapping(value = "/get-track-statistics")
    public List<GpsTrackStatistics> getTrackStatistics(
            @RequestBody(required = false) Map<String, String> params,
            @RequestParam(name = "groupByDateFormat", defaultValue = "YYYY-MM") String groupByDateFormat,
            @RequestParam(name = "filterName", required = false) String filterName,
            @RequestParam(name = "filterValue", required = false) String filterValue
    ) {
        QueryResult filterIds = gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(filterName, params);
        return gpsTrackRepository.getTrackStatistics(groupByDateFormat, filterValue, filterIds.asIdArray(), energyService.getThresholdPowerWatts());
    }

    /**
     * http://localhost:8080/mtl/api/tracks/details/get-sub-track?trackDataPointFrom=615396&trackDataPointTo=615484
     */
    @RequestMapping(value = "/details/get-sub-track")
    public ResponseEntity<List<GpsTrackDataPoint>> getSubTrackDetails(
            @RequestParam(name = "trackDataPointFrom", required = true) Long trackDataPointFrom,
            @RequestParam(name = "trackDataPointTo", required = true) Long trackDataPointTo,
            @RequestParam(name = "fullTrack", required = false, defaultValue = "false") boolean fullTrack
    ) {

        GpsTrackDataPoint from = gpsTrackDataPointRepository.findById(trackDataPointFrom).orElseThrow();
        GpsTrackDataPoint to = gpsTrackDataPointRepository.findById(trackDataPointTo).orElseThrow();

        if (!from.getGpsTrackDataId().equals(to.getGpsTrackDataId())) {
            throw new RuntimeException(("The given GpsTrackDataPoint's don't belong to the same track. " +
                                        "Yet, they must be equal. " +
                                        "gpsTrackDataFrom=%s, gpsTrackDataTo=%s")
                    .formatted(from.getGpsTrackDataId(), to.getGpsTrackDataId()));
        }

        List<GpsTrackDataPoint> subTrackData = new ArrayList<>();
        if (fullTrack) {
            subTrackData = gpsTrackDataPointRepository.getSubTrackData(from.getGpsTrackDataId(), 0, to.getPointIndexMax());
        } else {
            subTrackData = gpsTrackDataPointRepository.getSubTrackData(from.getGpsTrackDataId(), from.getPointIndex(), to.getPointIndex());
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(DEFAULT_CACHE_TIME_IN_SECONDS, TimeUnit.SECONDS))
                .body(subTrackData);
    }

}
