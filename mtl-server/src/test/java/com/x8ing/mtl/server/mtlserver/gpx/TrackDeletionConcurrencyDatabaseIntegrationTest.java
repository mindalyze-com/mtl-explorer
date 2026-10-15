package com.x8ing.mtl.server.mtlserver.gpx;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerKickoffService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false",
        "mtl.garmin-sync.enabled=false", "mtl.indexer.gps.live-watch-enabled=false"})
@Import(NoScheduledJobs.class)
class TrackDeletionConcurrencyDatabaseIntegrationTest {
    @TempDir
    Path sourceRoot;
    @Autowired
    GPXStoreService store;
    @Autowired
    GpxProcessingWorker processingWorker;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager em;
    @Autowired
    PlatformTransactionManager transactions;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;
    @MockitoBean
    MapServerKickoffService mapKickoff;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sourceDeletionCompletesDuringBackgroundTrackOrPointUpdate(boolean updatePoints) throws Exception {
        var tx = new TransactionTemplate(transactions);
        tx.setTimeout(30);
        long[] ids = tx.execute(status -> {
            String name = "synthetic-deletion-" + UUID.randomUUID() + ".gpx";
            long fileId = jdbc.queryForObject("""
                    INSERT INTO indexed_file("index",name,path,base_path,full_path,indexer_status)
                    VALUES ('GPS',?,'',?,?,'REMOVED') RETURNING id
                    """, Long.class, name, sourceRoot.toString(), sourceRoot.resolve(name).toString());
            var coordinates = List.of(GpsPipelineReliabilityTest.c(0, 0, 500, 0),
                    GpsPipelineReliabilityTest.c(30, 0, 501, 10),
                    GpsPipelineReliabilityTest.c(60, 0, 502, 20));
            var loaded = store.readAndSave(em.find(IndexedFile.class, fileId),
                    "<gpx version=\"1.1\" creator=\"MTL Explorer synthetic test\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk>"
                    + GpsPipelineReliabilityTest.segment(coordinates) + "</trk></gpx>");
            em.flush();
            return new long[]{fileId, loaded.getFirst().gpsTrack.getId()};
        });
        long fileId = ids[0], trackId = ids[1];
        var dataIds = jdbc.queryForList("SELECT id FROM gps_track_data WHERE gps_track_id=?", Long.class, trackId);
        var locked = new CountDownLatch(1);
        var update = new CountDownLatch(1);
        var backgroundPid = new AtomicInteger();
        var deletionPid = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var background = executor.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL statement_timeout='20s'");
                jdbc.execute("SET LOCAL deadlock_timeout='10s'");
                backgroundPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                // Match the parent-first lock held by a background track update or energy calculation.
                jdbc.queryForObject("SELECT id FROM gps_track WHERE id=? FOR NO KEY UPDATE", Long.class, trackId);
                locked.countDown();
                try {
                    assertThat(update.await(15, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while coordinating synthetic track update", exception);
                }
                if (updatePoints) {
                    jdbc.update("""
                            UPDATE gps_track_data_points SET energy_total_wh=1
                            WHERE gps_track_data_id IN (SELECT id FROM gps_track_data WHERE gps_track_id=?)
                            """, trackId);
                } else {
                    // Hibernate's full track UPDATE fires this real media-correlation queue trigger.
                    jdbc.update("UPDATE gps_track SET start_date=start_date WHERE id=?", trackId);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> tx.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL statement_timeout='20s'");
                jdbc.execute("SET LOCAL deadlock_timeout='500ms'");
                deletionPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                processingWorker.processDelete("GPS", fileId);
            }));
            try {
                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(20)).until(() ->
                        deletionPid.get() != 0 && Boolean.TRUE.equals(jdbc.queryForObject(
                                "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class,
                                backgroundPid.get(), deletionPid.get())));
            } finally {
                update.countDown();
            }
            background.get(25, TimeUnit.SECONDS);
            deletion.get(25, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE id=?", Long.class, trackId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track_data WHERE gps_track_id=?", Long.class, trackId)).isZero();
            for (long dataId : dataIds) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM gps_track_data_points WHERE gps_track_data_id=?",
                        Long.class, dataId)).isZero();
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM media_correlation_track_work WHERE track_id=?",
                    Long.class, trackId)).isZero();
            assertThat(jdbc.queryForObject("SELECT indexer_status FROM indexed_file WHERE id=?", String.class, fileId))
                    .isEqualTo("REMOVED");
            // A repeated filesystem notification remains harmless after the track is gone.
            processingWorker.processDelete("GPS", fileId);
        } finally {
            update.countDown();
            processingWorker.processDelete("GPS", fileId);
            jdbc.update("DELETE FROM indexed_file WHERE id=?", fileId);
        }
    }
}
