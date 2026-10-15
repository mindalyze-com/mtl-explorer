package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.TrackScopeService;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "mtl.planner.enabled=false",
        "mtl.media-correlation.initial-delay=PT1H",
        "mtl.media-correlation.run-schedule=PT1H",
        "mtl.indexer.gps.live-watch-enabled=false",
        "mtl.indexer.media.live-watch-enabled=false"
})
class MirrorRepositoryDatabaseIntegrationTest {

    @Autowired
    private MirrorRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private GpsTrackRepository gpsTrackRepository;

    @Autowired
    private TrackScopeService trackScopeService;

    @Autowired
    @Qualifier("primaryDataSource")
    private DataSource primaryDataSource;

    @Autowired
    @Qualifier("readOnlyDataSource")
    private DataSource readOnlyDataSource;

    @MockitoBean
    private GPXDirectoryWatcherService gpxDirectoryWatcherService;

    @MockitoBean
    private MediaIndexerService mediaIndexerService;

    @Test
    void bothRealDatabasePoolsDisableJit() {
        assertThat(new JdbcTemplate(primaryDataSource).queryForObject("SHOW jit", String.class)).isEqualTo("off");
        assertThat(new JdbcTemplate(readOnlyDataSource).queryForObject("SHOW jit", String.class)).isEqualTo("off");
    }

    @Test
    @Transactional
    void recordPageProjectsMeasurementFieldsWithoutLoadingProviderPayloads() {
        repository.disconnect();
        Instant day = Instant.parse("2045-08-02T00:00:00Z");
        repository.upsertDataPoints(List.of(dailyPoint(hex('e'), hex('f'), day,
                "{\"steps\":{\"countSum\":\"0\",\"providerExtension\":true},\"extra\":\"retained\"}")), UUID.randomUUID());
        var row = repository.findRecords("steps", null, day, day.plusSeconds(86400), null, null, null, null, 50).getFirst();
        assertThat(row.rawJson()).isNull();
        assertThat(row.valueJson()).contains("countSum", "\"0\"").doesNotContain("providerExtension", "extra");
        assertThat(repository.findRecord(row.kind(), row.id()).orElseThrow().rawJson())
                .contains("providerExtension", "retained");
    }

    @Test
    void reconciliationRunMarkersDoNotRequireAnIndexUpdateForEveryObservation() {
        assertThat(jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'google_health'
                   AND tablename IN ('data_point', 'heart_rate_sample')
                   AND indexdef LIKE '%(last_seen_run)%'
                """, String.class)).isEmpty();
    }

    @Test
    void unfilteredRecordBrowserAcceptsNullFiltersWhenTheMirrorIsEmpty() {
        assertThat(repository.findRecords(
                null, null, null, null, null, null, null, null, 26))
                .isEmpty();
    }

    @Test
    @Transactional
    void recordBrowserPagesAcrossDataPointsAndHeartRateSamplesWithTheSameTime() {
        Instant sharedTime = Instant.parse("2045-08-02T12:00:00Z");
        UUID runId = UUID.randomUUID();
        repository.disconnect();
        long sourceId = repository.upsertDataSource(
                hex('a'), "FITBIT", "AUTOMATIC", "Fitbit", "Pixel Watch", "{\"platform\":\"FITBIT\"}");
        repository.upsertDataPoints(List.of(
                intervalPoint(
                        hex('b'), hex('c'), sourceId, sharedTime, sharedTime.plusSeconds(60),
                        "{\"steps\":{\"count\":10}}"),
                intervalPoint(
                        hex('d'), hex('e'), sourceId, sharedTime.minusSeconds(60), sharedTime,
                        "{\"steps\":{\"count\":5}}")), runId);
        repository.upsertHeartRateSamples(List.of(new HeartRateSample(
                sourceId, sharedTime, sharedTime.toString(), null, null,
                61, null, null, hex('f'))), runId);

        List<RecordRow> firstPage = repository.findRecords(
                null, null, null, null, null, null, null, null, 1);
        assertThat(firstPage).singleElement().satisfies(record -> {
            assertThat(record.kind()).isEqualTo(RecordRow.RecordKind.DATA_POINT);
            assertThat(record.recordTime()).isEqualTo(sharedTime);
            assertThat(record.rawJson()).isNull();
        });

        RecordRow cursor = firstPage.getFirst();
        assertThat(repository.findRecords(
                null, null, null, null, null,
                cursor.recordTime(), 0, cursor.id(), 2))
                .extracting(RecordRow::kind, RecordRow::recordTime)
                .containsExactly(
                        Tuple.tuple(RecordRow.RecordKind.HEART_RATE_SAMPLE, sharedTime),
                        Tuple.tuple(
                                RecordRow.RecordKind.DATA_POINT, sharedTime.minusSeconds(60)));
    }

    @Test
    @Transactional
    void mirrorSummaryCombinesCivilDateRowsAndHeartRateSamples() {
        repository.disconnect();
        repository.deleteMirrorRows();
        assertThat(repository.findMirrorSummary()).isEqualTo(MirrorSummaryRow.empty());

        Instant day = Instant.parse("2042-08-02T00:00:00Z");
        Instant sampleTime = day.plusSeconds(43_200);
        UUID runId = UUID.randomUUID();
        repository.upsertDataPoints(List.of(dailyPoint(
                hex('a'), hex('b'), day, "{\"steps\":{\"countSum\":\"42\"}}")), runId);
        long sourceId = repository.upsertDataSource(
                hex('c'), "FITBIT", "AUTOMATIC", "Fitbit", "Pixel Watch", "{\"platform\":\"FITBIT\"}");
        repository.upsertHeartRateSamples(List.of(new HeartRateSample(
                sourceId, sampleTime, sampleTime.toString(), null, null,
                65, null, null, hex('d'))), runId);

        assertThat(repository.findMirrorSummary()).isEqualTo(
                new MirrorSummaryRow(2L, day, sampleTime));
    }

    @Test
    @Transactional
    void summariesIncludeAZeroRecordTypeWithSyncState() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        repository.disconnect();
        repository.startWindow(
                "sleep", "RAW", start, start.plusSeconds(3600), UUID.randomUUID(), false);
        repository.markSyncError("sleep", "RAW", "temporary test error");

        assertThat(repository.findTypeSummaries())
                .anySatisfy(summary -> {
                    assertThat(summary.dataType()).isEqualTo("sleep");
                    assertThat(summary.representation()).isEqualTo("RAW");
                    assertThat(summary.recordCount()).isZero();
                    assertThat(summary.lastError()).isEqualTo("temporary test error");
                });
    }

    @Test
    @Transactional
    void typeSummariesKeepExactCountsAndTimeBoundsForBothStorageTables() {
        repository.disconnect();
        repository.deleteMirrorRows();
        Instant day = Instant.parse("2042-08-02T00:00:00Z");
        Instant laterDay = day.plusSeconds(86_400);
        UUID runId = UUID.randomUUID();
        repository.upsertDataPoints(List.of(
                dailyPoint(hex('a'), hex('b'), day, "{\"steps\":{\"countSum\":\"42\"}}"),
                dailyPoint(hex('c'), hex('d'), laterDay, "{\"steps\":{\"countSum\":\"21\"}}")), runId);
        long sourceId = repository.upsertDataSource(
                hex('e'), "FITBIT", "AUTOMATIC", "Fitbit", "Pixel Watch", "{\"platform\":\"FITBIT\"}");
        repository.upsertHeartRateSamples(List.of(
                new HeartRateSample(sourceId, day.plusSeconds(60), day.plusSeconds(60).toString(),
                        null, null, 61, null, null, hex('f')),
                new HeartRateSample(sourceId, laterDay.plusSeconds(60), laterDay.plusSeconds(60).toString(),
                        null, null, 62, null, null, hex('0'))), runId);

        assertThat(repository.findTypeSummaries())
                .anySatisfy(summary -> {
                    assertThat(summary.dataType()).isEqualTo("steps");
                    assertThat(summary.representation()).isEqualTo("DAILY_ROLLUP");
                    assertThat(summary.recordCount()).isEqualTo(2);
                    assertThat(summary.earliestRecord()).isEqualTo(day);
                    assertThat(summary.latestRecord()).isEqualTo(laterDay);
                })
                .anySatisfy(summary -> {
                    assertThat(summary.dataType()).isEqualTo("heart-rate");
                    assertThat(summary.representation()).isEqualTo("HEART_RATE_SAMPLE");
                    assertThat(summary.recordCount()).isEqualTo(2);
                    assertThat(summary.earliestRecord()).isEqualTo(day.plusSeconds(60));
                    assertThat(summary.latestRecord()).isEqualTo(laterDay.plusSeconds(60));
                });
    }

    @Test
    @Transactional
    void pageProgressRetainsTheWindowRunAcrossAResume() {
        Instant start = Instant.parse("2026-08-02T00:00:00Z");
        Instant end = start.plusSeconds(3600);
        UUID runId = UUID.randomUUID();
        repository.disconnect();

        repository.startWindow("sleep", "RAW", start, end, runId, true);
        repository.markPageProgress("sleep", "RAW", runId, "next-page", 23L, 1, false);

        assertThat(repository.findWindowProgress("sleep", "RAW"))
                .contains(new MirrorRepository.WindowProgress(
                        start, end, "next-page", runId, 23L, 1, false, true));
        repository.markPageProgress("sleep", "RAW", runId, null, 31L, 2, true);
        assertThat(repository.findWindowProgress("sleep", "RAW"))
                .contains(new MirrorRepository.WindowProgress(
                        start, end, null, runId, 31L, 2, true, true));
    }

    @Test
    @Transactional
    void disjointWindowCompletionDoesNotAdvanceTheForwardCursor() {
        Instant contiguousStart = Instant.parse("2026-01-01T00:00:00Z");
        Instant contiguousEnd = Instant.parse("2026-02-01T00:00:00Z");
        Instant hotStart = Instant.parse("2026-08-01T00:00:00Z");
        Instant hotEnd = Instant.parse("2026-09-01T00:00:00Z");
        repository.disconnect();

        repository.startWindow(
                "cursor-test", "RAW", contiguousStart, contiguousEnd, UUID.randomUUID(), false);
        repository.markWindowComplete(
                "cursor-test", "RAW", contiguousStart, contiguousEnd, 0L, 0L, false);
        repository.startWindow("cursor-test", "RAW", hotStart, hotEnd, UUID.randomUUID(), false);
        repository.markWindowCompleteWithoutAdvancingCursor(
                "cursor-test", "RAW", hotStart, hotEnd, 0L, 0L, false);

        assertThat(repository.findSyncState("cursor-test", "RAW"))
                .hasValueSatisfying(state -> {
                    assertThat(state.cursorTime()).isEqualTo(contiguousEnd);
                    assertThat(state.oldestSyncedTime()).isEqualTo(contiguousStart);
                });
    }

    @Test
    @Transactional
    void authoritativeWindowReplacementUpsertsAndDeletesDataPointsAtomically() {
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = start.plusSeconds(3600);
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        repository.disconnect();
        long sourceId = repository.upsertDataSource(
                hex('a'), "FITBIT", "AUTOMATIC", "Fitbit", "Pixel Watch", "{\"platform\":\"FITBIT\"}");
        StoredPoint retained = intervalPoint(
                hex('b'), hex('c'), sourceId, start, start.plusSeconds(60), "{\"steps\":{\"count\":0}}");
        StoredPoint removed = intervalPoint(
                hex('d'), hex('e'), sourceId, start.plusSeconds(60), start.plusSeconds(120),
                "{\"steps\":{\"count\":12}}");

        assertThat(repository.upsertDataPoints(List.of(retained, removed), firstRun)).isEqualTo(2);
        StoredPoint updated = intervalPoint(
                retained.identityKey(), hex('f'), sourceId, start, start.plusSeconds(60),
                "{\"steps\":{\"count\":0,\"lateUpdate\":true}}");
        assertThat(repository.upsertDataPoints(List.of(updated), secondRun)).isEqualTo(1);
        assertThat(repository.deleteMissingDataPoints(
                "steps", StoredPoint.Representation.RECONCILED_INTERVAL,
                DataTypePolicy.RecordType.INTERVAL,
                DataTypePolicy.WindowTimeDomain.PHYSICAL,
                start, end, secondRun)).isEqualTo(1);

        List<RecordRow> records = repository.findRecords(
                "steps", "RECONCILED_INTERVAL", start, end, sourceId,
                null, null, null, 10);
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.rawJson()).isNull();
            assertThat(repository.findRecord(record.kind(), record.id()))
                    .hasValueSatisfying(detail -> assertThat(detail.rawJson())
                            .contains("lateUpdate")
                            .contains("\"count\": 0"));
        });
    }

    @Test
    @Transactional
    void authoritativeSleepDeletionUsesTheSameEndTimeBoundaryAsTheGoogleQuery() {
        Instant windowStart = Instant.parse("2026-08-03T00:00:00Z");
        Instant windowEnd = windowStart.plusSeconds(3600);
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        repository.disconnect();
        StoredPoint crossesWindowStart = new StoredPoint(
                "sleep", StoredPoint.Representation.RAW,
                hex('4'), hex('5'), "users/me/dataTypes/sleep/dataPoints/test", null,
                windowStart.minusSeconds(3600), windowStart.plusSeconds(60),
                windowStart.minusSeconds(3600).toString(), windowStart.plusSeconds(60).toString(),
                null, null, null, null, null, null, "{\"sleep\":{}}");

        repository.upsertDataPoints(List.of(crossesWindowStart), firstRun);

        assertThat(repository.deleteMissingDataPoints(
                "sleep", StoredPoint.Representation.RAW,
                DataTypePolicy.RecordType.SESSION_END,
                DataTypePolicy.WindowTimeDomain.PHYSICAL,
                windowStart, windowEnd, secondRun)).isEqualTo(1);
        assertThat(repository.findRecords(
                "sleep", "RAW", null, null, null, null, null, null, 10)).isEmpty();
    }

    @Test
    @Transactional
    void dailyRollupReplacementKeepsTrueZeroUpdatesLateValuesAndDeletesMissingDays() {
        Instant windowStart = Instant.parse("2026-08-05T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-08-07T00:00:00Z");
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        repository.disconnect();
        StoredPoint retained = dailyPoint(
                hex('8'), hex('9'), windowStart, "{\"steps\":{\"countSum\":\"0\"}}");
        StoredPoint removed = dailyPoint(
                hex('a'), hex('b'), windowStart.plusSeconds(86_400),
                "{\"steps\":{\"countSum\":\"23\"}}");

        assertThat(repository.upsertDataPoints(List.of(retained, removed), firstRun)).isEqualTo(2);
        StoredPoint lateUpdate = dailyPoint(
                retained.identityKey(), hex('c'), windowStart,
                "{\"steps\":{\"countSum\":\"12\",\"lateUpdate\":true}}");
        assertThat(repository.upsertDataPoints(List.of(lateUpdate), secondRun)).isEqualTo(1);
        assertThat(repository.deleteMissingDataPoints(
                "steps", StoredPoint.Representation.DAILY_ROLLUP,
                DataTypePolicy.RecordType.DAILY,
                DataTypePolicy.WindowTimeDomain.DATE,
                windowStart, windowEnd, secondRun)).isEqualTo(1);

        assertThat(repository.findRecords(
                "steps", "DAILY_ROLLUP", windowStart, windowEnd,
                null, null, null, null, 10))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.rawJson()).isNull();
                    assertThat(repository.findRecord(record.kind(), record.id()))
                            .hasValueSatisfying(detail -> assertThat(detail.rawJson())
                                    .contains("\"countSum\": \"12\"")
                                    .contains("lateUpdate"));
                });
    }

    @Test
    @Transactional
    void exerciseAssociationWaitsForASuccessfulIndexerRunAfterPublication() {
        Instant start = Instant.parse("2026-08-04T10:00:00Z");
        repository.disconnect();
        String uniqueName = "association-" + UUID.randomUUID() + ".tcx";
        long indexedFileId = jdbc.queryForObject("""
                INSERT INTO public.indexed_file
                    ("index", name, path, indexer_status, index_update_date)
                VALUES ('GPS', ?, 'google-health', 'COMPLETED_WITH_SUCCESS', now() - interval '1 minute')
                RETURNING id
                """, Long.class, uniqueName);
        long trackId = jdbc.queryForObject("""
                INSERT INTO public.gps_track (file_id, load_status)
                VALUES (?, 'SUCCESS')
                RETURNING id
                """, Long.class, indexedFileId);
        StoredPoint exercise = new StoredPoint(
                "exercise", StoredPoint.Representation.RAW,
                hex('6'), hex('7'), "users/me/dataTypes/exercise/dataPoints/test", null,
                start, start.plusSeconds(1800), start.toString(), start.plusSeconds(1800).toString(),
                LocalDateTime.ofInstant(start, ZoneOffset.UTC), LocalDateTime.ofInstant(start.plusSeconds(1800), ZoneOffset.UTC),
                null, null, null, null, "{\"exercise\":{}}");
        repository.upsertDataPoints(List.of(exercise), UUID.randomUUID());
        long pointId = jdbc.queryForObject("""
                SELECT id FROM google_health.route_ownership WHERE identity_key = ?
                """, Long.class, exercise.identityKey());
        repository.markExerciseRouteState(pointId, ExercisePoint.RouteState.PENDING, null);
        repository.prepareExerciseRoutes(start, start.plusSeconds(1800), false);
        assertThat(jdbc.queryForObject("SELECT route_state FROM google_health.route_ownership WHERE id = ?", String.class, pointId))
                .isEqualTo("NO_LOCATION_SCOPE");
        repository.markExerciseRoutePublicationStarted(pointId);
        repository.markExerciseRoutePublished(
                pointId, "google-health/" + uniqueName, exercise.fingerprint(), start, 100);
        assertThat(repository.findAllManagedRoutePaths()).containsExactly("google-health/" + uniqueName);
        jdbc.update("""
                UPDATE google_health.route_ownership
                   SET indexed_file_id = ?, gps_track_id = ?
                 WHERE id = ?
                """, indexedFileId, trackId, pointId);

        assertThat(repository.findPublishedExercisesAwaitingAssociation()).isEmpty();
        assertThat(trackScopeService.canonicalExerciseTrackIds()).contains(trackId);

        jdbc.update("UPDATE google_health.route_ownership SET route_state = 'FAILED' WHERE id = ?", pointId);
        jdbc.update("UPDATE public.indexed_file SET last_modified_date = ?, size = 100 WHERE id = ?",
                Timestamp.from(start), indexedFileId);
        assertThat(repository.findPublishedExercisesAwaitingAssociation()).isEmpty();
        assertThat(trackScopeService.canonicalExerciseTrackIds()).contains(trackId);

        jdbc.update("UPDATE google_health.route_ownership SET route_state = 'PENDING' WHERE id = ?", pointId);
        assertThat(repository.findPublishedExercisesAwaitingAssociation())
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.id()).isEqualTo(pointId);
                    assertThat(candidate.indexedFileId()).isEqualTo(indexedFileId);
                    assertThat(candidate.gpsTrackId()).isEqualTo(trackId);
                });

        // An unrelated metadata observation must not invalidate this completed file version.
        repository.upsertDataPoints(List.of(exercise), UUID.randomUUID());
        assertThat(repository.findPublishedExercisesAwaitingAssociation()).hasSize(1);
        assertThat(repository.associateExerciseRoute(pointId, indexedFileId, trackId, hex('8'))).isZero();
        assertThat(repository.findPublishedExercisesAwaitingAssociation()).hasSize(1);
        assertThat(repository.associateExerciseRoute(pointId, indexedFileId, trackId, exercise.fingerprint())).isEqualTo(1);
        assertThat(repository.findPublishedExercisesAwaitingAssociation()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT route_state FROM google_health.route_ownership WHERE id = ?", String.class, pointId))
                .isEqualTo("IMPORTED");

        // A replacement cannot associate to the preceding completed file version.
        repository.markExerciseRoutePublicationStarted(pointId);
        repository.markExerciseRoutePublished(pointId, "google-health/" + uniqueName, exercise.fingerprint(),
                start.plusMillis(1), 100);
        assertThat(repository.findPublishedExercisesAwaitingAssociation()).isEmpty();
        repository.associateExerciseRoute(pointId, indexedFileId, trackId, exercise.fingerprint());
        assertThat(jdbc.queryForObject("SELECT route_state FROM google_health.route_ownership WHERE id = ?", String.class, pointId))
                .isEqualTo("PENDING");
        jdbc.update("UPDATE public.indexed_file SET last_modified_date = ? WHERE id = ?",
                Timestamp.from(start.plusMillis(1)), indexedFileId);
        assertThat(repository.findPublishedExercisesAwaitingAssociation()).hasSize(1);

        repository.markExerciseRouteState(pointId, ExercisePoint.RouteState.FAILED, "Safe route error");
        assertThat(repository.routeSummary().failedCount()).isEqualTo(1);
        assertThat(repository.routeSummary().lastError()).isEqualTo("Safe route error");
        assertThat(repository.routeSummary().nextRetryAt()).isAfter(Instant.now());
        assertThat(repository.findDueExerciseRouteRetries(25)).isEmpty();
        jdbc.update("UPDATE google_health.route_ownership SET route_next_retry_at = now() - interval '1 minute' WHERE id = ?", pointId);
        assertThat(repository.findDueExerciseRouteRetries(25)).singleElement().satisfies(point -> assertThat(point.id()).isEqualTo(pointId));
    }

    @Test
    @Transactional
    void googleExerciseTypeOverridesAutomaticClassificationButNeverAUserChoice() {
        long automaticTrackId = insertTrackWithActivitySource("AUTO_GUESS");
        long userTrackId = insertTrackWithActivitySource("USER_SET");

        assertThat(gpsTrackRepository.updateActivityClassificationUnlessUserSet(
                automaticTrackId,
                GpsTrack.ACTIVITY_TYPE.RUNNING,
                GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH,
                "Google Health exercise type: RUNNING")).isEqualTo(1);
        assertThat(gpsTrackRepository.updateActivityClassificationUnlessUserSet(
                userTrackId,
                GpsTrack.ACTIVITY_TYPE.RUNNING,
                GpsTrack.ACTIVITY_TYPE_SOURCE.GOOGLE_HEALTH,
                "Google Health exercise type: RUNNING")).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT activity_type_source FROM public.gps_track WHERE id = ?",
                String.class, automaticTrackId)).isEqualTo("GOOGLE_HEALTH");
        assertThat(jdbc.queryForObject(
                "SELECT activity_type_source FROM public.gps_track WHERE id = ?",
                String.class, userTrackId)).isEqualTo("USER_SET");
    }

    @Test
    @Transactional
    void heartRateReconciliationKeepsExactOriginalTimeAndDeletesMissingSamples() {
        Instant start = Instant.parse("2026-08-02T00:00:00Z");
        Instant exact = Instant.parse("2026-08-02T00:00:00.123456789Z");
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        repository.disconnect();
        long sourceId = repository.upsertDataSource(
                hex('1'), "FITBIT", "AUTOMATIC", "Fitbit", "Pixel Watch", "{\"platform\":\"FITBIT\"}");
        HeartRateSample retained = new HeartRateSample(
                sourceId, exact, "2026-08-02T00:00:00.123456789Z", "+02:00",
                LocalDateTime.parse("2026-08-02T02:00:00.123456789"), 61,
                "RESTING", "WRIST", hex('2'));
        HeartRateSample removed = new HeartRateSample(
                sourceId, start.plusSeconds(60), "2026-08-02T00:01:00Z", "+02:00",
                LocalDateTime.parse("2026-08-02T02:01:00"), 65,
                null, null, hex('3'));

        assertThat(repository.upsertHeartRateSamples(List.of(retained, removed), firstRun)).isEqualTo(2);
        assertThat(repository.upsertHeartRateSamples(List.of(retained), secondRun)).isEqualTo(1);
        assertThat(repository.deleteMissingHeartRateSamples(start, start.plusSeconds(3600), secondRun)).isEqualTo(1);

        List<RecordRow> records = repository.findRecords(
                "heart-rate", "HEART_RATE_SAMPLE", start, start.plusSeconds(3600), sourceId,
                null, null, null, 10);
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.originalTime()).isEqualTo("2026-08-02T00:00:00.123456789Z");
            assertThat(record.valueSummary()).isEqualTo("61 bpm");
            assertThat(record.rawJson()).isNull();
            assertThat(repository.findRecord(record.kind(), record.id())).hasValueSatisfying(detail ->
                    assertThat(detail.rawJson())
                            .contains("2026-08-02T00:00:00.123456789Z")
                            .contains("RESTING")
                            .contains("WRIST"));
        });
    }

    @Test
    @Transactional
    void chartQueriesReturnDailyMetricsAndBucketedHeartRate() {
        Instant start = Instant.parse("2042-08-02T00:00:00Z");
        Instant end = start.plusSeconds(3_600);
        UUID runId = UUID.randomUUID();
        repository.disconnect();
        repository.upsertDataPoints(List.of(dailyPoint(
                hex('a'), hex('b'), start, "{\"steps\":{\"countSum\":\"42\"}}")), runId);
        repository.upsertDataPoints(List.of(dailyPoint(
                "heart-rate", hex('f'), hex('0'), start,
                "{\"heartRate\":{\"beatsPerMinuteAvg\":\"70\","
                + "\"beatsPerMinuteMin\":\"60\",\"beatsPerMinuteMax\":\"80\"}}")), runId);
        long sourceId = repository.upsertDataSource(
                hex('c'), "FITBIT", "AUTOMATIC", "Fitbit", "Pixel Watch", "{\"platform\":\"FITBIT\"}");
        repository.upsertHeartRateSamples(List.of(
                new HeartRateSample(
                        sourceId, start.plusSeconds(60), start.plusSeconds(60).toString(), null,
                        null, 60, null, null, hex('d')),
                new HeartRateSample(
                        sourceId, start.plusSeconds(120), start.plusSeconds(120).toString(), null,
                        null, 80, null, null, hex('e'))), runId);

        assertThat(repository.findMetricRecords(start, end))
                .filteredOn(row -> row.dataType().equals("steps"))
                .singleElement()
                .satisfies(row -> assertThat(row.rawJson()).contains("countSum"));
        assertThat(repository.findMetricRecords(start, end))
                .filteredOn(row -> row.dataType().equals("heart-rate"))
                .singleElement()
                .satisfies(row -> assertThat(row.rawJson()).contains("beatsPerMinuteAvg"));
        assertThat(repository.findHeartRateBuckets(start, end, 900))
                .singleElement()
                .satisfies(bucket -> {
                    assertThat(bucket.average()).isEqualTo(70d);
                    assertThat(bucket.minimum()).isEqualTo(60);
                    assertThat(bucket.maximum()).isEqualTo(80);
                });
    }

    @Test
    @Transactional
    void secondQueriesAggregateSamplesAndExcludeDailyValuesAndRangeEdges() {
        repository.disconnect();
        repository.deleteMirrorRows();
        Instant start = Instant.parse("2042-10-14T00:00:00Z");
        Instant end = start.plus(Duration.ofDays(1));
        Instant time = start.plusSeconds(12).plusMillis(123);
        UUID run = UUID.randomUUID();
        long source = repository.upsertDataSource(hex('c'), "TEST", "AUTOMATIC", "Synthetic", "Synthetic", "{}");
        repository.upsertHeartRateSamples(List.of(
                new HeartRateSample(source, start.minusSeconds(1), start.minusSeconds(1).toString(), null, null, 50, null, null, hex('1')),
                new HeartRateSample(source, time, time.toString(), null, null, 60, null, null, hex('2')),
                new HeartRateSample(source, time, time.toString(), null, null, 80, null, null, hex('3')),
                new HeartRateSample(source, end, end.toString(), null, null, 90, null, null, hex('4'))), run);
        repository.upsertDataPoints(List.of(
                dailyPoint(hex('5'), hex('6'), start, "{\"steps\":{\"countSum\":1234}}"),
                new StoredPoint("weight", StoredPoint.Representation.RAW, hex('7'), hex('8'), null, source,
                        time, null, time.toString(), null, null, null, null, null, null, "TEST",
                        "{\"weight\":{\"weightGrams\":71500}}"),
                new StoredPoint("weight", StoredPoint.Representation.RAW, hex('9'), hex('a'), null, source,
                        end, null, end.toString(), null, null, null, null, null, null, "TEST",
                        "{\"weight\":{\"weightGrams\":72000}}")), run);

        assertThat(repository.findHeartRateBuckets(start, end, 1)).containsExactly(
                new HeartRateBucket(start.plusSeconds(12), 70, 60, 80));
        assertThat(repository.findIntradayMetricRecords(start, end)).singleElement().satisfies(row -> {
            assertThat(row.dataType()).isEqualTo("weight");
            assertThat(row.recordTime()).isEqualTo(time);
        });
    }

    @Test
    @Transactional
    void groupedQueriesKeepCivilDatesAndWeightSamplesBeforeCalendarAggregation() {
        repository.disconnect();
        repository.deleteMirrorRows();
        Instant midnight = Instant.parse("2042-03-29T00:00:00Z");
        UUID runId = UUID.randomUUID();
        long sourceId = repository.upsertDataSource(hex('c'), "TEST", "AUTOMATIC", "Synthetic", "Synthetic", "{}");
        repository.upsertDataPoints(List.of(
                dailyPoint(hex('a'), hex('b'), midnight, "{\"steps\":{\"countSum\":0}}"),
                intervalPoint(hex('5'), hex('6'), sourceId, midnight, midnight.plusSeconds(60),
                        "{\"steps\":{\"count\":9000}}")), runId);
        repository.upsertHeartRateSamples(List.of(
                new HeartRateSample(sourceId, midnight.plusSeconds(60), midnight.plusSeconds(60).toString(), null, null, 60, null, null, hex('d')),
                new HeartRateSample(sourceId, midnight.plusSeconds(120), midnight.plusSeconds(120).toString(), null, null, 60, null, null, hex('e')),
                new HeartRateSample(sourceId, midnight.plusSeconds(3700), midnight.plusSeconds(3700).toString(), null, null, 120, null, null, hex('f'))), runId);
        var date = LocalDate.parse("2042-03-29");
        for (String zone : List.of("America/Los_Angeles", "Pacific/Kiritimati", "Europe/Zurich")) {
            assertThat(repository.findGroupedMetricRecords(date, date.plusDays(1), zone))
                    .singleElement().satisfies(row -> {
                        assertThat(row.date()).isEqualTo(date);
                        assertThat(row.record().representation()).isEqualTo("DAILY_ROLLUP");
                        assertThat(row.record().recordTime()).isNull();
                    });
        }
        assertThat(repository.findHeartRateDays(midnight, midnight.plusSeconds(7200), "America/Los_Angeles"))
                .singleElement().satisfies(day -> {
                    assertThat(day.date()).isEqualTo(date.minusDays(1));
                    assertThat(day.average()).isEqualTo(80); // unequal sample buckets would incorrectly give 90
                    assertThat(day.minimum()).isEqualTo(60);
                    assertThat(day.maximum()).isEqualTo(120);
                });
        assertThat(repository.findHeartRateDays(midnight, midnight.plusSeconds(7200), "Pacific/Kiritimati"))
                .singleElement().satisfies(day -> assertThat(day.date()).isEqualTo(date));
        assertThat(repository.findHeartRateDaysForRanges(List.of(
                new HeartRateTimeRange(midnight, midnight.plusSeconds(90)),
                new HeartRateTimeRange(midnight.plusSeconds(3600), midnight.plusSeconds(7200))), "Europe/Zurich"))
                .singleElement().satisfies(day -> assertThat(day.average()).isEqualTo(90));
    }

    @Test
    void liveSchemaHasDurableSynchronizationTablesSafeTrackLinksAndBrinTimeIndex() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='google_health'", String.class))
                .contains("history_audit", "history_work", "history_child", "recent_pass", "recent_failure", "route_ownership");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM pg_constraint constraint_row
                  JOIN pg_class table_row ON table_row.oid = constraint_row.conrelid
                  JOIN pg_namespace schema_row ON schema_row.oid = table_row.relnamespace
                 WHERE schema_row.nspname = 'google_health'
                   AND constraint_row.contype = 'f'
                   AND pg_get_constraintdef(constraint_row.oid) LIKE '%ON DELETE SET NULL%'
                """, Integer.class)).isGreaterThanOrEqualTo(4);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM pg_indexes
                 WHERE schemaname = 'google_health'
                   AND indexdef ILIKE '%USING brin%'
                   AND indexdef ILIKE '%sample_time%'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = 'google_health'
                   AND table_name = 'heart_rate_sample'
                   AND column_name = 'raw_json'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @Transactional
    void reconnectCannotMixASecondGoogleHealthAccountIntoAnExistingMirror() {
        repository.disconnect();
        repository.saveConnected("health-user-a", Set.of("scope-a"), "encrypted-a", null);
        repository.upsertDataPoints(List.of(dailyPoint(
                        hex('1'), hex('2'), Instant.parse("2026-08-01T00:00:00Z"), "{\"steps\":{}}")),
                UUID.randomUUID());

        Assertions.assertThatThrownBy(() -> repository.saveConnected(
                        "health-user-b", Set.of("scope-a"), "encrypted-b", null))
                .isInstanceOf(GoogleHealthAccountMismatchException.class)
                .hasMessageContaining("different from the existing Google Health mirror");

        repository.saveConnected("health-user-a", Set.of("scope-a"), "encrypted-c", null);
        assertThat(repository.findConnection()).hasValueSatisfying(connection ->
                assertThat(connection.googleUserId()).isEqualTo("health-user-a"));
    }

    @Test
    @Transactional
    void reconnectCannotReuseSyncMetadataFromASecondGoogleHealthAccount() {
        repository.disconnect();
        repository.saveConnected("health-user-a", Set.of("scope-a"), "encrypted-a", null);
        repository.startWindow(
                "steps", "RECONCILED_INTERVAL",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                UUID.randomUUID(), false);

        Assertions.assertThatThrownBy(() -> repository.saveConnected(
                        "health-user-b", Set.of("scope-a"), "encrypted-b", null))
                .isInstanceOf(GoogleHealthAccountMismatchException.class)
                .hasMessageContaining("different from the existing Google Health mirror");
    }

    @Test
    @Transactional
    void exerciseReplacementUsesCivilTimeAcrossOffsetsAndDst() {
        repository.disconnect();
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();
        LocalDateTime civilStart = LocalDateTime.parse("2026-08-01T00:30:00");
        Instant windowStart = Instant.parse("2026-08-01T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-08-01T01:00:00Z");
        List<Instant> physicalStarts = List.of(
                civilStart.toInstant(ZoneOffset.of("-08:00")),
                civilStart.toInstant(ZoneOffset.of("+05:30")),
                civilStart.toInstant(ZoneOffset.of("+14:00")),
                civilStart.atZone(ZoneId.of("Europe/Zurich")).toInstant());
        List<StoredPoint> exercises = IntStream.range(0, physicalStarts.size())
                .mapToObj(index -> new StoredPoint(
                        "exercise", StoredPoint.Representation.RAW,
                        hex((char) ('3' + index)), hex((char) ('7' + index)),
                        "users/me/dataTypes/exercise/dataPoints/offset-" + index, null,
                        physicalStarts.get(index), physicalStarts.get(index).plusSeconds(1800),
                        physicalStarts.get(index).toString(), physicalStarts.get(index).plusSeconds(1800).toString(),
                        civilStart, civilStart.plusMinutes(30), null, null, null, null,
                        "{\"exercise\":{}}"))
                .toList();
        repository.upsertDataPoints(exercises, firstRun);

        assertThat(repository.deleteMissingDataPoints(
                "exercise", StoredPoint.Representation.RAW,
                DataTypePolicy.RecordType.SESSION_START,
                DataTypePolicy.WindowTimeDomain.CIVIL,
                windowStart, windowEnd, secondRun)).isEqualTo(4);
    }

    private static StoredPoint intervalPoint(
            String identityKey,
            String fingerprint,
            long sourceId,
            Instant start,
            Instant end,
            String rawJson) {
        return new StoredPoint(
                "steps", StoredPoint.Representation.RECONCILED_INTERVAL,
                identityKey, fingerprint, null, sourceId,
                start, end, start.toString(), end.toString(),
                null, null, null, null, null, null, rawJson);
    }

    private static StoredPoint dailyPoint(
            String identityKey,
            String fingerprint,
            Instant day,
            String rawJson) {
        return dailyPoint("steps", identityKey, fingerprint, day, rawJson);
    }

    private static StoredPoint dailyPoint(
            String dataType,
            String identityKey,
            String fingerprint,
            Instant day,
            String rawJson) {
        LocalDateTime civilStart = LocalDateTime.ofInstant(day, ZoneOffset.UTC);
        return new StoredPoint(
                dataType, StoredPoint.Representation.DAILY_ROLLUP,
                identityKey, fingerprint, null, null,
                null, null, null, null,
                civilStart, civilStart.plusDays(1), null, null,
                civilStart.toLocalDate(), ApiClient.ALL_SOURCES, rawJson);
    }

    private long insertTrackWithActivitySource(String source) {
        return jdbc.queryForObject("""
                INSERT INTO public.gps_track
                    (load_status, duplicate_status, activity_type, activity_type_source)
                VALUES ('SUCCESS', 'UNIQUE', 'WALKING', ?)
                RETURNING id
                """, Long.class, source);
    }

    private static String hex(char character) {
        return String.valueOf(character).repeat(64);
    }
}
