package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackData;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.projection.*;
import com.x8ing.mtl.server.mtlserver.db.readonly.spring.QueryResult;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.*;
import com.x8ing.mtl.server.mtlserver.energy.EnergyService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsResponse;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.FilterParamResolver;
import com.x8ing.mtl.server.mtlserver.logic.grouping.sql.GpsTrackSQLFilter;
import com.x8ing.mtl.server.mtlserver.utils.TimingCollector;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/tracks")
@JsonPropertyOrder({
        "gpsTrackRepository",
        "trackTimeBetweenTwoPoints",
        "trackAndDataService",
        "gpsTrackDataRepository",
        "gpsTrackDataPointRepository",
        "gpsTrackEventRepository",
        "gpsTrackSQLFilter",
        "filterParamResolver",
        "energyService",
        "trackFileExportService",
        "statisticsOverviewExecutor"
})
public class TracksController {

    public static final int DEFAULT_CACHE_TIME_IN_SECONDS = 300;
    private static final int STATISTICS_OVERVIEW_TOP_LIST_LIMIT = 100;
    private static final long STATISTICS_OVERVIEW_SLOW_LOG_THRESHOLD_MS = 100;
    private static final List<String> OVERVIEW_TRACK_RANKING_ORDER = List.of(
            "longest-distance",
            "longest-duration",
            "biggest-ascent",
            "quickest-ascent",
            "most-energy",
            "fastest-speed",
            "peak-power"
    );
    private static final List<String> OVERVIEW_PERIOD_ORDER = List.of("day", "week", "month", "weekday");

    private final GpsTrackRepository gpsTrackRepository;

    private final TrackTimeBetweenTwoPoints trackTimeBetweenTwoPoints;

    private final GpsTrackAndDataService trackAndDataService;

    private final GpsTrackDataRepository gpsTrackDataRepository;

    private final GpsTrackDataPointRepository gpsTrackDataPointRepository;

    private final GpsTrackEventRepository gpsTrackEventRepository;

    private final GpsTrackSQLFilter gpsTrackSQLFilter;

    private final FilterParamResolver filterParamResolver;

    private final EnergyService energyService;

    private final TrackFileExportService trackFileExportService;

    private final Executor statisticsOverviewExecutor;

    public TracksController(
            GpsTrackRepository gpsTrackRepository,
            TrackTimeBetweenTwoPoints trackTimeBetweenTwoPoints,
            GpsTrackAndDataService trackAndDataService,
            GpsTrackDataRepository gpsTrackDataRepository,
            GpsTrackDataPointRepository gpsTrackDataPointRepository,
            GpsTrackEventRepository gpsTrackEventRepository,
            GpsTrackSQLFilter gpsTrackSQLFilter,
            FilterParamResolver filterParamResolver,
            EnergyService energyService,
            TrackFileExportService trackFileExportService,
            @Qualifier("statisticsOverviewExecutor") Executor statisticsOverviewExecutor
    ) {
        this.gpsTrackRepository = gpsTrackRepository;
        this.trackTimeBetweenTwoPoints = trackTimeBetweenTwoPoints;
        this.trackAndDataService = trackAndDataService;
        this.gpsTrackDataRepository = gpsTrackDataRepository;
        this.gpsTrackDataPointRepository = gpsTrackDataPointRepository;
        this.gpsTrackEventRepository = gpsTrackEventRepository;
        this.gpsTrackSQLFilter = gpsTrackSQLFilter;
        this.filterParamResolver = filterParamResolver;
        this.energyService = energyService;
        this.trackFileExportService = trackFileExportService;
        this.statisticsOverviewExecutor = statisticsOverviewExecutor;
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
                .cacheControl(privateTrackCacheControl())
                .body(gpsTrack);
    }

    @RequestMapping("/duplicates/{gpsTrackId}")
    public List<Long> getDuplicatesForTrackId(@PathVariable Long gpsTrackId) {
        return gpsTrackRepository.getDuplicatesForGpsTrackId(gpsTrackId);
    }

    @Operation(operationId = "downloadTrackSourceFile", summary = "Download the original indexed GPS source file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Original indexed track file",
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track source file not available", content = @Content),
            @ApiResponse(responseCode = "422", description = "Unsupported source track format", content = @Content)
    })
    @GetMapping(value = "/{gpsTrackId}/source-file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> downloadTrackSourceFile(@PathVariable Long gpsTrackId) {
        return trackDownloadResponse(trackFileExportService.sourceFile(gpsTrackId));
    }

    @Operation(operationId = "downloadTrackGpx", summary = "Download the indexed GPS source file as GPX")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "GPX track file",
                    content = @Content(mediaType = TrackFileExportService.GPX_MEDIA_TYPE_VALUE,
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content),
            @ApiResponse(responseCode = "404", description = "Track source file not available", content = @Content),
            @ApiResponse(responseCode = "422", description = "Unsupported or non-convertible source track format", content = @Content),
            @ApiResponse(responseCode = "500", description = "GPX conversion failed", content = @Content)
    })
    @GetMapping(value = "/{gpsTrackId}/gpx", produces = TrackFileExportService.GPX_MEDIA_TYPE_VALUE)
    public ResponseEntity<Resource> downloadTrackGpx(@PathVariable Long gpsTrackId) {
        return trackDownloadResponse(trackFileExportService.gpx(gpsTrackId));
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
            @RequestParam(name = "trackType", defaultValue = "SIMPLIFIED_SHAPE") String trackType
    ) {

        List<GpsTrackDataPoint> details = gpsTrackDataPointRepository
                .getTrackDetailsByGpsTrackIdAndPrecisionAndType(gpsTrackId, precisionInMeter, trackType);

        return ResponseEntity.ok()
                .cacheControl(privateTrackCacheControl())
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

    @PatchMapping("/{gpsTrackId}/statistics-exclusion")
    public GpsTrack updateTrackStatisticsExclusion(
            @PathVariable Long gpsTrackId,
            @RequestBody StatisticsExclusionUpdateRequest request
    ) {
        GpsTrack track = gpsTrackRepository.findById(gpsTrackId).orElseThrow();
        track.setHighlightExclusionReason(request.highlightExclusionReason());
        track.setStatisticsExclusionReason(request.statisticsExclusionReason());
        return gpsTrackRepository.save(track);
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
                List<Object[]> versionRows = gpsTrackRepository.findVersionsByIds(ids.toArray(Long[]::new));
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

    @PostMapping(value = "/get-track-overview")
    public StatisticsOverviewResponseDto getTrackOverview(
            @RequestBody(required = false) Map<String, String> params,
            @RequestParam(name = "filterName", required = false) String filterName
    ) {
        TimingCollector timing = new TimingCollector();
        Long[] ids = new Long[0];

        try {
            QueryResult filterIds = timing.timeUnchecked("resolveFilter",
                    () -> gpsTrackSQLFilter.getGpsTrackIdsForOptionalFilterName(filterName, params));
            Long[] filterTrackIds = filterIds.asIdArray();
            ids = filterTrackIds;

            var summaryFuture = overviewAsync(timing, "summary",
                    () -> toOverviewSummary(gpsTrackRepository.getTrackOverviewSummary(filterTrackIds)));

            var activityBreakdownFuture = overviewAsync(timing, "activityBreakdown",
                    () -> gpsTrackRepository.getTrackOverviewActivityBreakdown(filterTrackIds).stream()
                            .map(TracksController::toActivityBreakdown)
                            .toList());

            var trackRankingsFuture = overviewAsync(timing, "trackRankings",
                    () -> toTrackRankings(gpsTrackRepository.getTrackOverviewTrackRankings(filterTrackIds, STATISTICS_OVERVIEW_TOP_LIST_LIMIT)));

            var recentActivitiesFuture = overviewAsync(timing, "recentActivities",
                    () -> gpsTrackRepository.getTrackOverviewRecentActivities(filterTrackIds).stream()
                            .map(TracksController::toTrackRef)
                            .toList());

            var periodDistributionsFuture = overviewAsync(timing, "periodDistributions",
                    () -> toPeriodDistributions(gpsTrackRepository.getTrackOverviewPeriodDistributions(filterTrackIds, STATISTICS_OVERVIEW_TOP_LIST_LIMIT)));

            var milestonesFuture = overviewAsync(timing, "milestones",
                    () -> gpsTrackRepository.getTrackOverviewMilestones(filterTrackIds).stream()
                            .map(TracksController::toTrackRef)
                            .toList());

            var exclusionSummaryFuture = overviewAsync(timing, "exclusionSummary",
                    () -> toExclusionSummary(gpsTrackRepository.getTrackOverviewExclusions(filterTrackIds)));

            CompletableFuture.allOf(
                    summaryFuture,
                    activityBreakdownFuture,
                    trackRankingsFuture,
                    recentActivitiesFuture,
                    periodDistributionsFuture,
                    milestonesFuture,
                    exclusionSummaryFuture
            ).join();

            var periodDistributions = periodDistributionsFuture.join();

            return new StatisticsOverviewResponseDto(
                    summaryFuture.join(),
                    activityBreakdownFuture.join(),
                    trackRankingsFuture.join(),
                    recentActivitiesFuture.join(),
                    toActivePeriods(periodDistributions),
                    periodDistributions,
                    milestonesFuture.join(),
                    exclusionSummaryFuture.join()
            );
        } finally {
            logStatisticsOverviewTiming(filterName, params, ids, timing);
        }
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
                .cacheControl(privateTrackCacheControl())
                .body(subTrackData);
    }

    private static CacheControl privateTrackCacheControl() {
        return CacheControl.maxAge(DEFAULT_CACHE_TIME_IN_SECONDS, TimeUnit.SECONDS)
                .cachePrivate()
                .mustRevalidate();
    }

    private static void logStatisticsOverviewTiming(String filterName, Map<String, String> params, Long[] ids, TimingCollector timing) {
        long totalElapsedMs = timing.totalElapsedMs();
        String effectiveFilterName = filterName == null || filterName.isBlank() ? "<default>" : filterName;
        int filterParamCount = params == null ? 0 : params.size();
        int trackCount = ids == null ? 0 : ids.length;

        if (totalElapsedMs >= STATISTICS_OVERVIEW_SLOW_LOG_THRESHOLD_MS) {
            log.info("Statistics overview slow request: filterName={}, filterParamCount={}, trackCount={}, {}",
                    effectiveFilterName,
                    filterParamCount,
                    trackCount,
                    timing.formatSummary());
        } else if (log.isDebugEnabled()) {
            log.debug("Statistics overview timing: filterName={}, filterParamCount={}, trackCount={}, {}",
                    effectiveFilterName,
                    filterParamCount,
                    trackCount,
                    timing.formatSummary());
        }
    }

    private <T> CompletableFuture<T> overviewAsync(TimingCollector timing, String label, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(
                () -> timing.timeUnchecked(label, supplier),
                statisticsOverviewExecutor
        );
    }

    private static StatisticsOverviewResponseDto.Summary toOverviewSummary(GpsTrackOverviewSummary summary) {
        if (summary == null) {
            return new StatisticsOverviewResponseDto.Summary(0, 0, 0, 0, null, null);
        }
        return new StatisticsOverviewResponseDto.Summary(
                longValue(summary.getTrackCount()),
                doubleValue(summary.getDistanceM()),
                doubleValue(summary.getDurationMs()),
                doubleValue(summary.getEnergyWh()),
                summary.getOldestStart(),
                summary.getNewestStart()
        );
    }

    private static StatisticsOverviewResponseDto.ActivityBreakdown toActivityBreakdown(GpsTrackOverviewActivity activity) {
        return new StatisticsOverviewResponseDto.ActivityBreakdown(
                activity.getActivityType(),
                longValue(activity.getTrackCount()),
                doubleValue(activity.getDistanceM()),
                doubleValue(activity.getDurationMs()),
                doubleValue(activity.getEnergyWh())
        );
    }

    private static List<StatisticsOverviewResponseDto.TrackRanking> toTrackRankings(List<GpsTrackOverviewTrackRow> rows) {
        Map<String, List<StatisticsOverviewResponseDto.TrackRef>> rowsByKey = rows.stream()
                .collect(Collectors.groupingBy(
                        GpsTrackOverviewTrackRow::getRowKey,
                        LinkedHashMap::new,
                        Collectors.mapping(TracksController::toTrackRef, Collectors.toList())
                ));

        return OVERVIEW_TRACK_RANKING_ORDER.stream()
                .filter(rowsByKey::containsKey)
                .map(key -> new StatisticsOverviewResponseDto.TrackRanking(key, rowsByKey.get(key)))
                .toList();
    }

    private static StatisticsOverviewResponseDto.TrackRef toTrackRef(GpsTrackOverviewTrackRow row) {
        return new StatisticsOverviewResponseDto.TrackRef(
                row.getSortOrder(),
                row.getRowKey(),
                row.getTrackId(),
                doubleValue(row.getValue())
        );
    }

    private static StatisticsOverviewResponseDto.PeriodRow toPeriodRow(GpsTrackOverviewPeriod period) {
        return new StatisticsOverviewResponseDto.PeriodRow(
                period.getSortOrder(),
                period.getPeriodType(),
                period.getPeriodKey(),
                period.getLabel(),
                longValue(period.getTrackCount()),
                doubleValue(period.getDistanceM()),
                doubleValue(period.getDurationMs())
        );
    }

    private static List<StatisticsOverviewResponseDto.PeriodDistribution> toPeriodDistributions(List<GpsTrackOverviewPeriod> rows) {
        Map<String, List<StatisticsOverviewResponseDto.PeriodRow>> rowsByType = rows.stream()
                .collect(Collectors.groupingBy(
                        GpsTrackOverviewPeriod::getPeriodType,
                        LinkedHashMap::new,
                        Collectors.mapping(TracksController::toPeriodRow, Collectors.toList())
                ));

        return OVERVIEW_PERIOD_ORDER.stream()
                .filter(rowsByType::containsKey)
                .map(type -> new StatisticsOverviewResponseDto.PeriodDistribution(type, rowsByType.get(type)))
                .toList();
    }

    private static List<StatisticsOverviewResponseDto.PeriodRow> toActivePeriods(List<StatisticsOverviewResponseDto.PeriodDistribution> distributions) {
        return distributions.stream()
                .map(StatisticsOverviewResponseDto.PeriodDistribution::rows)
                .filter(Objects::nonNull)
                .map(rows -> rows.isEmpty() ? null : rows.getFirst())
                .filter(Objects::nonNull)
                .map(row -> new StatisticsOverviewResponseDto.PeriodRow(
                        overviewPeriodSortOrder(row.periodType()),
                        row.periodType(),
                        row.periodKey(),
                        row.label(),
                        row.trackCount(),
                        row.distanceM(),
                        row.durationMs()
                ))
                .toList();
    }

    private static int overviewPeriodSortOrder(String periodType) {
        int index = OVERVIEW_PERIOD_ORDER.indexOf(periodType);
        return index < 0 ? Integer.MAX_VALUE : (index + 1) * 10;
    }

    private static StatisticsOverviewResponseDto.ExclusionSummary toExclusionSummary(GpsTrackOverviewExclusions exclusions) {
        if (exclusions == null) {
            return new StatisticsOverviewResponseDto.ExclusionSummary(0, 0);
        }
        return new StatisticsOverviewResponseDto.ExclusionSummary(
                longValue(exclusions.getHighlightExcludedTrackCount()),
                longValue(exclusions.getStatisticsExcludedTrackCount())
        );
    }

    private static long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private static double doubleValue(Double value) {
        return value == null ? 0.0 : value;
    }

    private static ResponseEntity<Resource> trackDownloadResponse(TrackFileExportService.TrackFileDownload download) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(download.getMediaType())
                .contentLength(download.getContentLength())
                .lastModified(download.getLastModifiedMillis())
                .eTag(download.getEtag())
                .cacheControl(privateTrackCacheControl())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(download.getResource());
    }

}
