package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.SegmentCardiacCostRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.TrackHealthRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthController;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHGrouping;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthGroupingService;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.HealthQueryService;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.testsupport.NoScheduledJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"mtl.demo-mode=true", "mtl.demo-health.enabled=true", "mtl.demo-health.run-schedule=PT1H",
        "mtl.planner.enabled=false", "mtl.garmin-sync.enabled=false", "mtl.google-health.enabled=false"})
@Import(NoScheduledJobs.class)
@Transactional
class DemoHealthDatabaseIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2099, 10, 1);
    private static final Instant START = Instant.parse("2099-10-01T09:00:00Z");
    @Autowired
    DemoHealthRepository repository;
    @Autowired
    DemoHealthWorker worker;
    @Autowired
    DemoHealthProperties properties;
    @Autowired
    DemoHealthAccess access;
    @Autowired
    MtlAppProperties app;
    @Autowired
    MirrorRepository mirror;
    @Autowired
    TrackHealthRepository trackHealth;
    @Autowired
    SegmentCardiacCostRepository segmentHealth;
    @Autowired
    HealthController controller;
    @Autowired
    HealthGroupingService grouping;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    HealthQueryService queries;
    @Autowired
    ObjectMapper json;
    @MockitoBean
    GPXDirectoryWatcherService gps;
    @MockitoBean
    MediaIndexerService media;

    @Test
    void generatedRecordsAreReadableByTheSharedHealthAggregation() {
        assertThat(worker.day(DATE, DATE.plusDays(1))).isTrue();
        var result = grouping.grouped(DATE.minusDays(1).toString(), DATE.plusDays(1).toString(),
                GHGrouping.DAY, "UTC");
        assertThat(result.series()).extracting(series -> series.dataType()).contains(
                "steps", "distance", "active-energy-burned", "daily-resting-heart-rate", "weight", "sleep", "heart-rate");
    }

    @Test
    void generatedMetricsReachChartsAndTypedRecordResponses() {
        assertThat(worker.day(DATE, DATE.plusDays(1))).isTrue();
        var expected = Set.of("steps", "distance", "active-energy-burned", "sleep",
                "heart-rate", "daily-resting-heart-rate", "weight");
        var start = DATE.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var end = DATE.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        assertThat(controller.metrics(start, end)).extracting(series -> series.dataType())
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(grouping.grouped(DATE.minusDays(1).toString(), DATE.plusDays(1).toString(), GHGrouping.DAY, "UTC").series())
                .extracting(series -> series.dataType()).containsExactlyInAnyOrderElementsOf(expected);
        for (String type : expected) {
            String representation = type.equals("heart-rate") ? "DAILY_ROLLUP" : null;
            var response = queries.records(type, representation, null, null, null, null, 50);
            assertThat(response.records()).hasSize(1);
            var listed = response.records().getFirst();
            var detail = controller.record(listed.id());
            assertThat(listed.measurement()).as(type).isNotNull().isEqualTo(detail.measurement());
            assertThat(listed.valueSummary()).as(type).isNotBlank().isEqualTo(detail.valueSummary());
            assertThat(listed.payload()).isNull();
            assertThat(detail.payload()).containsKey(DataTypePolicy.jsonFieldName(type));
            var value = json.valueToTree(detail).path("measurement");
            assertThat(value.path("value").isNumber()).as(type).isTrue();
            assertThat(value.path("unit").asText()).as(type).isNotBlank();
            assertThat(json.valueToTree(listed).has("payload")).isFalse();
        }
        var steps = queries.records("steps", null, null, null, null, null, 50).records().getFirst();
        assertThat(steps.measurement().value()).isEqualTo(Math.round(DemoPhysiology.day(properties.getSeed(), DATE).steps()));
        assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
    }

    @Test
    void overlappingTracksShareOneTimelineWithoutDuplicateSamplesOrDoubleCountedActivity() {
        fixture("WALKING", 5);
        fixture("RUNNING", 14);
        assertThat(worker.day(DATE, DATE.plusDays(1))).isTrue();
        var summary = trackHealth.findHeartRateSummary(START, START.plusSeconds(600)).orElseThrow();
        assertThat(summary.averageBpm()).isGreaterThan(110);
        assertThat(segmentHealth.heartRate(START, START.plusSeconds(600)).stream()
                .filter(sample -> !sample.timestamp().isBefore(START) && !sample.timestamp().isAfter(START.plusSeconds(600))))
                .hasSize((int) summary.sampleCount());
        assertThat(jdbc.queryForObject("SELECT max(n) FROM (SELECT count(*) n FROM google_health.heart_rate_sample GROUP BY sample_time) counts", Long.class)).isEqualTo(1);
        assertThat(mirror.findHeartRateBuckets(START, START.plusSeconds(601), 15)).isNotEmpty();
        long dailySteps = jdbc.queryForObject("SELECT (raw_json->'steps'->>'countSum')::numeric::bigint FROM google_health.data_point WHERE data_type='steps'", Long.class);
        assertThat(dailySteps - DemoPhysiology.day(properties.getSeed(), DATE).steps()).isBetween(2400L, 2500L);
        assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
        assertThat(trackHealth.findHeartRateSummary(START, START.plusSeconds(600))).contains(summary);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.data_point WHERE data_type='exercise'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.connection", Long.class)).isEqualTo(1);
    }

    @Test
    void missingRecordsAndSamplesAreRestoredWithoutUpdatingExistingRows() {
        long track = fixture("RUNNING", 14);
        worker.day(DATE, DATE.plusDays(1));
        long records = mirror.findMirrorSummary().recordCount();
        long samples = jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class);
        int bpm = jdbc.queryForObject("SELECT beats_per_minute FROM google_health.heart_rate_sample WHERE sample_time=?", Integer.class, Timestamp.from(START));
        jdbc.update("""
                DELETE FROM google_health.data_point WHERE data_type='weight'
                   OR (data_type='exercise' AND identity_key IN (
                       SELECT identity_key FROM google_health.route_ownership WHERE gps_track_id=?))
                """, track);
        jdbc.update("DELETE FROM google_health.heart_rate_sample WHERE sample_time=?", Timestamp.from(START));
        var unchangedRecords = jdbc.queryForList("SELECT id,raw_json::text,updated_at,last_seen_run FROM google_health.data_point ORDER BY id");
        var unchangedSamples = jdbc.queryForList("SELECT id,beats_per_minute,fingerprint,last_seen_run FROM google_health.heart_rate_sample ORDER BY id");
        assertThat(repository.missingDays(DATE, DATE)).containsExactly(DATE);
        assertThat(worker.day(DATE, DATE.plusDays(1))).isTrue();
        assertThat(mirror.findMirrorSummary().recordCount()).isEqualTo(records);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.heart_rate_sample", Long.class)).isEqualTo(samples);
        assertThat(jdbc.queryForObject("SELECT beats_per_minute FROM google_health.heart_rate_sample WHERE sample_time=?", Integer.class, Timestamp.from(START))).isEqualTo(bpm);
        assertThat(jdbc.queryForList("SELECT id,raw_json::text,updated_at,last_seen_run FROM google_health.data_point ORDER BY id")).containsAll(unchangedRecords);
        assertThat(jdbc.queryForList("SELECT id,beats_per_minute,fingerprint,last_seen_run FROM google_health.heart_rate_sample ORDER BY id")).containsAll(unchangedSamples);
        assertThat(repository.missingDayCount(DATE, DATE)).isZero();
        assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
        jdbc.update("DELETE FROM google_health.heart_rate_sample WHERE sample_time=?", Timestamp.from(START));
        assertThat(repository.missingDayCount(DATE, DATE)).isEqualTo(1);
        assertThat(worker.day(DATE, DATE.plusDays(1))).isTrue();
        assertThat(repository.missingDayCount(DATE, DATE)).isZero();
    }

    @Test
    void dailyRecordsAreIdempotentAndAccountActionsStayBlocked() {
        worker.day(DATE, DATE.plusDays(1));
        long count = mirror.findMirrorSummary().recordCount();
        assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
        assertThat(mirror.findMirrorSummary().recordCount()).isEqualTo(count);
        assertThat(mirror.findGroupedMetricRecords(DATE, DATE.plusDays(1), "UTC")).isNotEmpty();
        assertThat(controller.status().connectionState().name()).isEqualTo("DISCONNECTED");
        assertThatThrownBy(() -> controller.startOAuth(false)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(controller::syncNow).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.deleteMirror(null)).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT refresh_token_ciphertext FROM google_health.connection WHERE id=1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT raw_json->>'synthetic' FROM google_health.data_source LIMIT 1", String.class)).isEqualTo("true");
    }

    @Test
    void zeroPercentGeneratesOnlyDailyBackgroundData() {
        fixture("RUNNING", 14);
        properties.setTrackPercentage(0);
        try {
            worker.day(DATE, DATE.plusDays(1));
            assertThat(repository.tracksForDay(DATE, 0)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.data_point WHERE data_type='exercise'", Long.class)).isZero();
            assertThat(trackHealth.findHeartRateSummary(START, START.plusSeconds(600)).orElseThrow().averageBpm()).isLessThan(100);
            assertThat(repository.missingDayCount(DATE, DATE)).isZero();
        } finally {
            properties.setTrackPercentage(100);
        }
    }

    @Test
    void unreliableTimingIsSkippedAndUnknownMotionDoesNotInventSamplesInsideGpsGaps() {
        long unreliable = fixture("RUNNING", 14), unknown = fixture("WALKING", 5);
        jdbc.update("UPDATE gps_track SET timing_unreliable=true WHERE id=?", unreliable);
        jdbc.update("UPDATE gps_track_data_points SET motion_state='UNKNOWN' WHERE gps_track_data_id IN (SELECT id FROM gps_track_data WHERE gps_track_id=?)", unknown);
        worker.day(DATE, DATE.plusDays(1));
        assertThat(jdbc.queryForList("SELECT gps_track_id FROM google_health.route_ownership WHERE present", Long.class)).containsExactly(unknown);
        assertThat(trackHealth.findHeartRateSummary(START, START.plusSeconds(600))).isEmpty();
        assertThat(repository.missingDayCount(DATE, DATE)).isZero();
    }

    @Test
    void sqlSelectionMatchesTheModelAndLoadsMetadataInPages() {
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 45; i++) ids.add(track("RUNNING"));
        properties.setTrackPercentage(67);
        try {
            var selected = new ArrayList<Long>();
            long afterId = 0;
            while (true) {
                var page = repository.tracksForDay(DATE, afterId);
                if (page.isEmpty()) break;
                assertThat(page).hasSizeLessThanOrEqualTo(properties.getBatchSize());
                page.forEach(value -> selected.add(value.id()));
                afterId = page.getLast().id();
            }
            assertThat(selected).containsExactlyElementsOf(ids.stream()
                    .filter(id -> DemoPhysiology.selected(properties.getSeed(), id.toString(), 67)).toList());
            assertThat(selected).isNotEmpty().hasSizeLessThan(ids.size());
        } finally {
            properties.setTrackPercentage(100);
        }
    }

    @Test
    void exerciseCoverageRequiresTheSharedSourceRecordAndRepairsMissingExercises() {
        long track = fixture("RUNNING", 14);
        worker.day(DATE, DATE.plusDays(1));
        long source = repository.sharedSource();
        long otherSource = mirror.upsertDataSource("b".repeat(64), "SYNTHETIC_DEMO", "SIMULATED", "Other synthetic source", null, "{}");
        jdbc.update("UPDATE google_health.data_point SET data_source_id=? WHERE data_type='exercise'", otherSource);
        assertThat(repository.missingDays(DATE.minusDays(1), DATE)).containsExactly(DATE, DATE.minusDays(1));
        jdbc.update("UPDATE google_health.data_point SET data_source_id=? WHERE data_type='exercise'", source);
        assertThat(repository.missingDayCount(DATE, DATE)).isZero();
        jdbc.update("DELETE FROM google_health.data_point WHERE data_type='exercise'");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM google_health.route_ownership WHERE gps_track_id=?", Long.class, track)).isEqualTo(1);
        assertThat(repository.missingDays(DATE, DATE)).containsExactly(DATE);
        assertThat(worker.day(DATE, DATE.plusDays(1))).isTrue();
        assertThat(repository.missingDayCount(DATE, DATE)).isZero();
        assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
    }

    @Test
    void completenessChecksThousandsOfExercisesAcrossYearsWithinABoundedDatabaseTurn() {
        long source = repository.sharedSource();
        int priorSamples = 30_000;
        var sampleStart = Timestamp.from(START.minusSeconds(30 * 86400L));
        jdbc.execute("SAVEPOINT prior_samples");
        jdbc.update("""
                INSERT INTO google_health.heart_rate_sample
                    (connection_id,data_source_id,sample_time,original_sample_time,beats_per_minute,fingerprint)
                SELECT 1,?,?::timestamptz + n * INTERVAL '15 seconds',
                       (?::timestamptz + n * INTERVAL '15 seconds')::text,80,repeat('a',64)
                FROM generate_series(1,?) n
                """, source, sampleStart, sampleStart, priorSamples);
        // Earlier fixture rollbacks leave index entries that repeated per-day counts must revisit.
        jdbc.execute("ROLLBACK TO SAVEPOINT prior_samples");
        jdbc.execute("RELEASE SAVEPOINT prior_samples");
        LocalDate first = DATE.minusDays(2007);
        jdbc.update("""
                INSERT INTO gps_track (load_status,duplicate_status,activity_type,start_date,end_date)
                SELECT 'SUCCESS','UNIQUE','RUNNING',?::date + (n % 2008) * INTERVAL '1 day' + INTERVAL '9 hours',
                       ?::date + (n % 2008) * INTERVAL '1 day' + INTERVAL '10 hours'
                FROM generate_series(1,12000) n
                """, first, first);
        jdbc.update("""
                INSERT INTO google_health.data_point
                    (connection_id,data_source_id,data_type,representation,identity_key,fingerprint,civil_date,raw_json)
                SELECT 1,?,'exercise','RAW',encode(sha256(convert_to('bulk:' || id,'UTF8')),'hex'),
                       repeat('a',64),start_date::date,'{}'::jsonb FROM gps_track
                """, source);
        jdbc.update("""
                INSERT INTO google_health.route_ownership
                    (account_id,connection_id,identity_key,calendar_policy,native_day,present,gps_track_id,route_state,fingerprint,raw_json)
                SELECT 'local-unconnected',1,encode(sha256(convert_to('bulk:' || id,'UTF8')),'hex'),
                       'TEST',start_date::date,true,id,'NOT_APPLICABLE',repeat('a',64),'{}'::jsonb FROM gps_track
                """);
        jdbc.update("""
                INSERT INTO google_health.data_point
                    (connection_id,data_source_id,data_type,representation,identity_key,fingerprint,civil_date,raw_json)
                SELECT 1,?,expected.type,expected.representation,
                       encode(sha256(convert_to(day::date || ':' || expected.type,'UTF8')),'hex'),repeat('a',64),day::date,
                       '{"heartRate":{"sampleCount":0}}'::jsonb
                FROM generate_series(?::date::timestamp,?::date::timestamp,INTERVAL '1 day') day
                CROSS JOIN (VALUES ('steps','DAILY_ROLLUP'), ('distance','DAILY_ROLLUP'),
                    ('active-energy-burned','DAILY_ROLLUP'), ('daily-resting-heart-rate','RAW'),
                    ('weight','RAW'), ('sleep','RAW'), ('heart-rate','DAILY_ROLLUP')) expected(type,representation)
                """, source, first, DATE);
        jdbc.execute("ANALYZE gps_track, google_health.data_point, google_health.route_ownership");
        jdbc.execute("SET LOCAL statement_timeout='2s'");
        assertThat(repository.missingDayCount(first, DATE)).isZero();
        assertThat(repository.missingDays(first, DATE)).isEmpty();
        jdbc.update("DELETE FROM google_health.data_point WHERE data_type='exercise' AND civil_date=?", DATE);
        assertThat(repository.missingDayCount(first, DATE)).isEqualTo(1);
        assertThat(repository.missingDays(first, DATE)).containsExactly(DATE);
    }

    @Test
    void betaToggleCannotWriteOutsideDemoModeAndRealHealthDataIsNeverOverwritten() {
        fixture("RUNNING", 14);
        app.setDemoMode(false);
        try {
            assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
            assertThat(access.enabled()).isFalse();
            assertThat(mirror.findMirrorSummary().recordCount()).isZero();
        } finally {
            app.setDemoMode(true);
        }
        properties.setEnabled(false);
        try {
            assertThat(worker.day(DATE, DATE.plusDays(1))).isFalse();
        } finally {
            properties.setEnabled(true);
        }
        jdbc.update("INSERT INTO google_health.connection (id) VALUES (1) ON CONFLICT (id) DO NOTHING");
        mirror.upsertDataSource("a".repeat(64), "FITBIT", "AUTOMATIC", "Actual provider", null, "{}");
        assertThatThrownBy(() -> worker.day(DATE, DATE.plusDays(1))).hasRootCauseInstanceOf(IllegalStateException.class).hasMessageContaining("dedicated demo");
        assertThat(mirror.findMirrorSummary().recordCount()).isZero();
    }

    private long track(String activity) {
        return jdbc.queryForObject("INSERT INTO gps_track (load_status,duplicate_status,activity_type,start_date,end_date) VALUES ('SUCCESS','UNIQUE',?,?,?) RETURNING id",
                Long.class, activity, Timestamp.from(START), Timestamp.from(START.plusSeconds(600)));
    }

    private long fixture(String activity, double speed) {
        long id = track(activity);
        long data = jdbc.queryForObject("INSERT INTO gps_track_data (gps_track_id,track_type) VALUES (?,'RAW_OUTLIER_CLEANED') RETURNING id", Long.class, id);
        for (int second = 0; second <= 600; second += 15)
            jdbc.update("""
                    INSERT INTO gps_track_data_points (gps_track_data_id,moving_window_in_sec,point_index,point_index_max,point_timestamp,
                        distance_in_meter_since_start,speed_in_kmh_moving_window,motion_state,point_long_lat)
                    VALUES (?,30,?,40,?,?,?,'MOVING',ST_SetSRID(ST_MakePoint(0,0),4326))
                    """, data, second / 15, Timestamp.from(START.plusSeconds(second)), second * speed / 3.6, speed);
        return id;
    }
}
