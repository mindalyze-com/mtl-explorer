package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentCardiacCostService;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.SegmentHeartRateService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.logic.crossing.beans.TriggerPoint;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingPointsResponseDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.CrossingsPerTrackDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.GpsTrackDataPointDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.media-correlation.initial-delay=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false", "mtl.indexer.media.live-watch-enabled=false"})
class SegmentCardiacCostDatabaseIntegrationTest {
    private static final Instant START = Instant.parse("2099-03-01T12:00:00Z");
    @Autowired
    private SegmentCardiacCostService service;
    @Autowired
    private SegmentHeartRateService heartRateService;
    @Autowired
    private SegmentCardiacCostRepository repository;
    @Autowired
    private MirrorRepository mirror;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private GPXDirectoryWatcherService gpxWatcher;
    @MockitoBean
    private MediaIndexerService mediaIndexer;

    @Test
    @Transactional
    void usesCanonicalWorkAndRawHeartRateWithStopsPartialCoverageAndDuplicateDevices() {
        mirror.disconnect();
        long trackId = jdbc.queryForObject("INSERT INTO gps_track (activity_type) VALUES ('BICYCLE') RETURNING id", Long.class);
        long canonicalId = variant(trackId, "RAW_OUTLIER_CLEANED");
        long simplifiedId = variant(trackId, "SIMPLIFIED_SHAPE");
        int[] times = {0, 30, 40, 50, 60, 90, 120};
        for (int i = 0; i < times.length; i++) {
            point(canonicalId, i, times[i], i == 0 ? 0 : 250.0 * (times[i] - times[i - 1]) / 3600);
        }
        jdbc.update("UPDATE gps_track_data_points SET motion_state='STOPPED' WHERE gps_track_data_id=? AND point_index=3", canonicalId);
        long fromId = point(simplifiedId, 0, 0, 999.0);
        long toId = point(simplifiedId, 1, 120, 999.0); // Simplified energy must never be used.
        jdbc.update("""
                INSERT INTO gps_track_event (gps_track_id, event_type, source, start_timestamp, end_timestamp)
                VALUES (?, 'STOP', 'DETECTED', ?, ?)
                """, trackId, Timestamp.from(START.plusSeconds(40)), Timestamp.from(START.plusSeconds(50)));
        long source = mirror.upsertDataSource("a".repeat(64), "FITBIT", "AUTOMATIC", "Fitbit", "Synthetic watch", "{}");
        List<HeartRateSample> samples = new ArrayList<>();
        for (int seconds : new int[]{30, 60, 90}) samples.add(sample(source, seconds, 150, String.valueOf(seconds).repeat(32).substring(0, 64)));
        samples.add(sample(source, 60, 150, "b".repeat(64)));
        mirror.upsertHeartRateSamples(samples, UUID.randomUUID());

        assertThat(repository.heartRate(START, START.plusSeconds(120))).hasSize(3);
        var result = service.get(trackId, fromId, toId);
        assertThat(result.available()).isTrue();
        assertThat(result.movingSeconds()).isEqualTo(110);
        assertThat(result.coveredMovingSeconds()).isEqualTo(50);
        assertThat(result.coveragePercent()).isCloseTo(50.0 / 110 * 100, within(1e-6));
        assertThat(result.estimatedHeartbeats()).isCloseTo(125, within(1e-6));
        assertThat(result.mechanicalEnergyKj()).isCloseTo(12.5, within(1e-6));
        assertThat(result.beatsPerKj()).isCloseTo(10, within(1e-6));
        assertThat(result.wattsPerBpm()).isCloseTo(250.0 / 150, within(1e-6));

        var response = new CrossingPointsResponseDto();
        var crossings = new CrossingsPerTrackDto();
        crossings.setCrossings(List.of(crossing("A", 0), crossing("B", 120)));
        response.getCrossings().put(trackId, crossings);
        heartRateService.enrich(response);
        var average = crossings.getCrossings().getLast().getHeartRateSinceLastTriggerPoint();
        assertThat(average.averageBpm()).isEqualTo(150);
        assertThat(average.coveredSeconds()).isEqualTo(60); // Includes the STOP, independently of cardiac cost.
        assertThat(average.coveragePercent()).isEqualTo(50);
        assertThat(crossings.getSegmentHeartRateSummaries().getFirst().heartRate()).isEqualTo(average);
    }

    private long variant(long trackId, String type) {
        return jdbc.queryForObject("INSERT INTO gps_track_data (gps_track_id, track_type) VALUES (?, ?) RETURNING id", Long.class, trackId, type);
    }

    private static CrossingDto crossing(String name, int seconds) {
        var crossing = new CrossingDto();
        crossing.setTriggerPoint(new TriggerPoint(name, null));
        var point = new GpsTrackDataPointDto();
        point.setPointTimestamp(Timestamp.from(START.plusSeconds(seconds)));
        crossing.setGpsTrackDataPoint(point);
        return crossing;
    }

    private long point(long dataId, int index, int seconds, double energyWh) {
        return jdbc.queryForObject("""
                INSERT INTO gps_track_data_points (gps_track_data_id, point_index, point_index_max, moving_window_in_sec,
                    point_timestamp, speed_in_kmh_moving_window, energy_total_wh, motion_state, point_long_lat)
                VALUES (?, ?, 6, 30, ?, 0, ?, 'MOVING', ST_SetSRID(ST_MakePoint(8, 47), 4326)) RETURNING id
                """, Long.class, dataId, index, Timestamp.from(START.plusSeconds(seconds)), energyWh);
    }

    private static HeartRateSample sample(long source, int seconds, int bpm, String fingerprint) {
        Instant timestamp = START.plusSeconds(seconds);
        return new HeartRateSample(source, timestamp, timestamp.toString(), "+00:00", LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC),
                bpm, null, "WRIST", fingerprint);
    }
}
