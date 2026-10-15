package com.x8ing.mtl.server.mtlserver.trackreview;

import com.x8ing.mtl.server.gpsquality.diagnostics.GpxDocument;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXStoreService;
import com.x8ing.mtl.server.mtlserver.gpx.GpxProcessingWorker;
import com.x8ing.mtl.server.mtlserver.gpx.TrackFileConverterService;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileChangedException;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerKickoffService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.x8ing.mtl.server.mtlserver.trackreview.TrackReviewModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/**
 * Actual source review, import transactions, and PostgreSQL RAW observations.
 */
@EnabledIfSystemProperty(named = "mtl.test.review.jdbc-url", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {"mtl.planner.enabled=false", "mtl.google-health.enabled=false", "mtl.garmin-sync.enabled=false"})
@Import(NoScheduledJobs.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SourceReviewImportIntegrationTest {
    private static final int COMPLEXITY_NESTING_DEPTH = 70;
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "mtl-review-import-" + UUID.randomUUID());
    @Autowired
    SourceReviewService reviews;
    @Autowired
    SourceReviewRepository reviewRepository;
    @Autowired
    GpxProcessingWorker importer;
    @Autowired
    IndexerRepository files;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    jakarta.persistence.EntityManager entityManager;
    @Autowired
    ApplicationContext context;
    @Autowired
    javax.sql.DataSource dataSource;
    @Autowired
    com.x8ing.mtl.server.mtlserver.web.services.track.TrackQualityController quality;
    @MockitoSpyBean
    GPXStoreService store;
    @MockitoBean
    GPXDirectoryWatcherService watcher;
    @MockitoBean
    MediaIndexerService mediaIndexer;
    @MockitoBean
    MapServerKickoffService mapKickoff;
    @MockitoBean
    TrackFileConverterService converter;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("mtl.test.review.jdbc-url"));
        registry.add("mtl.gpx-watch-directory", () -> ROOT.resolve("gpx").toString());
        registry.add("mtl.media-watch-directory", () -> ROOT.resolve("media").toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "moving-duplicates", "jitter", "backward", "forward-spike", "systemic", "legacy-contained", "normal-elevation-noise", "legacy-impact", "legacy-impact-limited", "gap", "empty"})
    void numericPolicySurvivesImportPersistenceAndReassessment(String scenario) throws Exception {
        var times = SyntheticTrackSources.regular(60);
        switch (scenario) {
            case "duplicate" -> times.set(20, times.get(19));
            case "moving-duplicates" -> {
                for (int i = 1; i < times.size(); i += 2) times.set(i, times.get(i - 1));
            }
            case "jitter" -> times.set(20, times.get(19).minusMillis(1000));
            case "backward", "legacy-contained" -> times.set(20, times.get(19).minusMillis(1001));
            case "forward-spike" -> times.set(20, times.get(20).plusSeconds(7200));
            case "systemic" -> {
                for (int i : new int[]{20, 30, 40}) times.set(i, times.get(i).minusSeconds(30));
            }
            case "gap" -> {
                for (int i = 30; i < times.size(); i++) times.set(i, times.get(i).plusSeconds(43200));
            }
            default -> { }
        }
        byte[] bytes = scenario.equals("empty") ? new byte[0] : SyntheticTrackSources.source(times);
        if (scenario.equals("normal-elevation-noise")) {
            StringBuilder xml = new StringBuilder("<gpx version='1.1' creator='synthetic'><trk><trkseg>");
            for (int i = 0; i < 301; i++) xml.append(String.format(java.util.Locale.ROOT,
                    "<trkpt lat='47' lon='%.10f'><ele>%d</ele><time>%s</time></trkpt>",
                    8 + i * 2 / 76000.0, 500 + (i % 2) * 2, SyntheticTrackSources.START.plusSeconds(i)));
            bytes = xml.append("</trkseg></trk></gpx>").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (scenario.equals("moving-duplicates")) {
            StringBuilder xml = new StringBuilder("<gpx version='1.1' creator='synthetic'><trk><trkseg>");
            for (int i = 0; i < times.size(); i++) xml.append(String.format(java.util.Locale.ROOT,
                    "<trkpt lat='47' lon='%.10f'><ele>500</ele><time>%s</time></trkpt>", 8 + i * 20 / 76000.0, times.get(i)));
            bytes = xml.append("</trkseg></trk></gpx>").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/policy-" + scenario + "-" + UUID.randomUUID() + ".gpx");
        Files.write(source, bytes);
        String largeSourceHash = null;
        if (scenario.equals("legacy-impact-limited")) {
            byte[] whitespace = new byte[8192];
            java.util.Arrays.fill(whitespace, (byte) ' ');
            try (var output = new java.io.BufferedOutputStream(Files.newOutputStream(source, java.nio.file.StandardOpenOption.APPEND))) {
                for (int n = 0; n < GpxDocument.MAX_SOURCE_BYTES; n += whitespace.length) output.write(whitespace);
            }
            largeSourceHash = GpxDocument.hash(Files.readAllBytes(source));
        }
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize(Files.size(source));
        indexed.setLastModifiedDate(new Date(Files.getLastModifiedTime(source).toMillis()));
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        boolean expectedReview = java.util.Set.of("backward", "forward-spike", "systemic", "legacy-contained", "gap", "empty").contains(scenario);
        GPXStoreService target = AopTestUtils.getUltimateTargetObject(store);
        try {
            if (scenario.equals("legacy-contained")) {
                // Reproduce stored output from the former alert/containment coupling using synthetic input.
                doAnswer(call -> {
                    java.util.List<com.x8ing.mtl.server.mtlserver.gpx.GPXReader.LoadResult> loaded = call.getArgument(1);
                    for (var result : loaded) {
                        var raw = java.util.Arrays.asList(result.trackRAW.getCoordinates());
                        var canonical = result.trackCleaned.getCoordinates();
                        for (var coordinate : canonical) coordinate.setM(Double.NaN);
                        result.trackCleaned = result.trackCleaned.getFactory().createLineString(canonical);
                        com.x8ing.mtl.server.mtlserver.gpx.SourceTimingContainment.preserveOriginal(result, raw, Date.from(times.getFirst()), 0);
                    }
                    return call.callRealMethod();
                }).when(target).saveLoadResults(any(IndexedFile.class), any(java.util.List.class), any(com.x8ing.mtl.server.mtlserver.utils.TimingCollector.class));
            }
            if (scenario.equals("empty")) reviews.assess(sourceId);
            else importCurrentSource(sourceId, false);
            if (scenario.equals("legacy-impact") || scenario.equals("legacy-impact-limited")) {
                var finding = new com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding(
                        com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Stage.ELEVATION,
                        com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.ELEVATION_CORRECTION,
                        com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Severity.REVIEW,
                        0, null, null, 0, 5000.0, "metres", "Retired aggregate comparison", java.util.List.of("Ascent", "Energy"));
                var oldIssue = new com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingIssue(null, null,
                        "LARGE_ELEVATION_CORRECTION", "canonical-ascent-impact", null, 5000.0, null, finding);
                jdbc.update("UPDATE gps_track_data d SET processing_issues=?::jsonb FROM gps_track t WHERE d.gps_track_id=t.id AND t.file_id=? AND d.track_type='RAW'",
                        new tools.jackson.databind.json.JsonMapper().writeValueAsString(java.util.List.of(oldIssue)), sourceId);
                var previous = reviewRepository.find(sourceId).orElseThrow();
                java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
                details.put("firstPointIndex", null);
                details.put("lastPointIndex", null);
                details.put("affectedCount", 0);
                var stored = new com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.Finding("ELEVATION",
                        com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.Reason.ELEVATION_CORRECTION,
                        com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueModels.Severity.REVIEW, 0,
                        com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository.hash("retired-summary"), finding.message(), details);
                new TransactionTemplate(transactions).executeWithoutResult(status -> context.getBean(com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository.class)
                        .sync(sourceId, com.x8ing.mtl.server.mtlserver.sourceissues.SourceIssueRepository.revision(previous.sourceHash()),
                                java.util.List.of(stored), java.util.Set.of("ELEVATION")));
                assertEquals(true, jdbc.queryForObject("SELECT count(*)>0 FROM source_issue WHERE source_id=? AND resolved_at IS NULL AND severity='REVIEW'", Boolean.class, sourceId));
                var reassessed = reviews.assess(sourceId).diagnosis();
                assertFalse(reassessed.needsReview());
                assertEquals(scenario.equals("legacy-impact-limited"), SourceReviewService.assessmentLimited(reassessed));
            }
            assertEquals(expectedReview, reviewRepository.find(sourceId).orElseThrow().diagnosis().needsReview());
            assertEquals(expectedReview, jdbc.queryForObject("SELECT count(*)>0 FROM source_issue WHERE source_id=? AND resolved_at IS NULL AND severity='REVIEW'", Boolean.class, sourceId));
            assertEquals(expectedReview, reviews.assess(sourceId).diagnosis().needsReview());
            if (scenario.equals("duplicate") || scenario.equals("moving-duplicates") || scenario.equals("jitter")) {
                assertEquals(true, jdbc.queryForObject("SELECT NOT timing_unreliable AND track_duration_in_motion_secs IS NOT NULL FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
                assertEquals(times.get(20).toEpochMilli() / 1000.0,
                        jdbc.queryForObject("SELECT ST_M(ST_PointN(d.track,21)) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW'", Double.class, sourceId), 0.001);
            }
            if (scenario.equals("gap")) assertEquals(false, jdbc.queryForObject("SELECT bool_or(timing_unreliable) FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
            if (scenario.equals("backward") || scenario.equals("forward-spike")) {
                assertEquals(true, jdbc.queryForObject("SELECT NOT timing_unreliable AND start_date<end_date AND motion_analysis_status='PARTIAL' FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
                assertTrue(jdbc.queryForObject("SELECT count(*) FROM gps_track_data_points p JOIN gps_track_data d ON d.id=p.gps_track_data_id JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW_OUTLIER_CLEANED' AND p.duration_between_points_in_sec>0", Long.class, sourceId) > 50);
                assertEquals(times.get(20).toEpochMilli() / 1000.0,
                        jdbc.queryForObject("SELECT ST_M(ST_PointN(d.track,21)) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW'", Double.class, sourceId), 0.001);
            }
            if (scenario.equals("systemic")) {
                assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable AND start_date=end_date FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
                assertTrue(reviews.assess(sourceId).diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.TIMING_UNRELIABLE));
            }
            if (scenario.equals("normal-elevation-noise")) {
                assertEquals(false, jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id, jsonb_array_elements(d.processing_issues) issue WHERE t.file_id=? AND issue->>'rule' LIKE '%-impact')", Boolean.class, sourceId));
            }
            if (scenario.equals("legacy-contained")) {
                assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable AND start_date=end_date FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
                assertTrue(reviews.assess(sourceId).diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.TIMING_UNRELIABLE));
                reset(target);
                importCurrentSource(sourceId, true);
                assertEquals(true, jdbc.queryForObject("SELECT NOT timing_unreliable AND start_date<end_date FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
                assertTrue(reviews.assess(sourceId).diagnosis().needsReview(), "The backward alert survives rebuilding timing");
                assertFalse(reviews.assess(sourceId).diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.TIMING_UNRELIABLE));
            }
            if (largeSourceHash == null) assertArrayEquals(bytes, Files.readAllBytes(source));
            else assertEquals(largeSourceHash, GpxDocument.hash(Files.readAllBytes(source)));
        } finally {
            reset(target);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void twoPausedConversionsLeaveTheDatabasePoolAvailable(boolean limitedAssessment) throws Exception {
        String xml = new String(SyntheticTrackSources.source(SyntheticTrackSources.regular(60)), java.nio.charset.StandardCharsets.UTF_8);
        String limited = xml.replace("</gpx>", "<extensions>" + "<nested>".repeat(COMPLEXITY_NESTING_DEPTH)
                + "</nested>".repeat(COMPLEXITY_NESTING_DEPTH) + "</extensions></gpx>");
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var firstCalls = new java.util.concurrent.ConcurrentHashMap<Path, AtomicInteger>();
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Conversion must not own a transaction");
            Path path = call.getArgument(0);
            if (limitedAssessment && firstCalls.computeIfAbsent(path, ignored -> new AtomicInteger()).getAndIncrement() == 0)
                return limited;
            entered.countDown();
            assertTrue(release.await(30, TimeUnit.SECONDS), "Conversion was not released");
            return xml;
        }).when(converter).convertToGpxForImport(any(), any());
        long first = convertedFixture();
        long second = convertedFixture();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> importer.processCreateOrChange("GPS", first, false));
            var two = executor.submit(() -> importer.processCreateOrChange("GPS", second, false));
            try {
                assertTrue(entered.await(20, TimeUnit.SECONDS), "Both workers must reach conversion");
                assertEquals(0, dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().getActiveConnections(), "Waiting conversions must release every connection");
                assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
            } finally {
                release.countDown();
            }
            one.get(30, TimeUnit.SECONDS);
            two.get(30, TimeUnit.SECONDS);
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id IN (?,?)", Integer.class, first, second));
            assertEquals(limitedAssessment, SourceReviewService.assessmentLimited(reviewRepository.find(first).orElseThrow().diagnosis()));
        } finally {
            reset(converter);
            deleteFixture(first);
            deleteFixture(second);
        }
    }

    @Test
    void interactiveAssessmentAndPreviewConvertWithoutHoldingAConnection() throws Exception {
        String xml = new String(SyntheticTrackSources.source(SyntheticTrackSources.regular(60)), java.nio.charset.StandardCharsets.UTF_8);
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getHikariPoolMXBean().getActiveConnections());
            return xml;
        }).when(converter).convertToGpx(any(), any());
        long sourceId = convertedFixture();
        try {
            var assessment = reviews.assess(sourceId);
            assertEquals(60, assessment.diagnosis().pointCount());
            assertEquals(60, reviews.detail(sourceId).originalPoints().size());
            assertEquals(60, reviews.points(sourceId, assessment.source().hash(), 0, 100).points().size());
        } finally {
            reset(converter);
            deleteFixture(sourceId);
        }
    }

    @Test
    void changingAConvertedSourceDiscardsItsAssessmentAndRetainsCommittedTracks() throws Exception {
        String xml = new String(SyntheticTrackSources.source(SyntheticTrackSources.regular(60)), java.nio.charset.StandardCharsets.UTF_8);
        long sourceId = convertedFixture();
        org.mockito.Mockito.when(converter.convertToGpxForImport(any(), any())).thenReturn(xml);
        try {
            importer.processCreateOrChange("GPS", sourceId, false);
            var previousTracks = jdbc.queryForList("SELECT id FROM gps_track WHERE file_id=?", Long.class, sourceId);
            var previousDiagnosis = reviewRepository.find(sourceId).orElseThrow();
            doAnswer(call -> {
                Files.writeString(call.getArgument(0, Path.class), "changed synthetic source");
                return xml;
            }).when(converter).convertToGpxForImport(any(), any());
            assertThrows(SourceFileChangedException.class, () -> importer.processCreateOrChange("GPS", sourceId, true));
            assertEquals(previousTracks, jdbc.queryForList("SELECT id FROM gps_track WHERE file_id=?", Long.class, sourceId));
            assertEquals(previousDiagnosis, reviewRepository.find(sourceId).orElseThrow());
        } finally {
            reset(converter);
            deleteFixture(sourceId);
        }
    }

    @Test
    void conversionFailurePersistsDiagnosisWithoutCreatingTracks() throws Exception {
        long sourceId = convertedFixture();
        org.mockito.Mockito.when(converter.convertToGpxForImport(any(), any())).thenThrow(new java.io.IOException("Synthetic timeout"));
        try {
            var failure = assertThrows(RuntimeException.class, () -> importer.processCreateOrChange("GPS", sourceId, false));
            assertTrue(failure.getMessage().contains("Source could not be parsed"));
            assertTrue(reviewRepository.find(sourceId).orElseThrow().diagnosis().findings().stream()
                    .anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.CONVERSION_FAILED));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=?", Integer.class, sourceId));
        } finally {
            reset(converter);
            deleteFixture(sourceId);
        }
    }

    @Test
    void anExistingTransactionIsRejectedBeforeAssessmentOrConversion() throws Exception {
        long sourceId = convertedFixture();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT 1", Integer.class);
                assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                        () -> importer.processCreateOrChange("GPS", sourceId, false));
                assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                        () -> reviews.assess(sourceId));
                assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                        () -> reviews.assessForImport(sourceId));
                assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                        () -> reviews.detail(sourceId));
            });
            org.mockito.Mockito.verifyNoInteractions(converter);
            assertTrue(reviewRepository.find(sourceId).isEmpty());
        } finally {
            deleteFixture(sourceId);
        }
    }

    private long convertedFixture() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = Files.writeString(ROOT.resolve("gpx/synthetic-" + UUID.randomUUID() + ".tcx"), "synthetic original TCX");
        var indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize(Files.size(source));
        indexed.setLastModifiedDate(new Date(Files.getLastModifiedTime(source).toMillis()));
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        return files.saveAndFlush(indexed).getId();
    }

    private void deleteFixture(long sourceId) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var file = files.findById(sourceId).orElseThrow();
            store.deleteWithAllDependencies(file);
            files.delete(file);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOffsetsAndInvalidDatesKeepDistinctTimingEligibilityAfterImport(boolean invalidDates) throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-timestamps-" + UUID.randomUUID() + ".gpx");
        String xml = new String(SyntheticTrackSources.source(SyntheticTrackSources.regular(60)),
                java.nio.charset.StandardCharsets.UTF_8).replace("Z</time>", ".00</time>");
        if (invalidDates) xml = xml.replaceAll("<time>[^<]*</time>", "<time>2026-02-30T10:00:00Z</time>");
        byte[] original = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, original);
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize((long) original.length);
        indexed.setLastModifiedDate(Date.from(SyntheticTrackSources.START));
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        try {
            importCurrentSource(sourceId, false);
            var diagnosis = reviewRepository.find(sourceId).orElseThrow().diagnosis();
            assertEquals(invalidDates ? 0 : 60, diagnosis.sections().getFirst().timedPointCount());
            assertEquals(invalidDates, jdbc.queryForObject("SELECT timing_unreliable FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
            assertEquals(false, jdbc.queryForObject("SELECT timing_estimated FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
            assertEquals(60, jdbc.queryForObject("""
                    SELECT ST_NPoints(d.track) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id
                    WHERE t.file_id=? AND d.track_type='RAW'
                    """, Integer.class, sourceId));
            Double firstTime = jdbc.queryForObject("""
                    SELECT ST_M(ST_StartPoint(d.track)) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id
                    WHERE t.file_id=? AND d.track_type='RAW'
                    """, Double.class, sourceId);
            if (invalidDates) assertTrue(firstTime == null || firstTime.isNaN());
            else {
                assertEquals(SyntheticTrackSources.START.getEpochSecond(), firstTime);
                assertEquals(true, jdbc.queryForObject("SELECT track_duration_in_motion_secs>0 FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
            }
            assertArrayEquals(original, Files.readAllBytes(source));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    @Test
    void cleaningEvidenceIsPersistedAndReusedOnlyForTheImportedRevision() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-cleaning-" + UUID.randomUUID() + ".gpx");
        StringBuilder xml = new StringBuilder("<gpx version='1.1' creator='synthetic' xmlns='http://www.topografix.com/GPX/1/1'><trk><trkseg>");
        for (int index = 0; index < 30; index++)
            xml.append(String.format(java.util.Locale.ROOT,
                    "<trkpt lat='%s' lon='%.10f'><ele>%s</ele><time>%s</time></trkpt>",
                    index == 3 ? "999" : "47", 8 + index * 20 / 76000.0, index == 12 ? "560" : "500",
                    SyntheticTrackSources.START.plusSeconds(index)));
        byte[] original = xml.append("</trkseg></trk></gpx>").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(source, original);
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize((long) original.length);
        indexed.setLastModifiedDate(new Date());
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        try {
            var before = reviews.assess(sourceId);
            assertTrue(before.diagnosis().findings().stream().noneMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.ELEVATION_SPIKE));
            importCurrentSource(sourceId, false);
            var imported = reviewRepository.find(sourceId).orElseThrow();
            var spike = imported.diagnosis().findings().stream().filter(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.ELEVATION_SPIKE).findFirst().orElseThrow();
            assertEquals(12, spike.firstPointIndex(), "Use source numbering, including the skipped invalid observation.");
            assertEquals(60, spike.magnitude());
            var recorded = reviewRepository.processingIssues(sourceId, imported.sourceHash());
            assertEquals(spike, com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingAssessment.reviewFindings(recorded).stream()
                    .filter(f -> f.reason() == spike.reason()).findFirst().orElseThrow());
            long trackId = reviewRepository.tracks(sourceId).getFirst().trackId();
            var report = quality.getTrackQuality(trackId);
            assertEquals(com.x8ing.mtl.server.mtlserver.gpx.GpsProcessingAssessment.VERSION, report.processingSettings().get("assessment"));
            assertEquals("10.0", report.processingSettings().get("elevation.maximumVerticalRateMetresPerSecond"));
            assertTrue(report.issues().stream().anyMatch(i -> "ELEVATION_REJECTED".equals(i.reason()) && i.rawPointIndex() == 11));
            assertEquals(12, report.observationEvidence().get(11).sourcePointIndex());
            assertEquals(imported.diagnosis().findings(), reviews.assess(sourceId).diagnosis().findings());

            var stored = jdbc.queryForObject("SELECT ST_AsEWKB(track) FROM gps_track_data WHERE gps_track_id=? AND track_type='RAW_OUTLIER_CLEANED'", byte[].class, trackId);
            Files.writeString(source, "\n", java.nio.file.StandardOpenOption.APPEND);
            var changed = reviews.assess(sourceId);
            assertTrue(changed.diagnosis().findings().stream().noneMatch(f -> f.reason() == spike.reason()), "Old import evidence cannot describe a new source revision.");
            assertTrue(reviewRepository.processingIssues(sourceId, changed.source().hash()).isEmpty());
            assertArrayEquals(stored, jdbc.queryForObject("SELECT ST_AsEWKB(track) FROM gps_track_data WHERE gps_track_id=? AND track_type='RAW_OUTLIER_CLEANED'", byte[].class, trackId));
            assertTrue(quality.getTrackQuality(trackId).issues().stream().anyMatch(i -> "ELEVATION_REJECTED".equals(i.reason())));
            importCurrentSource(sourceId, true);
            assertTrue(reviews.assess(sourceId).diagnosis().findings().stream().anyMatch(f -> f.reason() == spike.reason() && f.firstPointIndex() == 12));
            assertArrayEquals(original, java.util.Arrays.copyOf(Files.readAllBytes(source), original.length));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    @Test
    void complexityLimitedImportContainsCorruptClocksAndPersistsOriginalObservations() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-complex-clock-" + UUID.randomUUID() + ".gpx");
        var bad = SyntheticTrackSources.regular(240);
        for (int i = 60; i < 180; i += 3) bad.set(i, bad.get(i).minusSeconds(9 * 86400L));
        bad.set(100, null);
        var sameDay = SyntheticTrackSources.regular(240);
        for (int i = 60; i < 180; i += 3) sameDay.set(i, sameDay.get(i).minusSeconds(6 * 3600L));
        var healthy = SyntheticTrackSources.regular(120);
        byte[] original = new String(SyntheticTrackSources.document(SyntheticTrackSources.section(bad, 0),
                SyntheticTrackSources.section(sameDay, 300), SyntheticTrackSources.section(healthy, 600)), java.nio.charset.StandardCharsets.UTF_8)
                // Unused extension nodes are streamed; nesting still has an explicit review limit.
                .replace("</gpx>", "<extensions>" + "<example:extra>".repeat(COMPLEXITY_NESTING_DEPTH)
                                   + "</example:extra>".repeat(COMPLEXITY_NESTING_DEPTH)
                                   + "</extensions></gpx>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var limit = assertThrows(GpxDocument.ReviewLimitExceeded.class, () -> GpxDocument.parse(original));
        assertTrue(limit.getMessage().contains("XML nesting limit"));
        Files.write(source, original);
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize((long) original.length);
        indexed.setLastModifiedDate(new Date());
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        try {
            importCurrentSource(sourceId, false);
            assertTrue(SourceReviewService.assessmentLimited(reviewRepository.find(sourceId).orElseThrow().diagnosis()));
            assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=?", Integer.class, sourceId));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=? AND timing_unreliable AND NOT timing_estimated AND review_status='NOT_ASSESSED' AND start_date=end_date", Integer.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT NOT timing_unreliable AND NOT review_excluded AND review_status='NOT_ASSESSED' AND track_duration_in_motion_secs=595 FROM gps_track WHERE file_id=? AND source_recording_section=2", Boolean.class, sourceId));
            var recordings = java.util.List.of(bad, sameDay, healthy);
            for (int section = 0; section < recordings.size(); section++) {
                var times = recordings.get(section);
                var storedTimes = jdbc.queryForList("SELECT ST_M((p).geom) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id CROSS JOIN LATERAL ST_DumpPoints(d.track) p WHERE t.file_id=? AND t.source_recording_section=? AND d.track_type='RAW' ORDER BY (p).path", Double.class, sourceId, section);
                assertEquals(times.size(), storedTimes.size());
                for (int i = 0; i < times.size(); i++) {
                    if (times.get(i) == null) assertTrue(Double.isNaN(storedTimes.get(i)));
                    else assertEquals((double) times.get(i).getEpochSecond(), storedTimes.get(i));
                }
            }
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND t.timing_unreliable AND d.track_type='RAW_OUTLIER_CLEANED' AND d.recording_break_before_point_indexes<>'[]'::jsonb", Integer.class, sourceId));
            var reassessed = reviews.assess(sourceId);
            assertEquals(java.util.List.of(ReviewState.NOT_ASSESSED, ReviewState.NOT_ASSESSED, ReviewState.NOT_ASSESSED),
                    reassessed.sections().stream().map(SourceSectionReview::state).toList());
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=? AND timing_unreliable", Integer.class, sourceId));
            var raw = jdbc.queryForList("SELECT ST_AsEWKB(d.track) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW' ORDER BY t.source_recording_section", byte[].class, sourceId);
            reviews.decide(sourceId, new ReviewDecisionRequest(reassessed.source().hash(), 0, Decision.INCLUDE));
            importCurrentSource(sourceId, true);
            assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=?", Integer.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT timing_unreliable AND NOT timing_estimated AND NOT review_excluded AND review_status='INCLUDED' FROM gps_track WHERE file_id=? AND source_recording_section=0", Boolean.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT NOT timing_unreliable AND track_duration_in_motion_secs=595 FROM gps_track WHERE file_id=? AND source_recording_section=2", Boolean.class, sourceId));
            var reimportedRaw = jdbc.queryForList("SELECT ST_AsEWKB(d.track) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW' ORDER BY t.source_recording_section", byte[].class, sourceId);
            for (int section = 0; section < raw.size(); section++) assertArrayEquals(raw.get(section), reimportedRaw.get(section));
            assertArrayEquals(original, Files.readAllBytes(source));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    @Test
    void limitedReimportPersistsEstimatedTimingAndSectionDecisionsWithoutChangingTheHealthySection() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-large-repaired-" + UUID.randomUUID() + ".gpx");
        byte[] repaired = SyntheticTrackSources.document(SyntheticTrackSources.estimatedSection(
                        SyntheticTrackSources.regular(200).stream().map(t -> t.plusSeconds(3600)).toList(), 0),
                SyntheticTrackSources.section(SyntheticTrackSources.regular(200), 300));
        byte[] whitespace = new byte[8192];
        java.util.Arrays.fill(whitespace, (byte) ' ');
        try (var output = new java.io.BufferedOutputStream(Files.newOutputStream(source))) {
            output.write(repaired);
            for (int n = 0; n < GpxDocument.MAX_SOURCE_BYTES; n += whitespace.length) output.write(whitespace);
        }
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize(Files.size(source));
        indexed.setLastModifiedDate(new Date());
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        try {
            importCurrentSource(sourceId, false);
            var assessed = reviews.assess(sourceId);
            assertTrue(SourceReviewService.assessmentLimited(assessed.diagnosis()));
            assertTrue(assessed.diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.TIME_ESTIMATED));
            assertFalse(assessed.diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.TIMING_UNRELIABLE));
            assertEquals(java.util.List.of(ReviewState.NOT_ASSESSED, ReviewState.NOT_ASSESSED),
                    assessed.sections().stream().map(SourceSectionReview::state).toList());
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=?", Integer.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND timing_estimated AND timing_unreliable FROM gps_track WHERE file_id=? AND source_recording_section=0", Boolean.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND NOT timing_estimated AND NOT timing_unreliable AND track_duration_in_motion_secs=995 FROM gps_track WHERE file_id=? AND source_recording_section=1", Boolean.class, sourceId));
            var raw = jdbc.queryForList("SELECT t.source_recording_section,ST_AsEWKB(d.track) AS geometry FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW' ORDER BY t.source_recording_section", sourceId);
            assertEquals((double) SyntheticTrackSources.START.plusSeconds(3600).getEpochSecond(), jdbc.queryForObject("SELECT ST_M(ST_StartPoint(d.track)) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND t.source_recording_section=0 AND d.track_type='RAW'", Double.class, sourceId));

            reviews.decide(sourceId, new ReviewDecisionRequest(assessed.source().hash(), 0, Decision.INCLUDE));
            importCurrentSource(sourceId, true);
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND timing_estimated AND timing_unreliable AND review_status='INCLUDED' FROM gps_track WHERE file_id=? AND source_recording_section=0", Boolean.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND NOT timing_unreliable AND track_duration_in_motion_secs=995 FROM gps_track WHERE file_id=? AND source_recording_section=1", Boolean.class, sourceId));
            var reimportedRaw = jdbc.queryForList("SELECT t.source_recording_section,ST_AsEWKB(d.track) AS geometry FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW' ORDER BY t.source_recording_section", sourceId);
            for (int n = 0; n < raw.size(); n++) assertArrayEquals((byte[]) raw.get(n).get("geometry"), (byte[]) reimportedRaw.get(n).get("geometry"));

            // A changed revision cannot inherit use settings. The informational assessment limit stays incomplete.
            Files.writeString(source, "\n", java.nio.file.StandardOpenOption.APPEND);
            importCurrentSource(sourceId, true);
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND timing_estimated AND timing_unreliable AND review_status='NOT_ASSESSED' FROM gps_track WHERE file_id=? AND source_recording_section=0", Boolean.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND NOT timing_unreliable AND track_duration_in_motion_secs=995 FROM gps_track WHERE file_id=? AND source_recording_section=1", Boolean.class, sourceId));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    @Test
    void processingFailureSurvivesRollbackAndReassessmentUntilSuccessfulImport() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-import-failure-" + UUID.randomUUID() + ".gpx");
        byte[] original = SyntheticTrackSources.source(SyntheticTrackSources.regular(200));
        Files.write(source, original);
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize((long) original.length);
        indexed.setLastModifiedDate(new Date());
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        AtomicBoolean insertedRaw = new AtomicBoolean();
        GPXStoreService storeTarget = AopTestUtils.getUltimateTargetObject(store);
        try {
            doAnswer(call -> {
                call.callRealMethod();
                entityManager.flush();
                assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW'", Integer.class, sourceId));
                insertedRaw.set(true);
                throw new IllegalStateException("Synthetic failure after RAW insertion for a healthy source.");
            }).when(storeTarget).saveLoadResults(any(IndexedFile.class), any(java.util.List.class), any(com.x8ing.mtl.server.mtlserver.utils.TimingCollector.class));
            assertThrows(IllegalStateException.class, () -> importCurrentSource(sourceId, false));
            assertTrue(insertedRaw.get());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM gps_track WHERE file_id=?", Integer.class, sourceId));
            assertArrayEquals(original, Files.readAllBytes(source));
            var assessment = reviewRepository.find(sourceId).orElseThrow();
            assertTrue(assessment.diagnosis().needsReview());
            assertTrue(assessment.diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.PROCESSING_FAILED));
            assertTrue(reviews.assess(sourceId).diagnosis().needsReview(), "Reading the same file again cannot claim that the failed import succeeded.");

            reset(storeTarget);
            importCurrentSource(sourceId, false);
            assertFalse(reviewRepository.find(sourceId).orElseThrow().diagnosis().needsReview());
            assertTrue(reviewRepository.find(sourceId).orElseThrow().diagnosis().findings().stream()
                    .noneMatch(f -> f.stage() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Stage.PROCESSING));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND NOT timing_unreliable AND track_duration_in_motion_secs > 0 FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
        } finally {
            reset(storeTarget);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    @Test
    void processingDateMismatchIsPersistedAndReimportKeepsHealthyRecordedTimestamps() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-derived-dates-" + UUID.randomUUID() + ".gpx");
        byte[] original = SyntheticTrackSources.source(SyntheticTrackSources.regular(200));
        Files.write(source, original);
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize((long) original.length);
        indexed.setLastModifiedDate(new Date());
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        GPXStoreService target = AopTestUtils.getUltimateTargetObject(store);
        try {
            doAnswer(call -> {
                @SuppressWarnings("unchecked") var loaded = (java.util.List<com.x8ing.mtl.server.mtlserver.gpx.GPXReader.LoadResult>) call.callRealMethod();
                loaded.getFirst().gpsTrack.setStartDate(Date.from(SyntheticTrackSources.START.minusSeconds(7 * 86400L)));
                return loaded;
            }).when(target).saveLoadResults(any(IndexedFile.class), any(java.util.List.class), any(com.x8ing.mtl.server.mtlserver.utils.TimingCollector.class));
            importCurrentSource(sourceId, false);
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND timing_unreliable FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
            assertEquals((double) SyntheticTrackSources.START.getEpochSecond(), jdbc.queryForObject("SELECT ST_M(ST_StartPoint(d.track)) FROM gps_track_data d JOIN gps_track t ON t.id=d.gps_track_id WHERE t.file_id=? AND d.track_type='RAW'", Double.class, sourceId));
            var diagnosed = reviews.assess(sourceId);
            assertTrue(diagnosed.diagnosis().findings().stream().anyMatch(f -> f.reason() == com.x8ing.mtl.server.gpsquality.diagnostics.TrackFinding.Reason.INCONSISTENT_TIME_DOMAIN));
            assertFalse(SourceImportPlan.requiresContainment(diagnosed.diagnosis()));
            reset(target);
            importCurrentSource(sourceId, true);
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND NOT timing_unreliable AND NOT timing_estimated AND track_duration_in_motion_secs=995 FROM gps_track WHERE file_id=?", Boolean.class, sourceId));
            assertFalse(reviewRepository.find(sourceId).orElseThrow().diagnosis().needsReview());
            assertArrayEquals(original, Files.readAllBytes(source));
        } finally {
            reset(target);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

    private void importCurrentSource(long sourceId, boolean changed) throws Exception {
        // Model the indexer's committed scheduling revision before invoking its worker.
        IndexedFile file = files.findById(sourceId).orElseThrow();
        Path source = Path.of(file.getFullPath());
        file.setSize(Files.size(source));
        file.setLastModifiedDate(new Date(Files.getLastModifiedTime(source).toMillis()));
        files.saveAndFlush(file);
        importer.processCreateOrChange("GPS", sourceId, changed);
    }

    @AfterAll
    static void cleanupFiles() throws Exception {
        if (!Files.exists(ROOT)) return;
        try (var paths = Files.walk(ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    @Test
    void recordedSectionsKeepIndependentMapAndStatisticsSettingsAfterImport() throws Exception {
        Files.createDirectories(ROOT.resolve("gpx"));
        Path source = ROOT.resolve("gpx/synthetic-section-settings-" + UUID.randomUUID() + ".gpx");
        var times = SyntheticTrackSources.regular(90);
        byte[] original = SyntheticTrackSources.document(SyntheticTrackSources.section(times, 0),
                SyntheticTrackSources.section(times.stream().map(t -> t.plusSeconds(86400)).toList(), 90));
        Files.write(source, original);
        IndexedFile indexed = new IndexedFile();
        indexed.setIndex("GPS");
        indexed.setName(source.getFileName().toString());
        indexed.setPath("");
        indexed.setFullPath(source.toString());
        indexed.setSize((long) original.length);
        indexed.setLastModifiedDate(new Date());
        indexed.setIndexerStatus(IndexedFile.IndexerStatus.COMPLETED_WITH_SUCCESS);
        long sourceId = files.saveAndFlush(indexed).getId();
        try {
            importCurrentSource(sourceId, false);
            assertEquals(java.util.List.of(0, 1), jdbc.queryForList(
                    "SELECT source_recording_section FROM gps_track WHERE file_id=? ORDER BY source_recording_section", Integer.class, sourceId));
            String hash = reviewRepository.find(sourceId).orElseThrow().sourceHash();
            reviews.decide(sourceId, new ReviewDecisionRequest(hash, 1, Decision.EXCLUDE_MAP));
            reviews.decide(sourceId, new ReviewDecisionRequest(hash, 0, Decision.EXCLUDE_STATISTICS));
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND review_map_excluded FROM gps_track WHERE file_id=? AND source_recording_section=1", Boolean.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT review_excluded AND NOT review_map_excluded FROM gps_track WHERE file_id=? AND source_recording_section=0", Boolean.class, sourceId));
            importCurrentSource(sourceId, true);
            assertEquals(true, jdbc.queryForObject("SELECT NOT review_excluded AND review_map_excluded FROM gps_track WHERE file_id=? AND source_recording_section=1", Boolean.class, sourceId));
            assertEquals(true, jdbc.queryForObject("SELECT review_excluded AND NOT review_map_excluded FROM gps_track WHERE file_id=? AND source_recording_section=0", Boolean.class, sourceId));
            assertArrayEquals(original, Files.readAllBytes(source));
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                IndexedFile current = files.findById(sourceId).orElseThrow();
                store.deleteWithAllDependencies(current);
                files.delete(current);
            });
        }
    }

}
