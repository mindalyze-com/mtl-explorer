package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrackDataPoint;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataPointRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackDataRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.web.services.track.TrackQualityController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false",
        "mtl.denoise.algorithm=kalman", "mtl.media-correlation.initial-delay=PT1H"})
@Transactional
class GpsReliabilityDatabaseIntegrationTest {
    @Autowired
    GPXStoreService store;
    @Autowired
    GpsTrackDataRepository data;
    @Autowired
    GpsTrackDataPointRepository points;
    @Autowired
    GpsTrackRepository tracks;
    @Autowired
    TrackQualityController quality;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    jakarta.persistence.EntityManager entityManager;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;

    @Test
    void gapsStayDisconnectedInMetricsEveryLodAndSpatialQueries() {
        var first = GpsPipelineReliabilityTest.straight(30, 20, 1);
        var second = GpsPipelineReliabilityTest.straight(30, 20, 1);
        for (int i = 0; i < second.size(); i++) second.set(i, GpsPipelineReliabilityTest.c(20000 + i * 20, 0, 500, 3600 + i));
        long id = imported(GpsPipelineReliabilityTest.segment(first) + GpsPipelineReliabilityTest.segment(second));
        GpsTrack track = tracks.findById(id).orElseThrow();
        assertThat(track.getTrackLengthInMeter()).isBetween(1100d, 1250d);
        assertThat(track.getTrackDurationUnknownSecs()).isEqualTo(3571);
        assertThat(track.getTrackDurationStoppedSecs()).isZero();
        assertThat(track.getTrackDurationInMotionSecs()).isEqualTo(58);
        var canonical = data.findFirstByGpsTrackIdAndTrackType(id, "RAW_OUTLIER_CLEANED");
        assertThat(canonical.getRecordingBreakBeforePointIndexes()).containsExactly(30);
        var rows = points.getTrackDetailsByGpsTrackIdAndType(id, "RAW_OUTLIER_CLEANED");
        assertThat(rows.get(30).getIntervalStatus()).isEqualTo(GpsTrackDataPoint.IntervalStatus.UNKNOWN);
        assertThat(rows.get(30).getDistanceInMeterBetweenPoints()).isNull();
        assertThat(jdbc.queryForList("SELECT ST_NumGeometries(observed_path) FROM gps_track_data WHERE gps_track_id=? AND track_type <> 'RAW'", Integer.class, id))
                .allMatch(count -> count == 2).hasSize(5);
        assertThat(jdbc.queryForObject("SELECT bool_or(ST_DWithin(observed_path::geography, ST_SetSRID(ST_Point(8.13,47),4326)::geography,100)) FROM gps_track_data WHERE gps_track_id=? AND track_type='SIMPLIFIED_SHAPE'", Boolean.class, id)).isFalse();
        assertThat(quality.getTrackQuality(id).issues()).anyMatch(issue -> issue.reason().equals("UNKNOWN_INTERVAL"));
        assertThat(tracks.findSimilarTracksWithinTimeRangeForTrack(new Date(0), new Date(), 1000d, id)).isEmpty();
        // Execute the actual photo-selection queries, including their lateral geometry projections.
        tracks.excludeGoodTracksExceedingOffset(Integer.MAX_VALUE, 100);
        tracks.reEnablePhotoMatchedExcludedTracks(new Date(0), 100);
    }

    @Test
    void rawEvidenceAndMissingAltitudeSurvivePersistence() {
        var input = GpsPipelineReliabilityTest.straight(30, 20, 1);
        input.get(12).setZ(Double.NaN);
        String segment = GpsPipelineReliabilityTest.segment(input).replace("</trkpt>", "<fix>3d</fix><sat>5</sat><hdop>7</hdop><vdop>9</vdop></trkpt>");
        long id = imported(segment);
        var raw = data.findFirstByGpsTrackIdAndTrackType(id, "RAW");
        assertThat(raw.getTrack().getCoordinateN(12).getZ()).isNaN();
        assertThat(raw.getObservationEvidence()).hasSize(30);
        assertThat(raw.getObservationEvidence().get(12).decisions()).contains("ELEVATION_MISSING");
        assertThat(raw.getObservationEvidence().get(12).hdop()).isEqualTo(7);
        var report = quality.getTrackQuality(id);
        assertThat(report.processingSettings()).containsEntry("algorithm", "kalman");
        assertThat(report.rawCoordinates().get(12).get(2)).isNull();
        assertThat(report.issues()).anyMatch(issue -> issue.reason().equals("WEAK_SOURCE_FIX"));
    }

    private long imported(String segments) {
        String name = "synthetic-gps-reliability-" + UUID.randomUUID() + ".gpx";
        Long fileId = jdbc.queryForObject("INSERT INTO indexed_file(\"index\",name,path,full_path,indexer_status) VALUES('GPS',?,'/synthetic',?,'COMPLETED_WITH_SUCCESS') RETURNING id", Long.class, name, "/synthetic/" + name);
        IndexedFile file = entityManager.find(IndexedFile.class, fileId);
        var imported = store.readAndSave(file, "<gpx version=\"1.1\" creator=\"MTL Explorer synthetic test\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk>" + segments + "</trk></gpx>");
        assertThat(imported).hasSize(1);
        long id = imported.getFirst().gpsTrack.getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }
}
