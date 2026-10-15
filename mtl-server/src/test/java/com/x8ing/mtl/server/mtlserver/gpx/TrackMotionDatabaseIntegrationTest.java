package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackEvent;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackEventRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.logic.motion.GpsTrackEventService;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackMotionAnalyzer;
import com.x8ing.mtl.server.mtlserver.logic.motion.TrackStopDetector;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false",
        "mtl.denoise.algorithm=kalman", "mtl.media-correlation.initial-delay=PT1H"})
@Transactional
class TrackMotionDatabaseIntegrationTest {
    @Autowired
    GPXStoreService store;
    @Autowired
    GpsTrackDataRepository data;
    @Autowired
    GpsTrackDataPointRepository points;
    @Autowired
    GpsTrackRepository tracks;
    @Autowired
    GpsTrackEventRepository events;
    @Autowired
    GpsTrackEventService eventService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    jakarta.persistence.EntityManager em;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;
    @MockitoBean(name = "kalman")
    GpsSmoothingAlgorithm smoother;

    @Test
    void canonicalImportPersistsOneTimelineOriginalGeometryAndBoundaryEvidence() {
        when(smoother.denoise(any(LineString.class), anyList())).thenAnswer(call -> ((LineString) call.getArgument(0)).copy());
        when(smoother.processingSettings()).thenReturn(Map.of("algorithm", "synthetic-passthrough"));
        List<Coordinate> input = new ArrayList<>();
        for (int t = 0; t <= 450; t += 10) {
            double x = t <= 60 ? t : t <= 360 ? 60 : t <= 390 ? t - 300 : t <= 420 ? 90 : t - 330;
            input.add(GpsPipelineReliabilityTest.c(x, 0, 500, t));
        }
        long id = imported(GpsPipelineReliabilityTest.segment(input));
        var track = tracks.findById(id).orElseThrow();
        assertThat(track.getMotionAnalysisStatus()).isEqualTo(TrackMotionAnalyzer.Status.COMPLETE);
        assertThat(track.getTrackDurationInMotionSecs()).isEqualTo(120);
        assertThat(track.getTrackDurationStoppedSecs()).isEqualTo(330);
        assertThat(track.getTrackDurationShortStoppedSecs()).isEqualTo(30);
        assertThat(track.getTrackDurationUnknownSecs()).isZero();
        assertThat(track.getTrackStopCount()).isEqualTo(1);
        var canonical = data.findFirstByGpsTrackIdAndTrackType(id, "RAW_OUTLIER_CLEANED");
        var rows = points.getTrackDetailsByGpsTrackIdAndType(id, "RAW_OUTLIER_CLEANED");
        assertThat(rows).hasSize(input.size());
        assertThat(canonical.getTrack().getNumPoints()).isEqualTo(input.size());
        var raw = data.findFirstByGpsTrackIdAndTrackType(id, "RAW");
        for (int i = 0; i < input.size(); i++) {
            assertThat(canonical.getTrack().getCoordinateN(i).getX()).isEqualTo(raw.getTrack().getCoordinateN(i).getX());
            assertThat(canonical.getTrack().getCoordinateN(i).getY()).isEqualTo(raw.getTrack().getCoordinateN(i).getY());
            assertThat(canonical.getTrack().getCoordinateN(i).getM()).isEqualTo(raw.getTrack().getCoordinateN(i).getM());
        }
        assertThat(rows.get(7).getMotionState()).isEqualTo(TrackMotionAnalyzer.State.STOPPED);
        assertThat(rows.get(7).getIntervalStatus()).isEqualTo(GpsTrackDataPoint.IntervalStatus.OBSERVED);
        assertThat(rows.stream().filter(p -> p.getMotionState() == TrackMotionAnalyzer.State.STOPPED)
                .mapToDouble(GpsTrackDataPoint::getDurationBetweenPointsInSec).sum()).isEqualTo(330);
        var detected = events.findAllByGpsTrackIdOrderByStartPointIndexAsc(id);
        assertThat(detected).hasSize(1);
        var stop = detected.getFirst();
        assertThat(stop.getStartPointIndex()).isEqualTo(6);
        assertThat(stop.getEndPointIndex()).isEqualTo(36);
        assertThat(stop.getStartGpsTrackDataPointId()).isEqualTo(rows.get(6).getId());
        assertThat(stop.getConfidence()).isNull();
        assertThat(stop.getDetectionEvidence()).isEqualTo(TrackStopDetector.StopEvidence.OBSERVED_STATIONARY);
        assertThat(stop.getDetectionVersion()).isEqualTo(TrackMotionAnalyzer.VERSION);
        assertThat(stop.getStartBoundary().status()).isEqualTo(TrackMotionAnalyzer.BoundaryStatus.BRACKETED);
        assertThat(stop.getConfirmedAtTimestamp()).isAfter(stop.getStartTimestamp());
        // Replacement is idempotent and preserves user events.
        var userEvent = events.save(GpsTrackEvent.builder().gpsTrackId(id).eventType(GpsTrackEvent.EVENT_TYPE.STOP)
                .source(GpsTrackEvent.SOURCE.USER).label("User note").build());
        var analysis = TrackMotionAnalyzer.analyze(Arrays.asList(canonical.getTrack().getCoordinates()), List.of());
        var ranges = TrackStopDetector.mapStopRangesToTrackPoints(rows, TrackStopDetector.eventRanges(analysis));
        eventService.replaceDetectedStopEvents(id, canonical.getId(), ranges);
        eventService.replaceDetectedStopEvents(id, canonical.getId(), ranges);
        em.flush();
        em.clear();
        assertThat(events.findAllByGpsTrackIdOrderByStartPointIndexAsc(id)).hasSize(2);
        assertThat(events.findById(userEvent.getId())).isPresent();
    }

    private long imported(String segment) {
        String name = "synthetic-motion-" + UUID.randomUUID() + ".gpx";
        Long fileId = jdbc.queryForObject("INSERT INTO indexed_file(\"index\",name,path,full_path,indexer_status) VALUES('GPS',?,'/synthetic',?,'COMPLETED_WITH_SUCCESS') RETURNING id",
                Long.class, name, "/synthetic/" + name);
        var result = store.readAndSave(em.find(IndexedFile.class, fileId),
                "<gpx version=\"1.1\" creator=\"MTL Explorer synthetic test\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk>" + segment + "</trk></gpx>");
        assertThat(result).hasSize(1);
        long id = result.getFirst().gpsTrack.getId();
        em.flush();
        em.clear();
        return id;
    }
}
