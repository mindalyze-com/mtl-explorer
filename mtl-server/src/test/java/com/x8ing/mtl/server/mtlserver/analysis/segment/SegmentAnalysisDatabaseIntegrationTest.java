package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.TrackTimeBetweenTwoPoints;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.CrossingPointsRequest;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real import, spatial discovery, resolver, metric/health repositories and comparison. Synthetic data, rolled back.
 */
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false",
        "mtl.denoise.algorithm=median", "mtl.media-correlation.initial-delay=PT1H"})
@Transactional
class SegmentAnalysisDatabaseIntegrationTest {
    @Autowired
    GPXStoreService store;
    @Autowired
    TrackTimeBetweenTwoPoints crossing;
    @Autowired
    SegmentAttemptResolver resolver;
    @Autowired
    SegmentAnalysisController controller;
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
    void fractionalAttemptHasTheSamePreciseIntervalEverywhereAndRejectsChangedSource() {
        String name = "synthetic-analysis-" + UUID.randomUUID() + ".gpx";
        Long fileId = jdbc.queryForObject("""
                INSERT INTO indexed_file("index", name, path, full_path, indexer_status)
                VALUES ('GPS', ?, '/synthetic', ?, 'COMPLETED_WITH_SUCCESS') RETURNING id
                """, Long.class, name, "/synthetic/" + name);
        var imported = store.readAndSave(entityManager.find(IndexedFile.class, fileId), """
                <gpx version="1.1" creator="MTL Explorer synthetic test" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
                <trkpt lat="47" lon="8"><ele>500</ele><time>2036-01-01T00:00:00Z</time></trkpt>
                <trkpt lat="47" lon="8.001"><ele>510</ele><time>2036-01-01T00:00:10Z</time></trkpt>
                </trkseg></trk></gpx>
                """);
        long trackId = imported.getFirst().gpsTrack.getId();
        entityManager.flush();
        entityManager.clear();
        jdbc.update("""
                UPDATE gps_track_data_points SET energy_total_wh=10, power_watts=3600, speed_in_kmh_moving_window=36
                WHERE gps_track_data_id IN (SELECT id FROM gps_track_data WHERE gps_track_id=? AND track_type='RAW_OUTLIER_CLEANED')
                """, trackId);
        jdbc.update("INSERT INTO google_health.connection(id) VALUES(1) ON CONFLICT DO NOTHING");
        for (int second : List.of(0, 2, 10))
            jdbc.update("""
                    INSERT INTO google_health.heart_rate_sample(sample_time, original_sample_time, beats_per_minute, fingerprint, last_seen_run)
                    VALUES(?::timestamptz, ?, 120, ?, ?::uuid)
                    """, "2036-01-01T00:00:%02dZ".formatted(second), "synthetic", UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var request = new CrossingPointsRequest();
        request.radius = .2;
        request.triggerPoints = List.of(new TriggerPoint("A", new Coordinate(8.0001, 47)), new TriggerPoint("B", new Coordinate(8.0004, 47)));
        var raw = crossing.getTrackTimeBetweenPoints(request, new Long[]{trackId}, SegmentAttemptResolver::prepare);
        var discovery = CrossingPointsResponseDto.from(raw);
        resolver.publish(raw, discovery);
        var selected = discovery.getCrossings().get(trackId).getAttempts().getFirst();
        String reference = selected.identity().reference();
        assertEquals(Instant.parse("2036-01-01T00:00:01Z"), selected.identity().start());
        assertEquals(Instant.parse("2036-01-01T00:00:04Z"), selected.identity().end());
        var core = controller.getSegmentAttemptAnalysis(reference, 2, com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.TIME).getBody();
        var health = controller.getSegmentAttemptHealth(reference, 2).getBody();
        var comparison = controller.getSegmentAttemptComparison(reference, reference).getBody();
        var table = controller.getSegmentAttemptHeartRateSummaries(List.of(reference)).getBody();
        assertEquals(selected.identity(), core.identity());
        assertEquals(core.identity(), health.identity());
        assertEquals(core.identity(), comparison.referenceIdentity());
        assertEquals(core.identity(), table.attempts().getFirst().identity());
        assertEquals(3d, core.summary().elapsedSeconds());
        assertEquals(3d, core.summary().energyWh(), 1e-8);
        assertEquals(120d, health.heartRate().averageBpm());
        assertEquals(3d, health.heartRate().coveredSeconds());
        assertEquals(10.8d, health.cardiacCost().mechanicalEnergyKj(), 1e-8);
        assertEquals(6d, health.cardiacCost().estimatedHeartbeats(), 1e-8);
        assertEquals(1, health.points().size());
        assertEquals(Instant.parse("2036-01-01T00:00:02Z"), health.points().getFirst().timestamp());
        assertEquals(0d, comparison.finishDifferenceSeconds());
        assertEquals(core.summary(), controller.getSegmentAttemptAnalysis(reference, 100, com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.TIME).getBody().summary());
        jdbc.update("UPDATE gps_track_data_points SET power_watts=999 WHERE gps_track_data_id IN (SELECT id FROM gps_track_data WHERE gps_track_id=?)", trackId);
        entityManager.clear();
        assertEquals(410, assertThrows(ResponseStatusException.class, () -> controller.getSegmentAttemptAnalysis(reference, 2, com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode.TIME)).getStatusCode().value());
    }
}
