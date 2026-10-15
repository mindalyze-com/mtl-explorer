package com.x8ing.mtl.server.mtlserver.logic.crossing;

import com.x8ing.mtl.server.mtlserver.analysis.segment.SegmentReadScope;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXReader;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsResponse;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.SegmentTimingStatus;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actual parser, cleaner, persisted rows/events, spatial query and crossing service; synthetic data only.
 */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false",
        "mtl.denoise.algorithm=median", "mtl.media-correlation.initial-delay=PT1H"})
@Transactional
class SegmentTimingDatabaseIntegrationTest {
    @Autowired
    GPXStoreService store;
    @Autowired
    TrackTimeBetweenTwoPoints service;
    @Autowired
    GpsTrackEventRepository events;
    @Autowired
    GpsTrackRepository tracks;
    @Autowired
    GpsTrackDataPointRepository points;
    @Autowired
    GpsTrackVariantSelector variants;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    jakarta.persistence.EntityManager entityManager;
    @MockitoBean
    GPXDirectoryWatcherService directoryWatcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;

    @MockitoBean
    SegmentReadScope readScope;

    @BeforeEach
    void useRollbackTransactionForGeometryFixtures() {
        com.x8ing.mtl.server.mtlserver.testsupport.SegmentReadTestSupport.directReads(readScope);
    }

    @Test
    void millisecondsSurviveImportPersistenceAndCrossingCalculation() {
        long trackId = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00.900Z</time></trkpt>
                  <trkpt lat="47" lon="8.00001"><time>2026-01-01T00:00:01.100Z</time></trkpt>
                </trkseg>
                """);
        var result = measure(trackId, 0.2, 8, 8.00001);
        assertThat(result.crossings.get(trackId).getCrossings()).hasSize(2);
        var end = result.crossings.get(trackId).getCrossings().getLast();
        assertThat(end.timeInSecSinceLastTriggerPoint).isEqualTo(0.2);
        assertThat(end.timingStatusSinceLastTriggerPoint).isEqualTo(SegmentTimingStatus.AVAILABLE);
        assertThat(result.segmentsStats).singleElement().satisfies(segment -> assertThat(segment.timeDelta).isEqualTo(0.2));
    }

    @Test
    void aShortExplicitRecordingBreakIsPersistedAndCannotCreateCrossings() {
        long trackId = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.0001"><time>2026-01-01T00:00:10Z</time></trkpt>
                </trkseg><trkseg>
                  <trkpt lat="47" lon="8.002"><time>2026-01-01T00:00:20Z</time></trkpt>
                  <trkpt lat="47" lon="8.0021"><time>2026-01-01T00:00:30Z</time></trkpt>
                </trkseg>
                """);
        assertThat(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(trackId))
                .anySatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo(GpsTrackEvent.EVENT_TYPE.GPS_GAP);
                    assertThat(event.getSource()).isEqualTo(GpsTrackEvent.SOURCE.IMPORTED);
                    assertThat(event.getStartPointIndex()).isEqualTo(1);
                    assertThat(event.getEndPointIndex()).isEqualTo(2);
                });
        assertThat(measure(trackId, 2, 8.0007, 8.0014).crossings).doesNotContainKey(trackId);
        var across = measure(trackId, 2, 8.0001, 8.002);
        assertThat(across.crossings.get(trackId).getCrossings()).hasSize(2);
        assertThat(across.crossings.get(trackId).getCrossings().getLast().timingStatusSinceLastTriggerPoint)
                .isEqualTo(SegmentTimingStatus.AVAILABLE);
        var end = across.crossings.get(trackId).getCrossings().getLast();
        assertThat(end.recordingGapSinceLastTriggerPoint).isTrue();
        assertThat(end.timeInSecSinceLastTriggerPoint).isEqualTo(10d);
        assertThat(end.distanceInMeterSinceLastTriggerPoint).isNull();
        assertThat(end.avgSpeedSinceLastTriggerPoint).isNull();
        assertThat(across.segmentsStats).isEmpty();
        assertThat(end.segmentNotesSinceLastTriggerPoint.coverageComplete).isFalse();
    }

    @Test
    void stationarySamplesOnDifferentRecordingSectionsDoNotBecomeDenseStopAnchors() {
        long trackId = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:10Z</time></trkpt>
                </trkseg><trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:20:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.001"><time>2026-01-01T00:20:10Z</time></trkpt>
                </trkseg>
                """);
        var result = measure(trackId, 2, 8, 8.001);
        assertThat(result.crossings.get(trackId).getCrossings()).hasSize(3);
        assertThat(result.crossings.get(trackId).getCrossings().get(1).timingStatusSinceLastTriggerPoint)
                .isEqualTo(SegmentTimingStatus.AVAILABLE);
    }

    @Test
    void dateLineCandidateQueriesUseGeographyInsteadOfLongitudeBoundingBoxes() {
        long trackId = imported("""
                <trkseg>
                  <trkpt lat="47" lon="179.999"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="-179.999"><time>2026-01-01T00:00:20Z</time></trkpt>
                </trkseg>
                """);
        assertThat(tracks.getTracksWithinDistanceToPoint(179.9995, 47, 10, new Long[]{trackId})).containsExactly(trackId);
        assertThat(tracks.getTracksWithDistanceToPoint(-179.9995, 47, 10, new Long[]{trackId}))
                .singleElement().satisfies(row -> assertThat(row.getDistanceMeters()).isLessThan(0.01));
        var result = measure(trackId, 10, 179.9995, -179.9995);
        assertThat(result.crossings.get(trackId).getCrossings()).hasSize(2);
        assertThat(result.crossings.get(trackId).getCrossings().getLast().timeInSecSinceLastTriggerPoint).isEqualTo(10);
    }

    @Test
    void denseStopAnchorsKeepTheRecordedElapsedTime() {
        StringBuilder segment = new StringBuilder("<trkseg><trkpt lat=\"47\" lon=\"8\"><time>2026-01-01T00:00:00Z</time></trkpt>");
        var base = java.time.Instant.parse("2026-01-01T00:00:00Z");
        for (int second = 10; second <= 190; second++) {
            segment.append("<trkpt lat=\"47\" lon=\"8.002\"><time>")
                    .append(base.plusSeconds(second)).append("</time></trkpt>");
        }
        segment.append("<trkpt lat=\"47\" lon=\"8.004\"><time>2026-01-01T00:03:20Z</time></trkpt></trkseg>");
        long trackId = imported(segment.toString());
        assertThat(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(trackId))
                .anyMatch(event -> event.getEventType() == GpsTrackEvent.EVENT_TYPE.STOP)
                .noneMatch(event -> event.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP);
        var result = measure(trackId, 2, 8, 8.004);
        assertThat(result.crossings.get(trackId).getCrossings().getLast().timeInSecSinceLastTriggerPoint).isEqualTo(200);
        assertThat(result.segmentsStats).hasSize(1);
        var notes = result.crossings.get(trackId).getCrossings().getLast().segmentNotesSinceLastTriggerPoint;
        assertThat(notes.stopCount).isEqualTo(1);
        assertThat(notes.totalStoppedSec).isEqualTo(180);
        assertThat(notes.longestStopSec).isEqualTo(180);
    }

    @Test
    void nearbyDuplicateTimestampRemainsGeometryOnlyThroughImportAndDiscovery() {
        long trackId = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.0004"><time>2026-01-01T00:00:04Z</time></trkpt>
                  <trkpt lat="47" lon="8.00041"><time>2026-01-01T00:00:04Z</time></trkpt>
                  <trkpt lat="47" lon="8.001"><time>2026-01-01T00:00:10Z</time></trkpt>
                </trkseg>
                """);
        assertThat(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(trackId))
                .noneMatch(event -> event.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP);
        var canonical = points.findAllByGpsTrackDataIdOrderByPointIndexAsc(variants.forMetricsId(trackId));
        assertThat(canonical).anyMatch(point -> point.getIntervalStatus()
                == com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint.IntervalStatus.GEOMETRY_ONLY);
        var result = measure(trackId, 2, 8, 8.001);
        var end = result.crossings.get(trackId).getCrossings().getLast();
        assertThat(end.timingStatusSinceLastTriggerPoint).isEqualTo(SegmentTimingStatus.AVAILABLE);
        assertThat(end.timeInSecSinceLastTriggerPoint).isEqualTo(10);
        assertThat(end.distanceInMeterSinceLastTriggerPoint).isPositive();
    }

    @Test
    void actualRadiusQualificationAndCountsExcludeNearMissCandidates() {
        long id = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.005"><time>2026-01-01T00:00:40Z</time></trkpt>
                </trkseg>
                """);
        var near = net.sf.geographiclib.Geodesic.WGS84.Direct(47, 8.001, 0, 10.1);
        assertThat(tracks.getSegmentCandidateTrackIds(near.lon2, near.lat2, 10, new Long[]{id})).containsExactly(id);
        var request = new CrossingPointsRequest();
        request.radius = 10d;
        request.triggerPoints = List.of(new TriggerPoint("A", new Coordinate(near.lon2, near.lat2)),
                new TriggerPoint("B", new Coordinate(8.002, 47)), new TriggerPoint("C", new Coordinate(8.004, 47)));
        var result = service.getTrackCrossingsWithoutSummaries(request, new Long[]{id});
        assertThat(result.crossings).isEmpty();
        assertThat(result.segmentsStats).isEmpty();
        assertThat(result.tracksPerZone).containsEntry("A", 0).containsEntry("B", 1).containsEntry("C", 1);
    }

    @Test
    void separateZoneCandidatesAreQualifiedExactlyThroughGeometryProjections() {
        long first = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.0002"><time>2026-01-01T00:00:10Z</time></trkpt>
                </trkseg>
                """);
        long second = imported("""
                <trkseg>
                  <trkpt lat="47" lon="8.01"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.0102"><time>2026-01-01T00:00:10Z</time></trkpt>
                </trkseg>
                """);
        var request = new CrossingPointsRequest();
        request.radius = 1d;
        request.triggerPoints = List.of(new TriggerPoint("A", new Coordinate(8.0001, 47)),
                new TriggerPoint("B", new Coordinate(8.0101, 47)));
        var result = service.getTrackCrossingsWithoutSummaries(request, new Long[]{first, second});
        assertThat(result.crossings).isEmpty();
        assertThat(result.tracksPerZone).containsExactlyInAnyOrderEntriesOf(java.util.Map.of("A", 1, "B", 1));
    }

    @Test
    void isolatedObservedFixAtRecordingBoundaryRemainsDiscoverable() {
        long id = imported("""
                <trkseg><trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt></trkseg>
                <trkseg>
                  <trkpt lat="47" lon="8.001"><time>2026-01-01T00:00:10Z</time></trkpt>
                  <trkpt lat="47" lon="8.002"><time>2026-01-01T00:00:20Z</time></trkpt>
                </trkseg>
                """);
        assertThat(tracks.getSegmentCandidateTrackIds(8, 47, 1, new Long[]{id})).containsExactly(id);
        var result = measure(id, 1, 8, 8.002);
        var end = result.crossings.get(id).getCrossings().getLast();
        assertThat(end.recordingGapSinceLastTriggerPoint).isTrue();
        assertThat(end.timeInSecSinceLastTriggerPoint).isEqualTo(20d);
        assertThat(end.distanceInMeterSinceLastTriggerPoint).isNull();
    }

    @Test
    void anUntimedObservationCannotHideALongGapDuringImport() {
        for (String endTime : List.of("00:02:10", "01:00:00")) {
            long id = imported("""
                    <trkseg>
                      <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                      <trkpt lat="47" lon="8.0002"/>
                      <trkpt lat="47" lon="8.0004"><time>2026-01-01T%sZ</time></trkpt>
                    </trkseg>
                    """.formatted(endTime));
            assertThat(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(id))
                    .anyMatch(e -> e.getEventType() == GpsTrackEvent.EVENT_TYPE.GPS_GAP && e.getEndPointIndex() == 2);
            var result = measure(id, 1, 8, 8.0004);
            var end = result.crossings.get(id).getCrossings().getLast();
            assertThat(end.recordingGapSinceLastTriggerPoint).isTrue();
            assertThat(end.timeInSecSinceLastTriggerPoint).isEqualTo(endTime.equals("01:00:00") ? 3600d : 130d);
            assertThat(end.distanceInMeterSinceLastTriggerPoint).isNull();
            assertThat(tracks.getSegmentCandidateTrackIds(8.0003, 47, 1, new Long[]{id})).isEmpty();
        }
    }

    @Test
    void candidateGeometryFollowsTheRecordedCurveAtPolarLatitudes() {
        long id = imported("""
                <trkseg>
                  <trkpt lat="89.999" lon="0"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="89.999" lon="179"><time>2026-01-01T00:01:40Z</time></trkpt>
                </trkseg>
                """);
        assertThat(tracks.getSegmentCandidateTrackIds(90, 89.999, 1, new Long[]{id})).containsExactly(id);
        var request = new CrossingPointsRequest();
        request.radius = 1d;
        request.triggerPoints = List.of(new TriggerPoint("A", new Coordinate(90, 89.999)));
        assertThat(service.getTrackCrossingsWithoutSummaries(request, new Long[]{id}).crossings.get(id).getCrossings())
                .singleElement().satisfies(c -> assertThat(c.distanceToTriggerPointInMeter).isLessThan(.001));
    }

    @Test
    void longEastWestCanonicalPathSurvivesCandidateSelectionAtOrdinaryLatitude() {
        var start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var section = new StringBuilder("<trkseg>");
        for (int i = 0; i <= 250; i++) {
            double longitude = 8 + i * 200 / (111320 * Math.cos(Math.toRadians(47)));
            section.append("<trkpt lat=\"47\" lon=\"").append(longitude).append("\"><time>")
                    .append(start.plusSeconds(i * 20L)).append("</time></trkpt>");
        }
        section.append("</trkseg>");
        long id = imported(section.toString());
        for (int i : List.of(1, 25, 125, 200, 249)) {
            double longitude = 8 + i * 200 / (111320 * Math.cos(Math.toRadians(47)));
            assertThat(tracks.getSegmentCandidateTrackIds(longitude, 47, .1, new Long[]{id})).containsExactly(id);
        }
    }

    @Test
    void stoppedCoverageDoesNotLeakBetweenRealSectionsWithRepeatingTimes() {
        var start = java.time.Instant.parse("2026-01-01T00:00:00Z");
        var section = new StringBuilder("<trkseg><trkpt lat=\"47\" lon=\"8\"><time>2026-01-01T00:00:00Z</time></trkpt>");
        for (int i = 10; i <= 190; i++) section.append("<trkpt lat=\"47\" lon=\"8.002\"><time>")
                .append(start.plusSeconds(i)).append("</time></trkpt>");
        section.append("""
                <trkpt lat="47" lon="8.004"><time>2026-01-01T00:03:20Z</time></trkpt></trkseg>
                <trkseg>
                  <trkpt lat="47" lon="8"><time>2026-01-01T00:00:00Z</time></trkpt>
                  <trkpt lat="47" lon="8.002"><time>2026-01-01T00:01:40Z</time></trkpt>
                  <trkpt lat="47" lon="8.004"><time>2026-01-01T00:03:20Z</time></trkpt>
                </trkseg>
                """);
        long id = imported(section.toString());
        var visits = measure(id, 1, 8, 8.004).crossings.get(id).getCrossings();
        assertThat(visits).hasSize(4);
        assertThat(visits.get(1).segmentNotesSinceLastTriggerPoint.totalStoppedSec).isEqualTo(180d);
        assertThat(visits.getLast().segmentNotesSinceLastTriggerPoint.totalStoppedSec).isZero();
        assertThat(visits.getLast().segmentNotesSinceLastTriggerPoint.stopCount).isZero();
    }

    private long imported(String segments) {
        String name = "synthetic-segment-" + UUID.randomUUID() + ".gpx";
        Long id = jdbc.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status)
                VALUES ('GPS', ?, '/synthetic', ?, 'COMPLETED_WITH_SUCCESS') RETURNING id
                """, Long.class, name, "/synthetic/" + name);
        var file = entityManager.find(IndexedFile.class, id);
        List<GPXReader.LoadResult> results = store.readAndSave(file,
                "<gpx version=\"1.1\" creator=\"MTL Explorer synthetic test\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk>"
                + segments + "</trk></gpx>");
        assertThat(results).hasSize(1);
        long trackId = results.getFirst().gpsTrack.getId();
        assertThat(points.findAllByGpsTrackDataIdOrderByPointIndexAsc(variants.forMetricsId(trackId))).isNotEmpty();
        return trackId;
    }

    private CrossingPointsResponse measure(long trackId, double radius, double fromLon, double toLon) {
        var request = new CrossingPointsRequest();
        request.radius = radius;
        request.triggerPoints = List.of(new TriggerPoint("A", new Coordinate(fromLon, 47)),
                new TriggerPoint("B", new Coordinate(toLon, 47)));
        return service.getTrackCrossingsWithoutSummaries(request, new Long[]{trackId});
    }
}
