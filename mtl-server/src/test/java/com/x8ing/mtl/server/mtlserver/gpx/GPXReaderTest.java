package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read a synthetic file through the real importer and persist each geometry variant.
 */
@SpringBootTest
@Import(NoScheduledJobs.class)
@Transactional
class GPXReaderTest {
    private static final int POINT_COUNT = 200;
    private static final Instant START = Instant.parse("2020-01-01T10:00:00Z");

    @TempDir
    Path directory;
    @Autowired
    GPXStoreService store;
    @Autowired
    IndexerRepository files;
    @Autowired
    JdbcTemplate jdbc;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void importGpxFilePreservesRawAndPersistsObservedGeometry(boolean includeOutlier) throws Exception {
        StringBuilder xml = new StringBuilder("""
                <gpx version="1.1" creator="MTL Explorer synthetic test" xmlns="http://www.topografix.com/GPX/1/1">
                <trk><name>Synthetic import</name><trkseg>
                """);
        for (int i = 0; i < POINT_COUNT; i++) {
            double longitude = includeOutlier && i == POINT_COUNT / 2 ? 10 : 8 + i * 0.0001;
            xml.append(String.format(Locale.ROOT,
                    "<trkpt lat=\"47\" lon=\"%.6f\"><ele>500</ele><time>%s</time></trkpt>",
                    longitude, START.plusSeconds(i * 5L)));
        }
        xml.append("</trkseg></trk></gpx>");
        Path source = Files.writeString(directory.resolve("synthetic-import.gpx"), xml);
        IndexedFile file = new IndexedFile();
        file.setIndex("GPS");
        file.setName(source.getFileName().toString());
        file.setPath(directory.toString());
        file.setFullPath(source.toString());
        file.setSize(Files.size(source));
        file.setLastModifiedDate(new Date());
        files.saveAndFlush(file);

        var results = store.readAndSave(file);

        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.gpsTrack.getLoadStatus()).isEqualTo(GpsTrack.LOAD_STATUS.SUCCESS);
        assertThat(result.gpsTrack.getDidFilterOutlierByDistance()).isEqualTo(includeOutlier);
        assertThat(result.trackRAW.getNumPoints()).isEqualTo(POINT_COUNT);
        assertThat(result.trackCleaned.getNumPoints()).isEqualTo(POINT_COUNT - (includeOutlier ? 1 : 0));
        assertThat(result.gpsTrack.getTrackLengthInMeter()).isBetween(1_400d, 1_800d);
        assertThat(jdbc.queryForObject("""
                SELECT ST_NPoints(track) FROM gps_track_data WHERE gps_track_id=? AND track_type='RAW'
                """, Integer.class, result.gpsTrack.getId())).isEqualTo(POINT_COUNT);
        assertThat(jdbc.queryForObject("""
                SELECT bool_and(observed_path IS NOT NULL AND NOT ST_IsEmpty(observed_path))
                FROM gps_track_data WHERE gps_track_id=?
                """, Boolean.class, result.gpsTrack.getId())).isTrue();
    }
}
