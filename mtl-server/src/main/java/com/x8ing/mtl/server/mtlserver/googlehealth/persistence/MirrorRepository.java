package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.*;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHFailedRange;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHStreamSyncStatus;
import com.x8ing.mtl.server.mtlserver.googlehealth.web.GHSynchronizationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class MirrorRepository {

    // Project only measurement operands for the bounded record page, never route/provider payloads.
    private static final String RECORD_VALUE_JSON = """
                CASE point.data_type
                WHEN 'steps' THEN jsonb_build_object('steps',
                    jsonb_strip_nulls(jsonb_build_object('countSum', point.raw_json#>'{steps,countSum}', 'count', point.raw_json#>'{steps,count}')))
                WHEN 'distance' THEN jsonb_build_object('distance',
                    jsonb_strip_nulls(jsonb_build_object('millimetersSum', point.raw_json#>'{distance,millimetersSum}', 'millimeters', point.raw_json#>'{distance,millimeters}')))
                WHEN 'active-energy-burned' THEN jsonb_build_object('activeEnergyBurned',
                    jsonb_strip_nulls(jsonb_build_object('kcalSum', point.raw_json#>'{activeEnergyBurned,kcalSum}', 'kcal', point.raw_json#>'{activeEnergyBurned,kcal}')))
                WHEN 'heart-rate' THEN jsonb_build_object('heartRate',
                    jsonb_strip_nulls(jsonb_build_object('beatsPerMinute', point.raw_json#>'{heartRate,beatsPerMinute}', 'beatsPerMinuteAvg', point.raw_json#>'{heartRate,beatsPerMinuteAvg}', 'beatsPerMinuteMin', point.raw_json#>'{heartRate,beatsPerMinuteMin}', 'beatsPerMinuteMax', point.raw_json#>'{heartRate,beatsPerMinuteMax}')))
                WHEN 'sleep' THEN jsonb_build_object('sleep',
                    jsonb_build_object('summary', jsonb_build_object('minutesAsleep', point.raw_json#>'{sleep,summary,minutesAsleep}')))
                WHEN 'daily-resting-heart-rate' THEN jsonb_build_object('dailyRestingHeartRate',
                    jsonb_strip_nulls(jsonb_build_object('beatsPerMinute', point.raw_json#>'{dailyRestingHeartRate,beatsPerMinute}')))
                WHEN 'daily-heart-rate-variability' THEN jsonb_build_object('dailyHeartRateVariability',
                    jsonb_strip_nulls(jsonb_build_object('averageHeartRateVariabilityMilliseconds', point.raw_json#>'{dailyHeartRateVariability,averageHeartRateVariabilityMilliseconds}', 'deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds', point.raw_json#>'{dailyHeartRateVariability,deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds}')))
                WHEN 'weight' THEN jsonb_build_object('weight',
                    jsonb_strip_nulls(jsonb_build_object('weightGrams', point.raw_json#>'{weight,weightGrams}')))
                WHEN 'body-fat' THEN jsonb_build_object('bodyFat',
                    jsonb_strip_nulls(jsonb_build_object('percentage', point.raw_json#>'{bodyFat,percentage}')))
                WHEN 'daily-oxygen-saturation' THEN jsonb_build_object('dailyOxygenSaturation',
                    jsonb_strip_nulls(jsonb_build_object('averagePercentage', point.raw_json#>'{dailyOxygenSaturation,averagePercentage}')))
                WHEN 'daily-respiratory-rate' THEN jsonb_build_object('dailyRespiratoryRate',
                    jsonb_strip_nulls(jsonb_build_object('breathsPerMinute', point.raw_json#>'{dailyRespiratoryRate,breathsPerMinute}')))
                WHEN 'daily-vo2-max' THEN jsonb_build_object('dailyVo2Max',
                    jsonb_strip_nulls(jsonb_build_object('vo2Max', point.raw_json#>'{dailyVo2Max,vo2Max}')))
                WHEN 'daily-sleep-temperature-derivations' THEN jsonb_build_object('dailySleepTemperatureDerivations',
                    jsonb_strip_nulls(jsonb_build_object('nightlyTemperatureCelsius', point.raw_json#>'{dailySleepTemperatureDerivations,nightlyTemperatureCelsius}')))
                WHEN 'core-body-temperature' THEN jsonb_build_object('coreBodyTemperature',
                    jsonb_strip_nulls(jsonb_build_object('temperatureCelsius', point.raw_json#>'{coreBodyTemperature,temperatureCelsius}')))
                WHEN 'blood-glucose' THEN jsonb_build_object('bloodGlucose',
                    jsonb_strip_nulls(jsonb_build_object('bloodGlucoseMilligramsPerDeciliter', point.raw_json#>'{bloodGlucose,bloodGlucoseMilligramsPerDeciliter}')))
                END::text
                """;

    private static final short CONNECTION_ID = 1;
    private static final int DATA_POINT_BATCH_SIZE = 500;
    private static final int MAX_EMPTY_BATCH_DAYS = 90;
    private ConnectorProperties workProperties = new ConnectorProperties();
    private WorkRetryPolicy workRetryPolicy = new WorkRetryPolicy(workProperties);

    @Autowired
    public void configureWorkLimits(ConnectorProperties properties) {
        workProperties = properties;
        workRetryPolicy = new WorkRetryPolicy(properties);
    }

    private static final int HEART_RATE_BATCH_SIZE = 1_000;
    private static final int DATA_POINT_KIND_ORDER = 0;
    private static final int HEART_RATE_KIND_ORDER = 1;
    private static final String POINT_IDENTITY_QUERY_SCOPE = " AND stored.data_type=? AND stored.representation=?";
    private static final String CALENDAR_RECORD_PREDICATE = "(representation = 'DAILY_ROLLUP' OR "
                                                            + "(representation = 'RAW' AND data_type IN ("
                                                            + DataTypePolicy.all().stream()
                                                                    .filter(policy -> policy.mode() == DataTypePolicy.Mode.RAW
                                                                                      && policy.recordType() == DataTypePolicy.RecordType.DAILY)
                                                                    .map(policy -> "'" + policy.dataType() + "'")
                                                                    .collect(Collectors.joining(", "))
                                                            + ")))";
    private static final String DATA_POINT_RECORD_TIME = """
            COALESCE(point.physical_start_time,
                     point.civil_start_time AT TIME ZONE 'UTC',
                     point.civil_date::timestamp AT TIME ZONE 'UTC')
            """.strip();

    private static final String DATA_POINT_UPSERT = """
            INSERT INTO google_health.data_point
                (connection_id, data_source_id, data_type, representation, identity_key,
                 fingerprint, google_resource_name, physical_start_time, physical_end_time,
                 original_start_time, original_end_time, civil_start_time, civil_end_time,
                 utc_offset_start, utc_offset_end, civil_date, data_source_family, raw_json,
                 last_seen_run, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, now())
            ON CONFLICT (connection_id, data_type, representation, identity_key) DO UPDATE SET
                data_source_id = EXCLUDED.data_source_id,
                fingerprint = EXCLUDED.fingerprint,
                google_resource_name = EXCLUDED.google_resource_name,
                physical_start_time = EXCLUDED.physical_start_time,
                physical_end_time = EXCLUDED.physical_end_time,
                original_start_time = EXCLUDED.original_start_time,
                original_end_time = EXCLUDED.original_end_time,
                civil_start_time = EXCLUDED.civil_start_time,
                civil_end_time = EXCLUDED.civil_end_time,
                utc_offset_start = EXCLUDED.utc_offset_start,
                utc_offset_end = EXCLUDED.utc_offset_end,
                civil_date = EXCLUDED.civil_date,
                data_source_family = EXCLUDED.data_source_family,
                raw_json = EXCLUDED.raw_json,
                last_seen_run = COALESCE(EXCLUDED.last_seen_run, google_health.data_point.last_seen_run),
                updated_at = CASE WHEN google_health.data_point.fingerprint <> EXCLUDED.fingerprint
                                  THEN now() ELSE google_health.data_point.updated_at END
            WHERE google_health.data_point.fingerprint IS DISTINCT FROM EXCLUDED.fingerprint
               OR EXCLUDED.last_seen_run IS NOT NULL
            """;

    private static final String HEART_RATE_UPSERT = """
            INSERT INTO google_health.heart_rate_sample
                (connection_id,data_source_id,sample_time,original_sample_time,utc_offset,civil_time,
                 beats_per_minute,motion_context,sensor_location,fingerprint,last_seen_run,
                 identity_key,google_resource_name,raw_json)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            ON CONFLICT (connection_id,identity_key) DO UPDATE SET
                data_source_id=EXCLUDED.data_source_id,sample_time=EXCLUDED.sample_time,
                original_sample_time=EXCLUDED.original_sample_time,utc_offset=EXCLUDED.utc_offset,
                civil_time=EXCLUDED.civil_time,beats_per_minute=EXCLUDED.beats_per_minute,
                motion_context=EXCLUDED.motion_context,sensor_location=EXCLUDED.sensor_location,
                fingerprint=EXCLUDED.fingerprint,google_resource_name=EXCLUDED.google_resource_name,raw_json=EXCLUDED.raw_json,
                last_seen_run=COALESCE(EXCLUDED.last_seen_run,google_health.heart_rate_sample.last_seen_run),
                updated_at=CASE WHEN google_health.heart_rate_sample.fingerprint <> EXCLUDED.fingerprint
                                THEN now() ELSE google_health.heart_rate_sample.updated_at END
            WHERE google_health.heart_rate_sample.fingerprint <> EXCLUDED.fingerprint OR EXCLUDED.last_seen_run IS NOT NULL
            """;

    private static final String NAMED_HEART_RATE =
            "COALESCE(google_resource_name ~ '^users/[^/]+/dataTypes/heart-rate/dataPoints/[^/]+$',false)";
    private static final String NAMED_HEART_RATE_UPSERT = HEART_RATE_UPSERT.replace(
            "ON CONFLICT (connection_id,identity_key)",
            "ON CONFLICT (connection_id,identity_key) WHERE " + NAMED_HEART_RATE);
    private static final String UNNAMED_HEART_RATE_UPSERT = HEART_RATE_UPSERT.replace(
            "ON CONFLICT (connection_id,identity_key)",
            "ON CONFLICT (connection_id,sample_time,identity_key) WHERE NOT " + NAMED_HEART_RATE);

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public MirrorRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.objectMapper = objectMapper;
    }

    public Optional<AccountConnection> findConnection() {
        List<AccountConnection> rows = jdbc.query("""
                SELECT google_user_id, granted_scopes::text, refresh_token_ciphertext, state,
                       last_error, connected_at, refresh_token_expires_at, disconnected_at, updated_at,
                       authorization_generation
                  FROM google_health.connection
                 WHERE id = ?
                """, (rs, rowNum) -> mapConnection(rs), CONNECTION_ID);
        return rows.stream().findFirst();
    }

    @Transactional
    public void saveConnected(
            String googleUserId,
            Set<String> scopes,
            String refreshTokenCiphertext,
            Instant refreshTokenExpiresAt) {
        ensureConnectionRow();
        String existingGoogleUserId = jdbc.queryForObject("""
                SELECT google_user_id
                  FROM google_health.connection
                 WHERE id = ?
                   FOR UPDATE
                """, String.class, CONNECTION_ID);
        if (existingGoogleUserId != null
            && !existingGoogleUserId.isBlank()
            && !existingGoogleUserId.equals(googleUserId)
            && hasMirrorState()) {
            throw new GoogleHealthAccountMismatchException();
        }
        jdbc.update("""
                        INSERT INTO google_health.connection
                            (id, google_user_id, granted_scopes, refresh_token_ciphertext, state,
                             last_error, connected_at, refresh_token_expires_at, disconnected_at, updated_at)
                        VALUES (?, ?, ?::jsonb, ?, ?, NULL, now(), ?, NULL, now())
                        ON CONFLICT (id) DO UPDATE SET
                            google_user_id = EXCLUDED.google_user_id,
                            granted_scopes = EXCLUDED.granted_scopes,
                            refresh_token_ciphertext = EXCLUDED.refresh_token_ciphertext,
                            state = EXCLUDED.state,
                            last_error = NULL,
                            connected_at = now(),
                            refresh_token_expires_at = EXCLUDED.refresh_token_expires_at,
                            disconnected_at = NULL,
                            authorization_generation = google_health.connection.authorization_generation + 1,
                            updated_at = now()
                        """, CONNECTION_ID, googleUserId, toJson(scopes), refreshTokenCiphertext,
                AccountConnection.ConnectionState.CONNECTED.name(), timestamp(refreshTokenExpiresAt));
    }

    public boolean updateRefreshToken(long expectedGeneration, String refreshTokenCiphertext,
                                      Instant refreshTokenExpiresAt) {
        return jdbc.update("""
                        UPDATE google_health.connection
                           SET refresh_token_ciphertext = ?,
                               refresh_token_expires_at = ?,
                               updated_at = now()
                         WHERE id = ? AND authorization_generation = ? AND state = 'CONNECTED'
                        """, refreshTokenCiphertext, timestamp(refreshTokenExpiresAt),
                CONNECTION_ID, expectedGeneration) == 1;
    }

    public boolean updateRefreshTokenExpiry(long expectedGeneration, Instant refreshTokenExpiresAt) {
        return jdbc.update("""
                UPDATE google_health.connection
                   SET refresh_token_expires_at = ?, updated_at = now()
                 WHERE id = ? AND authorization_generation = ? AND state = 'CONNECTED'
                """, timestamp(refreshTokenExpiresAt), CONNECTION_ID, expectedGeneration) == 1;
    }

    public boolean markReconnectRequired(long expectedGeneration, String safeError) {
        return jdbc.update("""
                        UPDATE google_health.connection
                           SET state = ?, last_error = ?, updated_at = now()
                         WHERE id = ? AND authorization_generation = ? AND state = 'CONNECTED'
                        """, AccountConnection.ConnectionState.RECONNECT_REQUIRED.name(), safeError,
                CONNECTION_ID, expectedGeneration) == 1;
    }

    public void disconnect() {
        ensureConnectionRow();
        jdbc.update("""
                UPDATE google_health.connection
                   SET refresh_token_ciphertext = NULL,
                       refresh_token_expires_at = NULL,
                       state = ?,
                       last_error = NULL,
                       disconnected_at = now(),
                       authorization_generation = authorization_generation + 1,
                       updated_at = now()
                 WHERE id = ?
                """, AccountConnection.ConnectionState.DISCONNECTED.name(), CONNECTION_ID);
    }

    @Transactional
    public void deleteMirrorRows() {
        for (String table : List.of("history_audit", "history_work", "recent_failure", "recent_pass", "route_ownership", "sync_control")) {
            jdbc.update("DELETE FROM google_health." + table);
        }
        int heartRateSamples = jdbc.update(
                "DELETE FROM google_health.heart_rate_sample WHERE connection_id = ?", CONNECTION_ID);
        int dataPoints = jdbc.update(
                "DELETE FROM google_health.data_point WHERE connection_id = ?", CONNECTION_ID);
        int syncStates = jdbc.update(
                "DELETE FROM google_health.sync_state WHERE connection_id = ?", CONNECTION_ID);
        int dataSources = jdbc.update(
                "DELETE FROM google_health.data_source WHERE connection_id = ?", CONNECTION_ID);
        log.info("Google Health local mirror rows deleted: dataPoints={}, heartRateSamples={}, "
                 + "syncStates={}, dataSources={}",
                dataPoints, heartRateSamples, syncStates, dataSources);
    }

    public Optional<GHSyncState> findSyncState(String dataType, String representation) {
        List<GHSyncState> rows = jdbc.query("""
                SELECT data_type, representation, cursor_time, oldest_synced_time,
                       active_window_start, active_window_end, active_page_token,
                       backfill_complete, last_attempt_at, last_success_at, last_error,
                       imported_count, deleted_count
                  FROM google_health.sync_state
                 WHERE connection_id = ? AND data_type = ? AND representation = ?
                """, (rs, rowNum) -> new GHSyncState(
                rs.getString("data_type"),
                rs.getString("representation"),
                instant(rs, "cursor_time"),
                instant(rs, "oldest_synced_time"),
                instant(rs, "active_window_start"),
                instant(rs, "active_window_end"),
                rs.getString("active_page_token"),
                rs.getBoolean("backfill_complete"),
                instant(rs, "last_attempt_at"),
                instant(rs, "last_success_at"),
                rs.getString("last_error"),
                rs.getLong("imported_count"),
                rs.getLong("deleted_count")
        ), CONNECTION_ID, dataType, representation);
        return rows.stream().findFirst();
    }

    public List<GHSyncState> findAllSyncStates() {
        return jdbc.query("""
                SELECT data_type, representation, cursor_time, oldest_synced_time,
                       active_window_start, active_window_end, active_page_token,
                       backfill_complete, last_attempt_at, last_success_at, last_error,
                       imported_count, deleted_count
                  FROM google_health.sync_state
                 WHERE connection_id = ?
                 ORDER BY data_type, representation
                """, (rs, rowNum) -> new GHSyncState(
                rs.getString("data_type"), rs.getString("representation"),
                instant(rs, "cursor_time"), instant(rs, "oldest_synced_time"),
                instant(rs, "active_window_start"), instant(rs, "active_window_end"),
                rs.getString("active_page_token"), rs.getBoolean("backfill_complete"),
                instant(rs, "last_attempt_at"), instant(rs, "last_success_at"),
                rs.getString("last_error"), rs.getLong("imported_count"), rs.getLong("deleted_count")
        ), CONNECTION_ID);
    }

    public MirrorSummaryRow findMirrorSummary() {
        MirrorSummaryRow summary = jdbc.queryForObject("""
                WITH storage AS (
                    SELECT COUNT(*) AS record_count,
                           (SELECT sample_time
                              FROM google_health.heart_rate_sample
                             WHERE connection_id = ?
                             ORDER BY sample_time ASC
                             LIMIT 1) AS earliest_record,
                           (SELECT sample_time
                              FROM google_health.heart_rate_sample
                             WHERE connection_id = ?
                             ORDER BY sample_time DESC
                             LIMIT 1) AS latest_record
                      FROM google_health.heart_rate_sample
                     WHERE connection_id = ?
                    UNION ALL
                    SELECT COUNT(*) AS record_count,
                           MIN(COALESCE(physical_start_time,
                                        civil_date::timestamp AT TIME ZONE 'UTC')) AS earliest_record,
                           MAX(COALESCE(physical_start_time,
                                        civil_date::timestamp AT TIME ZONE 'UTC')) AS latest_record
                      FROM google_health.data_point
                     WHERE connection_id = ?
                )
                SELECT COALESCE(SUM(record_count), 0) AS record_count,
                       MIN(earliest_record) AS earliest_record,
                       MAX(latest_record) AS latest_record
                  FROM storage
                """, (rs, rowNum) -> new MirrorSummaryRow(
                rs.getLong("record_count"),
                instant(rs, "earliest_record"),
                instant(rs, "latest_record")
        ), CONNECTION_ID, CONNECTION_ID, CONNECTION_ID, CONNECTION_ID);
        return summary == null ? MirrorSummaryRow.empty() : summary;
    }

    public Optional<WindowProgress> findWindowProgress(String dataType, String representation) {
        return jdbc.query("""
                        SELECT active_window_start, active_window_end, active_page_token,
                               active_window_run_id, active_imported_count,
                               active_page_count, active_pages_complete, active_backfill_complete
                          FROM google_health.sync_state
                         WHERE connection_id = ?
                           AND data_type = ?
                           AND representation = ?
                           AND active_window_start IS NOT NULL
                           AND active_window_end IS NOT NULL
                        """, (rs, rowNum) -> new WindowProgress(
                        instant(rs, "active_window_start"),
                        instant(rs, "active_window_end"),
                        rs.getString("active_page_token"),
                        rs.getObject("active_window_run_id", UUID.class),
                        rs.getLong("active_imported_count"),
                        rs.getInt("active_page_count"),
                        rs.getBoolean("active_pages_complete"),
                        rs.getBoolean("active_backfill_complete")),
                CONNECTION_ID, dataType, representation).stream().findFirst();
    }

    public void startWindow(String dataType,
                            String representation,
                            Instant start,
                            Instant end,
                            UUID runId,
                            boolean backfillComplete) {
        jdbc.update("""
                        INSERT INTO google_health.sync_state
                            (connection_id, data_type, representation, active_window_start,
                             active_window_end, active_page_token, active_window_run_id,
                             active_imported_count, active_page_count, active_pages_complete, active_backfill_complete,
                             last_attempt_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, NULL, ?, 0, 0, false, ?, now(), now())
                        ON CONFLICT (connection_id, data_type, representation) DO UPDATE SET
                            active_window_start = EXCLUDED.active_window_start,
                            active_window_end = EXCLUDED.active_window_end,
                            active_page_token = NULL,
                            active_window_run_id = EXCLUDED.active_window_run_id,
                            active_imported_count = 0,
                            active_page_count = 0,
                            active_pages_complete = false,
                            active_backfill_complete = EXCLUDED.active_backfill_complete,
                            last_attempt_at = now(),
                            last_error = NULL,
                            updated_at = now()
                        """, CONNECTION_ID, dataType, representation, timestamp(start), timestamp(end),
                runId, backfillComplete);
    }

    public void markPageProgress(String dataType,
                                 String representation,
                                 UUID runId,
                                 String pageToken,
                                 long imported,
                                 int pageCount,
                                 boolean pagesComplete) {
        jdbc.update("""
                        UPDATE google_health.sync_state
                           SET active_page_token = ?,
                               active_imported_count = ?,
                               active_page_count = ?,
                               active_pages_complete = ?,
                               updated_at = now()
                         WHERE connection_id = ?
                           AND data_type = ?
                           AND representation = ?
                           AND active_window_run_id = ?
                        """, pageToken, imported, pageCount, pagesComplete,
                CONNECTION_ID, dataType, representation, runId);
    }

    public void markWindowComplete(String dataType, String representation, Instant start, Instant end,
                                   long imported, long deleted, boolean backfillComplete) {
        markWindowComplete(
                dataType, representation, start, end, imported, deleted, backfillComplete, true);
    }

    public void markWindowCompleteWithoutAdvancingCursor(
            String dataType,
            String representation,
            Instant start,
            Instant end,
            long imported,
            long deleted,
            boolean backfillComplete) {
        markWindowComplete(
                dataType, representation, start, end, imported, deleted, backfillComplete, false);
    }

    private void markWindowComplete(
            String dataType,
            String representation,
            Instant start,
            Instant end,
            long imported,
            long deleted,
            boolean backfillComplete,
            boolean advanceCursor) {
        jdbc.update("""
                        UPDATE google_health.sync_state
                           SET cursor_time = CASE WHEN ?
                                    THEN GREATEST(COALESCE(cursor_time, ?), ?)
                                    ELSE cursor_time
                               END,
                               oldest_synced_time = CASE
                                   WHEN oldest_synced_time IS NULL OR
                                       (? >= oldest_synced_time AND ? <= cursor_time)
                                   THEN LEAST(COALESCE(oldest_synced_time, ?), ?)
                                   ELSE oldest_synced_time END,
                               active_window_start = NULL,
                               active_window_end = NULL,
                               active_page_token = NULL,
                               active_window_run_id = NULL,
                               active_imported_count = 0,
                               active_page_count = 0,
                               active_pages_complete = false,
                               active_backfill_complete = false,
                               backfill_complete = backfill_complete OR
                                   (? AND (oldest_synced_time IS NULL OR
                                       (? >= oldest_synced_time AND ? <= cursor_time))),
                               last_success_at = now(),
                               last_error = NULL,
                               imported_count = imported_count + ?,
                               deleted_count = deleted_count + ?,
                               updated_at = now()
                         WHERE connection_id = ? AND data_type = ? AND representation = ?
                        """, advanceCursor, timestamp(end), timestamp(end), timestamp(end), timestamp(start),
                timestamp(start), timestamp(start), backfillComplete, timestamp(end), timestamp(start),
                imported, deleted, CONNECTION_ID, dataType, representation);
        // Daily and full plans are independent. A completed window can advance either
        // matching edge, atomically with the authoritative deletion above.
        for (String prefix : List.of("reconciliation", "daily_reconciliation")) {
            jdbc.update("""
                            UPDATE google_health.sync_state
                               SET %1$s_end = CASE WHEN ? <= %1$s_start THEN NULL ELSE CAST(? AS timestamptz) END,
                                   %1$s_start = CASE WHEN ? <= %1$s_start THEN NULL ELSE %1$s_start END,
                                   updated_at = now()
                             WHERE connection_id = ? AND data_type = ? AND representation = ?
                               AND %1$s_end = ?
                            """.formatted(prefix), timestamp(start), timestamp(start), timestamp(start), CONNECTION_ID,
                    dataType, representation, timestamp(end));
        }
    }

    public void planReconciliation(String dataType, String representation, Instant start, Instant end,
                                   boolean completesBackfill) {
        String prefix = completesBackfill ? "reconciliation" : "daily_reconciliation";
        jdbc.update("""
                INSERT INTO google_health.sync_state
                    (connection_id, data_type, representation, %1$s_start, %1$s_end, updated_at)
                VALUES (?, ?, ?, ?, ?, now())
                ON CONFLICT (connection_id, data_type, representation) DO UPDATE SET
                    %1$s_start = LEAST(COALESCE(sync_state.%1$s_start, EXCLUDED.%1$s_start), EXCLUDED.%1$s_start),
                    %1$s_end = COALESCE(sync_state.%1$s_end, EXCLUDED.%1$s_end),
                    updated_at = now()
                """.formatted(prefix), CONNECTION_ID, dataType, representation, timestamp(start), timestamp(end));
    }

    public Optional<ReconciliationRange> findReconciliation(String dataType, String representation) {
        return findReconciliation(dataType, representation, false)
                .or(() -> findReconciliation(dataType, representation, true));
    }

    public Optional<ReconciliationRange> findReconciliation(String dataType, String representation,
                                                            boolean completesBackfill) {
        String prefix = completesBackfill ? "reconciliation" : "daily_reconciliation";
        return jdbc.query("""
                        SELECT %1$s_start AS range_start, %1$s_end AS range_end
                          FROM google_health.sync_state
                         WHERE connection_id = ? AND data_type = ? AND representation = ?
                           AND %1$s_start IS NOT NULL AND %1$s_end IS NOT NULL
                        """.formatted(prefix), (rs, rowNum) -> new ReconciliationRange(
                        instant(rs, "range_start"), instant(rs, "range_end"), completesBackfill),
                CONNECTION_ID, dataType, representation).stream().findFirst();
    }

    public record ReconciliationRange(Instant start, Instant end, boolean completesBackfill) {
    }

    public void markSyncError(String dataType, String representation, String safeError) {
        jdbc.update("""
                UPDATE google_health.sync_state
                   SET last_error = ?, updated_at = now()
                 WHERE connection_id = ? AND data_type = ? AND representation = ?
                """, safeError, CONNECTION_ID, dataType, representation);
    }

    public long upsertDataSource(String fingerprint, String platform, String recordingMethod,
                                 String applicationName, String deviceDisplayName, String rawJson) {
        Long id = jdbc.queryForObject("""
                        WITH inserted AS (INSERT INTO google_health.data_source
                            (connection_id, fingerprint, platform, recording_method,
                             application_name, device_display_name, raw_json, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now())
                        ON CONFLICT (connection_id, fingerprint) DO UPDATE SET
                            platform = EXCLUDED.platform,
                            recording_method = EXCLUDED.recording_method,
                            application_name = EXCLUDED.application_name,
                            device_display_name = EXCLUDED.device_display_name,
                            raw_json = EXCLUDED.raw_json,
                            updated_at = CASE WHEN google_health.data_source.raw_json IS DISTINCT FROM EXCLUDED.raw_json
                                              THEN now() ELSE google_health.data_source.updated_at END
                        WHERE google_health.data_source.raw_json IS DISTINCT FROM EXCLUDED.raw_json
                        RETURNING id)
                        SELECT id FROM inserted UNION ALL
                        SELECT id FROM google_health.data_source WHERE connection_id=? AND fingerprint=? LIMIT 1
                        """, Long.class, CONNECTION_ID, fingerprint, platform, recordingMethod,
                applicationName, deviceDisplayName, rawJson, CONNECTION_ID, fingerprint);
        if (id == null) {
            throw new IllegalStateException("Google Health data source upsert returned no id");
        }
        return id;
    }

    public int upsertDataPoints(List<StoredPoint> points, UUID syncRunId) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate(DATA_POINT_UPSERT, points, DATA_POINT_BATCH_SIZE, (ps, point) -> {
            bindDataPoint(ps, point, syncRunId, 1);
        });
        points.stream().filter(point -> point.dataType().equals(DataTypePolicy.EXERCISE_DATA_TYPE))
                .forEach(this::upsertRouteOwner);
        return sum(counts);
    }

    private static int bindDataPoint(PreparedStatement ps, StoredPoint point, UUID syncRunId, int index) throws SQLException {
        ps.setShort(index++, CONNECTION_ID);
        if (point.dataSourceId() == null) ps.setNull(index++, Types.BIGINT);
        else ps.setLong(index++, point.dataSourceId());
        ps.setString(index++, point.dataType());
        ps.setString(index++, point.representation().name());
        ps.setString(index++, point.identityKey());
        ps.setString(index++, point.fingerprint());
        ps.setString(index++, point.googleResourceName());
        ps.setTimestamp(index++, timestamp(point.physicalStartTime()));
        ps.setTimestamp(index++, timestamp(point.physicalEndTime()));
        ps.setString(index++, point.originalStartTime());
        ps.setString(index++, point.originalEndTime());
        ps.setObject(index++, floorCivilTime(point.civilStartTime()));
        ps.setObject(index++, floorCivilTime(point.civilEndTime()));
        ps.setString(index++, point.utcOffsetStart());
        ps.setString(index++, point.utcOffsetEnd());
        ps.setObject(index++, point.civilDate());
        ps.setString(index++, point.dataSourceFamily());
        ps.setString(index++, point.rawJson());
        ps.setObject(index++, syncRunId);
        return index;
    }

    private void upsertRouteOwner(StoredPoint point) {
        jdbc.update("""
                        INSERT INTO google_health.route_ownership
                            (account_id,identity_key,calendar_policy,native_day,present,google_resource_name,
                             physical_start_time,physical_end_time,fingerprint,raw_json,route_state)
                        SELECT COALESCE(google_user_id,'local-unconnected'),?, ?, ?,TRUE, ?, ?, ?, ?, ?::jsonb,
                             CASE WHEN CAST(? AS text) IS NULL THEN 'NO_ROUTE'
                                  WHEN jsonb_exists(granted_scopes,?) THEN 'PENDING' ELSE 'NO_LOCATION_SCOPE' END
                          FROM google_health.connection WHERE id=1
                        ON CONFLICT(account_id,identity_key) DO UPDATE SET
                            native_day=EXCLUDED.native_day,present=TRUE,google_resource_name=EXCLUDED.google_resource_name,
                            physical_start_time=EXCLUDED.physical_start_time,physical_end_time=EXCLUDED.physical_end_time,
                            fingerprint=EXCLUDED.fingerprint,raw_json=EXCLUDED.raw_json,
                            route_state=CASE WHEN google_health.route_ownership.fingerprint <> EXCLUDED.fingerprint
                                             THEN EXCLUDED.route_state ELSE google_health.route_ownership.route_state END,
                            route_attempts=CASE WHEN google_health.route_ownership.fingerprint <> EXCLUDED.fingerprint
                                             THEN 0 ELSE google_health.route_ownership.route_attempts END,
                            route_next_retry_at=CASE WHEN google_health.route_ownership.fingerprint <> EXCLUDED.fingerprint
                                             THEN NULL ELSE google_health.route_ownership.route_next_retry_at END,
                            updated_at=now()
                        WHERE NOT google_health.route_ownership.present
                           OR google_health.route_ownership.fingerprint <> EXCLUDED.fingerprint
                           OR google_health.route_ownership.native_day <> EXCLUDED.native_day
                        """, point.identityKey(), SyncWindowPolicy.CALENDAR_POLICY,
                point.civilDate() != null ? point.civilDate() : point.civilStartTime().toLocalDate(), point.googleResourceName(), timestamp(point.physicalStartTime()),
                timestamp(point.physicalEndTime()), point.fingerprint(), point.rawJson(), point.googleResourceName(), OAuthScopes.LOCATION);
    }

    public int upsertHeartRateSamples(List<HeartRateSample> samples, UUID syncRunId) {
        if (samples == null || samples.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (var group : heartRateIdentityGroups(samples).entrySet()) {
            if (group.getValue().isEmpty()) continue;
            int[][] counts = jdbc.batchUpdate(group.getKey() ? NAMED_HEART_RATE_UPSERT : UNNAMED_HEART_RATE_UPSERT,
                    group.getValue(), HEART_RATE_BATCH_SIZE, (ps, sample) -> bindHeartRate(ps, sample, syncRunId, 1));
            changed += sum(counts);
        }
        return changed;
    }

    private static int bindHeartRate(PreparedStatement ps, HeartRateSample sample, UUID syncRunId, int index) throws SQLException {
        ps.setShort(index++, CONNECTION_ID);
        if (sample.dataSourceId() == null) ps.setNull(index++, Types.BIGINT);
        else ps.setLong(index++, sample.dataSourceId());
        ps.setTimestamp(index++, timestamp(sample.sampleTime()));
        ps.setString(index++, sample.originalSampleTime());
        ps.setString(index++, sample.utcOffset());
        ps.setObject(index++, floorCivilTime(sample.civilTime()));
        ps.setInt(index++, sample.beatsPerMinute());
        ps.setString(index++, sample.motionContext());
        ps.setString(index++, sample.sensorLocation());
        ps.setString(index++, sample.fingerprint());
        ps.setObject(index++, syncRunId);
        ps.setString(index++, sample.identityKey());
        ps.setString(index++, sample.googleResourceName());
        ps.setString(index++, sample.rawJson());
        return index;
    }

    public int importHistoricalHeartRate(List<HeartRateSample> samples) {
        int changed = 0;
        for (var group : heartRateIdentityGroups(samples).entrySet()) {
            changed += upsertBatches(group.getValue(), HEART_RATE_BATCH_SIZE,
                    group.getKey() ? NAMED_HEART_RATE_UPSERT : UNNAMED_HEART_RATE_UPSERT, MirrorRepository::bindHeartRate);
        }
        return changed;
    }

    private static Map<Boolean, List<HeartRateSample>> heartRateIdentityGroups(List<HeartRateSample> samples) {
        return samples.stream().collect(Collectors.partitioningBy(sample ->
                PointMapper.hasCompleteResourceName(sample.googleResourceName(), DataTypePolicy.HEART_RATE_DATA_TYPE)));
    }

    public long countHeartRateContentChanges(List<HeartRateSample> samples) {
        return countHeartRateDifferences(samples, null);
    }

    public void checkHeartRateQueryConflicts(List<HeartRateSample> samples, UUID traversal) {
        if (traversal != null && countHeartRateDifferences(samples, traversal) > 0)
            throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_TRAVERSAL_IDENTITY);
    }

    private long countHeartRateDifferences(List<HeartRateSample> samples, UUID traversal) {
        long count = 0;
        for (var group : heartRateIdentityGroups(samples).entrySet()) {
            if (group.getValue().isEmpty()) continue;
            boolean named = group.getKey();
            List<HeartRateSample> rows = group.getValue();
            List<Object> parameters = new ArrayList<>();
            parameters.add(new SqlArrayValue("text", rows.stream().map(HeartRateSample::identityKey).toArray()));
            parameters.add(new SqlArrayValue("text", rows.stream().map(HeartRateSample::fingerprint).toArray()));
            if (!named) parameters.add(new SqlArrayValue("timestamptz", rows.stream()
                    .map(row -> row.sampleTime().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)).toArray()));
            if (traversal != null) parameters.add(traversal);
            String incoming = named ? "unnest(?::char(64)[],?::char(64)[]) incoming(key,fp)"
                    : "unnest(?::char(64)[],?::char(64)[],?::timestamptz[]) incoming(key,fp,time)";
            count += jdbc.queryForObject("SELECT count(*) FROM " + incoming
                    + (traversal == null ? " LEFT JOIN " : " JOIN ") + "google_health.heart_rate_sample stored"
                    + " ON stored.connection_id=1 AND stored.identity_key=incoming.key"
                    + (named ? " AND " : " AND stored.sample_time=incoming.time AND NOT ")
                    + NAMED_HEART_RATE
                    + " WHERE stored.fingerprint IS DISTINCT FROM incoming.fp"
                    + (traversal == null ? "" : " AND stored.last_seen_run=?"), Long.class, parameters.toArray());
        }
        return count;
    }

    public int importHistoricalPoints(List<StoredPoint> points) {
        int changed = upsertBatches(points, DATA_POINT_BATCH_SIZE, DATA_POINT_UPSERT, MirrorRepository::bindDataPoint);
        points.stream().filter(point -> point.dataType().equals(DataTypePolicy.EXERCISE_DATA_TYPE))
                .forEach(this::upsertRouteOwner);
        return changed;
    }

    /** One multi-row SQL statement per bounded batch; update counts come from the write itself. */
    private <T> int upsertBatches(List<T> rows, int batchSize, String sql, RowBinder<T> binder) {
        int valuesStart = sql.indexOf("VALUES ") + "VALUES ".length();
        int conflictStart = sql.indexOf("ON CONFLICT");
        String tuple = sql.substring(valuesStart, conflictStart).strip();
        Map<Integer, String> statements = new HashMap<>();
        int changed = 0;
        for (int start = 0; start < rows.size(); start += batchSize) {
            List<T> batch = rows.subList(start, Math.min(rows.size(), start + batchSize));
            String statement = statements.computeIfAbsent(batch.size(), size -> sql.substring(0, valuesStart)
                    + String.join(",", Collections.nCopies(size, tuple)) + "\n" + sql.substring(conflictStart));
            changed += jdbc.update(connection -> {
                PreparedStatement prepared = connection.prepareStatement(statement);
                int index = 1;
                for (T row : batch) index = binder.bind(prepared, row, null, index);
                return prepared;
            });
        }
        return changed;
    }

    @FunctionalInterface
    private interface RowBinder<T> {
        int bind(PreparedStatement statement, T row, UUID stamp, int index) throws SQLException;
    }

    public int deleteMissingDataPoints(String dataType,
                                       StoredPoint.Representation representation,
                                       DataTypePolicy.RecordType recordType,
                                       DataTypePolicy.WindowTimeDomain windowTimeDomain,
                                       Instant start,
                                       Instant end,
                                       UUID syncRunId) {
        String windowPredicate = switch (windowTimeDomain) {
            case DATE -> "civil_date >= :civilStartDate AND civil_date < :civilEndDate";
            case CIVIL -> "civil_start_time >= :civilStartTime AND civil_start_time < :civilEndTime";
            case PHYSICAL -> switch (recordType) {
                case SESSION_END -> "physical_end_time >= :windowStart AND physical_end_time < :windowEnd";
                case INTERVAL, SAMPLE, SESSION_START -> "physical_start_time >= :windowStart AND physical_start_time < :windowEnd";
                case DAILY -> throw new IllegalArgumentException("Daily records require a civil-date window");
            };
        };
        String sql = """
                DELETE FROM google_health.data_point
                 WHERE connection_id = :connectionId
                   AND data_type = :dataType
                   AND representation = :representation
                   AND (%s)
                   AND last_seen_run IS DISTINCT FROM :syncRunId
                """.formatted(windowPredicate);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("connectionId", CONNECTION_ID)
                .addValue("dataType", dataType)
                .addValue("representation", representation.name())
                .addValue("windowStart", timestamp(start))
                .addValue("windowEnd", timestamp(end))
                .addValue("civilStartDate", start.atZone(ZoneOffset.UTC).toLocalDate())
                .addValue("civilEndDate", end.atZone(ZoneOffset.UTC).toLocalDate())
                .addValue("civilStartTime", LocalDateTime.ofInstant(start, ZoneOffset.UTC))
                .addValue("civilEndTime", LocalDateTime.ofInstant(end, ZoneOffset.UTC))
                .addValue("syncRunId", syncRunId);
        return namedJdbc.update(sql, params);
    }

    public int deleteMissingHeartRateSamples(Instant start, Instant end, UUID syncRunId) {
        String sql = """
                DELETE FROM google_health.heart_rate_sample
                 WHERE connection_id = :connectionId
                   AND sample_time >= :windowStart
                   AND sample_time < :windowEnd
                   AND last_seen_run IS DISTINCT FROM :syncRunId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("connectionId", CONNECTION_ID)
                .addValue("windowStart", timestamp(start))
                .addValue("windowEnd", timestamp(end))
                .addValue("syncRunId", syncRunId);
        return namedJdbc.update(sql, params);
    }

    public void prepareExerciseRoutes(Instant start, Instant end, boolean locationGranted) {
        ExercisePoint.RouteState targetState = locationGranted
                ? ExercisePoint.RouteState.PENDING : ExercisePoint.RouteState.NO_LOCATION_SCOPE;
        jdbc.update("""
                        UPDATE google_health.route_ownership
                           SET route_state = ?, route_error = NULL, updated_at = now()
                         WHERE connection_id = ?
                           AND native_day >= ?
                           AND native_day < ?
                           AND tcx_relative_path IS NULL
                           AND (route_state IN (?, ?) OR (? = FALSE AND route_state = 'PENDING'))
                        """, targetState.name(), CONNECTION_ID, start.atOffset(ZoneOffset.UTC).toLocalDate(), end.minusNanos(1).atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1),
                ExercisePoint.RouteState.NOT_APPLICABLE.name(), ExercisePoint.RouteState.NO_LOCATION_SCOPE.name(), locationGranted);
    }

    public List<ExercisePoint> findExerciseRouteCandidates(Instant start, Instant end) {
        return jdbc.query("""
                        SELECT id, google_resource_name, physical_start_time, physical_end_time,
                               fingerprint, raw_json::text, tcx_relative_path, route_state,
                               indexed_file_id, gps_track_id
                          FROM google_health.route_ownership
                         WHERE connection_id = ?
                           AND native_day >= ?
                           AND native_day < ?
                           AND route_state IN (?, ?)
                           AND NOT (route_state = 'FAILED' AND route_next_retry_at IS NULL)
                           AND (route_next_retry_at IS NULL OR route_next_retry_at <= now())
                           AND (tcx_relative_path IS NULL OR route_fingerprint IS DISTINCT FROM fingerprint)
                         ORDER BY physical_start_time, id
                        """, (rs, rowNum) -> mapExercisePoint(rs),
                CONNECTION_ID,
                start.atOffset(ZoneOffset.UTC).toLocalDate(), end.atOffset(ZoneOffset.UTC).toLocalDate(), ExercisePoint.RouteState.PENDING.name(),
                ExercisePoint.RouteState.FAILED.name());
    }

    public List<ExercisePoint> findMissingExerciseRoutes(
            Instant start, Instant end, UUID syncRunId) {
        return jdbc.query("""
                        SELECT id, google_resource_name, physical_start_time, physical_end_time,
                               fingerprint, raw_json::text, tcx_relative_path, route_state,
                               indexed_file_id, gps_track_id
                          FROM google_health.route_ownership
                         WHERE connection_id = ?
                           AND native_day >= ?
                           AND native_day < ?
                           AND NOT present
                           AND tcx_relative_path IS NOT NULL
                         ORDER BY id
                        """, (rs, rowNum) -> mapExercisePoint(rs),
                CONNECTION_ID,
                start.atOffset(ZoneOffset.UTC).toLocalDate(), end.atOffset(ZoneOffset.UTC).toLocalDate());
    }

    public List<String> findAllManagedRoutePaths() {
        return jdbc.queryForList("""
                SELECT DISTINCT tcx_relative_path
                  FROM google_health.route_ownership
                 WHERE connection_id = ? AND tcx_relative_path IS NOT NULL
                """, String.class, CONNECTION_ID);
    }

    public List<ExercisePoint> findAllManagedExerciseRoutes() {
        return jdbc.query("""
                SELECT id, google_resource_name, physical_start_time, physical_end_time,
                       fingerprint, raw_json::text, tcx_relative_path, route_state,
                       indexed_file_id, gps_track_id
                  FROM google_health.route_ownership
                 WHERE connection_id = ?
                   AND tcx_relative_path IS NOT NULL
                 ORDER BY id
                """, (rs, rowNum) -> mapExercisePoint(rs), CONNECTION_ID);
    }

    public void markExerciseRoutePublished(long pointId, String relativePath, String pointFingerprint,
                                           Instant fileModifiedAt, long fileSize) {
        jdbc.update("""
                        UPDATE google_health.route_ownership
                           SET tcx_relative_path = ?, route_fingerprint = ?, route_state = ?,
                               route_error = NULL, route_attempts = 0, route_next_retry_at = NULL,
                               route_file_modified_at = ?, route_file_size = ?
                         WHERE connection_id = ? AND id = ?
                        """, relativePath, pointFingerprint, ExercisePoint.RouteState.PENDING.name(),
                timestamp(fileModifiedAt), fileSize, CONNECTION_ID, pointId);
    }

    public void markExerciseRoutePublicationStarted(long pointId) {
        jdbc.update("""
                UPDATE google_health.route_ownership
                   SET route_state = ?, route_error = NULL,
                       route_file_modified_at = NULL, route_file_size = NULL
                 WHERE connection_id = ? AND id = ?
                """, ExercisePoint.RouteState.PENDING.name(), CONNECTION_ID, pointId);
    }

    public void markExerciseRouteState(long pointId, ExercisePoint.RouteState state, String safeError) {
        if (state == ExercisePoint.RouteState.FAILED) {
            recordExerciseRouteFailure(pointId, false, Instant.now(), safeError);
            return;
        }
        jdbc.update("""
                UPDATE google_health.route_ownership
                   SET route_state = ?, route_error = ?, route_next_retry_at = NULL,
                       updated_at = now()
                 WHERE connection_id = ? AND id = ?
                """, state.name(), safeError, CONNECTION_ID, pointId);
    }

    public void recordExerciseRouteFailure(long pointId, boolean recoverable, Instant now, String safeError) {
        var savedAttempts = jdbc.queryForList("SELECT route_attempts FROM google_health.route_ownership WHERE connection_id=? AND id=?",
                Integer.class, CONNECTION_ID, pointId);
        if (savedAttempts.isEmpty()) {
            log.info("Google Health route failure not saved: route ownership was removed");
            return;
        }
        int attempts = savedAttempts.getFirst();
        var decision = workRetryPolicy.afterFailure(attempts + 1, recoverable, now);
        jdbc.update("UPDATE google_health.route_ownership SET route_state='FAILED',route_error=?,route_attempts=route_attempts+1,"
                    + "route_next_retry_at=?,updated_at=now() WHERE connection_id=? AND id=?",
                safeError, timestamp(decision.retryAt()), CONNECTION_ID, pointId);
        log.info("Google Health route failure saved: attempts={}, automaticRetry={}, nextRetryAt={}",
                attempts + 1, decision.automaticRetry(), decision.retryAt());
    }

    /**
     * Called only by the serialized sync worker; its durable queue is independent of data windows.
     */
    public List<ExercisePoint> findDueExerciseRouteRetries(int limit) {
        return jdbc.query("""
                SELECT id, google_resource_name, physical_start_time, physical_end_time,
                       fingerprint, raw_json::text, tcx_relative_path, route_state,
                       indexed_file_id, gps_track_id
                  FROM google_health.route_ownership
                  WHERE connection_id = ? AND present AND google_resource_name IS NOT NULL
                    AND (route_state IN ('NOT_APPLICABLE', 'NO_LOCATION_SCOPE')
                         OR (route_state = 'FAILED' AND route_next_retry_at IS NOT NULL)
                        OR (route_state = 'PENDING'
                            AND (tcx_relative_path IS NULL OR route_fingerprint IS DISTINCT FROM fingerprint
                                 OR route_file_modified_at IS NULL)))
                   AND (route_next_retry_at IS NULL OR route_next_retry_at <= now())
                 ORDER BY route_next_retry_at NULLS FIRST, id
                 LIMIT ?
                """, (rs, rowNum) -> mapExercisePoint(rs), CONNECTION_ID, limit);
    }

    public RouteSummary routeSummary() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE NOT present AND tcx_relative_path IS NOT NULL) AS retained,
                       COUNT(*) FILTER (WHERE route_state = 'PENDING') AS pending,
                       COUNT(*) FILTER (WHERE route_state = 'FAILED') AS failed,
                       MIN(route_next_retry_at) FILTER (WHERE route_state = 'FAILED') AS next_retry,
                       (array_agg(route_error ORDER BY updated_at DESC)
                           FILTER (WHERE route_state = 'FAILED' AND route_error IS NOT NULL))[1] AS last_error
                  FROM google_health.route_ownership
                 WHERE connection_id = ?
                """, (rs, rowNum) -> new RouteSummary(rs.getLong("pending"), rs.getLong("failed"),
                rs.getString("last_error"), instant(rs, "next_retry"), rs.getLong("retained")), CONNECTION_ID);
    }

    public List<ExercisePoint> findPublishedExercisesAwaitingAssociation() {
        return findPublishedExercisesAwaitingAssociation(Integer.MAX_VALUE);
    }

    public List<ExercisePoint> findPublishedExercisesAwaitingAssociation(int limit) {
        return jdbc.query("""
                        SELECT point.id, point.google_resource_name, point.physical_start_time,
                               point.physical_end_time, point.fingerprint, point.raw_json::text,
                               point.tcx_relative_path, point.route_state, file.id AS indexed_file_id,
                               track.id AS gps_track_id
                          FROM google_health.route_ownership point
                          JOIN public.indexed_file file
                            ON file.index = 'GPS'
                           AND file.indexer_status = 'COMPLETED_WITH_SUCCESS'
                           AND (CASE WHEN file.path = '' THEN file.name ELSE file.path || '/' || file.name END)
                               = point.tcx_relative_path
                          JOIN LATERAL (
                               SELECT candidate.id
                                 FROM public.gps_track candidate
                                WHERE candidate.file_id = file.id
                                  AND candidate.load_status = 'SUCCESS'
                                ORDER BY candidate.source_segment_index NULLS FIRST, candidate.id
                                LIMIT 1
                          ) track ON TRUE
                         WHERE point.connection_id = ?
                           AND TRUE
                           AND point.tcx_relative_path IS NOT NULL
                           AND point.route_state = ?
                           AND point.route_fingerprint = point.fingerprint
                           AND file.last_modified_date = point.route_file_modified_at
                           AND file.size = point.route_file_size
                         ORDER BY point.id LIMIT ?
                        """, (rs, rowNum) -> mapExercisePoint(rs), CONNECTION_ID,
                ExercisePoint.RouteState.PENDING.name(), limit);
    }

    public int associateExerciseRoute(long pointId, long indexedFileId, long gpsTrackId, String pointFingerprint) {
        return jdbc.update("""
                        UPDATE google_health.route_ownership point
                           SET indexed_file_id = ?, gps_track_id = ?, route_state = ?,
                               route_error = NULL, route_next_retry_at = NULL, updated_at = now()
                          FROM public.indexed_file file, public.gps_track track
                         WHERE point.connection_id = ? AND point.id = ?
                           AND file.id = ? AND track.id = ? AND track.file_id = file.id
                           AND file.indexer_status = 'COMPLETED_WITH_SUCCESS' AND track.load_status = 'SUCCESS'
                           AND point.route_state = 'PENDING' AND point.route_fingerprint = point.fingerprint
                           AND point.fingerprint = ?
                           AND file.last_modified_date = point.route_file_modified_at
                           AND file.size = point.route_file_size
                        """, indexedFileId, gpsTrackId, ExercisePoint.RouteState.IMPORTED.name(), CONNECTION_ID,
                pointId, indexedFileId, gpsTrackId, pointFingerprint);
    }

    public void replaceExerciseTrackAssociations(Map<Long, Long> replacementTrackIds) {
        replacementTrackIds.forEach((previousTrackId, replacementTrackId) -> jdbc.update("""
                UPDATE google_health.route_ownership
                   SET gps_track_id = ?, updated_at = now()
                 WHERE connection_id = ?
                   AND gps_track_id = ?
                """, replacementTrackId, CONNECTION_ID, previousTrackId));
    }

    public List<ExercisePoint> findExercisesAssociatedWithTracks(Set<Long> trackIds) {
        if (trackIds == null || trackIds.isEmpty()) {
            return List.of();
        }
        return namedJdbc.query("""
                        SELECT id, google_resource_name, physical_start_time, physical_end_time,
                               fingerprint, raw_json::text, tcx_relative_path, route_state,
                               indexed_file_id, gps_track_id
                          FROM google_health.route_ownership
                         WHERE connection_id = :connectionId
                           AND TRUE
                           AND gps_track_id IN (:trackIds)
                         ORDER BY id
                        """, new MapSqlParameterSource()
                        .addValue("connectionId", CONNECTION_ID)
                        .addValue("exerciseDataType", DataTypePolicy.EXERCISE_DATA_TYPE)
                        .addValue("trackIds", trackIds),
                (rs, rowNum) -> mapExercisePoint(rs));
    }

    public List<GHTypeSummary> findTypeSummaries() {
        return jdbc.query("""
                        WITH totals AS (
                            SELECT data_type, representation, COUNT(*) AS record_count,
                                   MIN(COALESCE(physical_start_time,
                                                civil_start_time AT TIME ZONE 'UTC',
                                                civil_date::timestamp AT TIME ZONE 'UTC')) AS earliest_record,
                                   MAX(COALESCE(physical_start_time,
                                                civil_start_time AT TIME ZONE 'UTC',
                                                civil_date::timestamp AT TIME ZONE 'UTC')) AS latest_record,
                                   CASE WHEN bool_and(physical_start_time IS NULL) AND bool_and(%s)
                                        THEN 'CALENDAR_DATE'
                                        WHEN bool_and(physical_start_time IS NULL) THEN 'CIVIL'
                                        ELSE 'PHYSICAL' END AS temporal_kind,
                                   MIN(civil_date) AS earliest_civil_date, MAX(civil_date) AS latest_civil_date,
                                   MIN(civil_start_time)::text AS earliest_civil_time,
                                   MAX(civil_start_time)::text AS latest_civil_time
                              FROM google_health.data_point
                             WHERE connection_id = ?
                             GROUP BY data_type, representation
                            UNION ALL
                            SELECT ?, ?, COUNT(*),
                                   (SELECT sample_time
                                      FROM google_health.heart_rate_sample
                                     WHERE connection_id = ?
                                     ORDER BY sample_time ASC
                                     LIMIT 1),
                                   (SELECT sample_time
                                      FROM google_health.heart_rate_sample
                                     WHERE connection_id = ?
                                     ORDER BY sample_time DESC
                                     LIMIT 1), 'PHYSICAL', NULL::date, NULL::date, NULL::text, NULL::text
                              FROM google_health.heart_rate_sample
                             WHERE connection_id = ?
                            HAVING COUNT(*) > 0
                        ), states AS (
                            SELECT data_type, representation, last_success_at, last_error
                              FROM google_health.sync_state
                             WHERE connection_id = ?
                        )
                        SELECT COALESCE(totals.data_type, states.data_type) AS data_type,
                               COALESCE(totals.representation, states.representation) AS representation,
                               COALESCE(totals.record_count, 0) AS record_count,
                               totals.earliest_record, totals.latest_record,
                               COALESCE(totals.temporal_kind, 'PHYSICAL') AS temporal_kind,
                               totals.earliest_civil_date, totals.latest_civil_date,
                               totals.earliest_civil_time, totals.latest_civil_time,
                               states.last_success_at, states.last_error
                          FROM totals
                          FULL OUTER JOIN states
                            ON states.data_type = totals.data_type
                           AND states.representation = totals.representation
                         ORDER BY data_type, representation
                        """.formatted(CALENDAR_RECORD_PREDICATE), (rs, rowNum) -> new GHTypeSummary(
                        rs.getString("data_type"),
                        rs.getString("representation"),
                        rs.getLong("record_count"),
                        instant(rs, "earliest_record"),
                        instant(rs, "latest_record"),
                        RecordRow.TemporalKind.valueOf(rs.getString("temporal_kind")),
                        rs.getString("earliest_civil_date"), rs.getString("latest_civil_date"),
                        rs.getString("earliest_civil_time") == null ? null : rs.getString("earliest_civil_time").replace(' ', 'T'),
                        rs.getString("latest_civil_time") == null ? null : rs.getString("latest_civil_time").replace(' ', 'T'),
                        instant(rs, "last_success_at"),
                        rs.getString("last_error")
                ), CONNECTION_ID,
                DataTypePolicy.HEART_RATE_DATA_TYPE, DataTypePolicy.Mode.HEART_RATE_SAMPLE.name(),
                CONNECTION_ID, CONNECTION_ID, CONNECTION_ID,
                CONNECTION_ID);
    }

    public List<GHDataSourceSummary> findDataSourceSummaries() {
        return jdbc.query("""
                SELECT source.id, source.application_name, source.device_display_name,
                       source.platform,
                       (SELECT COUNT(*) FROM google_health.data_point point
                         WHERE point.data_source_id = source.id)
                       +
                       (SELECT COUNT(*) FROM google_health.heart_rate_sample sample
                         WHERE sample.data_source_id = source.id) AS record_count
                  FROM google_health.data_source source
                 WHERE source.connection_id = ?
                 ORDER BY COALESCE(source.device_display_name, source.application_name, source.platform), source.id
                """, (rs, rowNum) -> new GHDataSourceSummary(
                rs.getLong("id"),
                rs.getString("application_name"),
                rs.getString("device_display_name"),
                rs.getString("platform"),
                rs.getLong("record_count")
        ), CONNECTION_ID);
    }

    public List<RecordRow> findRecords(
            String dataType,
            String representation,
            Instant start,
            Instant end,
            Long dataSourceId,
            Instant cursorTime,
            Integer cursorKind,
            Long cursorId,
            int limit) {
        return findRecords(dataType, representation, start, end, null, null, dataSourceId,
                cursorTime, cursorKind, cursorId, limit);
    }

    public List<RecordRow> findRecords(String dataType, String representation, Instant start, Instant end,
                                       LocalDate startDate, LocalDate endDateExclusive, Long dataSourceId,
                                       Instant cursorTime, Integer cursorKind, Long cursorId, int limit) {
        String normalizedDataType = blankToNull(dataType);
        String normalizedRepresentation = blankToNull(representation);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("connectionId", CONNECTION_ID)
                .addValue("pointKindOrder", DATA_POINT_KIND_ORDER)
                .addValue("sampleKindOrder", HEART_RATE_KIND_ORDER)
                .addValue("pointKind", RecordRow.RecordKind.DATA_POINT.name())
                .addValue("sampleKind", RecordRow.RecordKind.HEART_RATE_SAMPLE.name())
                .addValue("heartRateDataType", DataTypePolicy.HEART_RATE_DATA_TYPE)
                .addValue("heartRateRepresentation", DataTypePolicy.Mode.HEART_RATE_SAMPLE.name())
                .addValue("limit", limit, Types.INTEGER);
        if (cursorTime != null) {
            params.addValue("cursorTime", timestamp(cursorTime), Types.TIMESTAMP)
                    .addValue("cursorKind", cursorKind == null ? 0 : cursorKind, Types.INTEGER)
                    .addValue("cursorId", cursorId == null ? Long.MAX_VALUE : cursorId, Types.BIGINT);
        }

        StringBuilder pointCandidates = new StringBuilder("""
                SELECT point.id, :pointKindOrder AS kind_order,
                       %s AS record_time
                  FROM google_health.data_point point
                 WHERE point.connection_id = :connectionId
                """.formatted(DATA_POINT_RECORD_TIME));
        addEqualsFilter(pointCandidates, params, "point.data_type", "dataType", normalizedDataType, Types.VARCHAR);
        addEqualsFilter(pointCandidates, params, "point.representation", "representation",
                normalizedRepresentation, Types.VARCHAR);
        if (startDate != null || endDateExclusive != null) {
            addCalendarRecordFilter(pointCandidates, params, "start", startDate, start, ">=");
            addCalendarRecordFilter(pointCandidates, params, "end", endDateExclusive, end, "<");
        } else {
            addRangeFilter(pointCandidates, params, DATA_POINT_RECORD_TIME, "startTime", start, ">=");
            addRangeFilter(pointCandidates, params, DATA_POINT_RECORD_TIME, "endTime", end, "<");
        }
        addEqualsFilter(pointCandidates, params, "point.data_source_id", "dataSourceId", dataSourceId, Types.BIGINT);
        addRecordCursorFilter(
                pointCandidates, DATA_POINT_RECORD_TIME, DATA_POINT_KIND_ORDER, "point.id", cursorTime);
        pointCandidates.append("""
                 ORDER BY record_time DESC, point.id DESC
                 LIMIT :limit
                """);

        StringBuilder sampleCandidates = new StringBuilder("""
                SELECT sample.id, :sampleKindOrder AS kind_order,
                       sample.sample_time AS record_time
                  FROM google_health.heart_rate_sample sample
                 WHERE sample.connection_id = :connectionId
                """);
        boolean includeHeartRateSamples =
                (normalizedDataType == null || DataTypePolicy.HEART_RATE_DATA_TYPE.equals(normalizedDataType))
                && (normalizedRepresentation == null
                    || DataTypePolicy.Mode.HEART_RATE_SAMPLE.name().equals(normalizedRepresentation));
        if (!includeHeartRateSamples) {
            sampleCandidates.append(" AND FALSE\n");
        } else {
            addRangeFilter(sampleCandidates, params, "sample.sample_time", "startTime", start, ">=");
            addRangeFilter(sampleCandidates, params, "sample.sample_time", "endTime", end, "<");
            addEqualsFilter(
                    sampleCandidates, params, "sample.data_source_id", "dataSourceId", dataSourceId, Types.BIGINT);
            addRecordCursorFilter(
                    sampleCandidates, "sample.sample_time", HEART_RATE_KIND_ORDER, "sample.id", cursorTime);
        }
        sampleCandidates.append("""
                 ORDER BY sample.sample_time DESC, sample.id DESC
                 LIMIT :limit
                """);

        String sql = """
                WITH candidate_records AS (
                    (%s)
                    UNION ALL
                    (%s)
                ),
                selected_records AS MATERIALIZED (
                    SELECT id, kind_order, record_time
                      FROM candidate_records
                     ORDER BY record_time DESC, kind_order, id DESC
                     LIMIT :limit
                ),
                records AS (
                    SELECT point.id, selected.kind_order, :pointKind AS kind,
                           point.data_type, point.representation, selected.record_time,
                           point.original_start_time AS original_time,
                           NULL::text AS value_summary,
                           point.data_source_id, source.device_display_name,
                           source.application_name, source.platform,
                           COALESCE(owner.route_state,'NOT_APPLICABLE') AS route_state, owner.route_error,
                           CASE WHEN point.physical_start_time IS NOT NULL THEN 'PHYSICAL'
                                WHEN %s THEN 'CALENDAR_DATE'
                                ELSE 'CIVIL' END AS temporal_kind,
                           point.civil_date, point.civil_start_time AS civil_time, owner.gps_track_id,
                           NULL::text AS raw_json, %s AS value_json,
                           point.physical_end_time AS end_time
                      FROM selected_records selected
                      JOIN google_health.data_point point ON point.id = selected.id
                      LEFT JOIN google_health.route_ownership owner ON owner.connection_id=point.connection_id
                       AND owner.identity_key=point.identity_key AND point.data_type='exercise'
                      LEFT JOIN google_health.data_source source ON source.id = point.data_source_id
                     WHERE selected.kind_order = :pointKindOrder
                    UNION ALL
                    SELECT sample.id, selected.kind_order, :sampleKind AS kind,
                           :heartRateDataType AS data_type, :heartRateRepresentation AS representation,
                           selected.record_time,
                           sample.original_sample_time AS original_time,
                           sample.beats_per_minute::text || ' bpm' AS value_summary,
                           sample.data_source_id, source.device_display_name,
                           source.application_name, source.platform,
                           NULL::text AS route_state, NULL::text AS route_error,
                           'PHYSICAL' AS temporal_kind, NULL::date AS civil_date,
                           NULL::timestamp AS civil_time, NULL::bigint AS gps_track_id,
                           NULL::text AS raw_json,
                           jsonb_build_object('heartRate', jsonb_build_object(
                               'beatsPerMinute', sample.beats_per_minute))::text AS value_json,
                           NULL::timestamptz AS end_time
                      FROM selected_records selected
                      JOIN google_health.heart_rate_sample sample ON sample.id = selected.id
                      LEFT JOIN google_health.data_source source ON source.id = sample.data_source_id
                     WHERE selected.kind_order = :sampleKindOrder
                )
                SELECT * FROM records
                 ORDER BY record_time DESC, kind_order, id DESC
                """.formatted(pointCandidates, sampleCandidates, CALENDAR_RECORD_PREDICATE, RECORD_VALUE_JSON);
        return namedJdbc.query(sql, params, (rs, rowNum) -> mapRecordRow(rs));
    }

    public List<MetricRecordRow> findMetricRecords(Instant start, Instant end) {
        return jdbc.query("""
                        SELECT point.data_type, point.representation,
                               COALESCE(point.physical_start_time,
                                        point.civil_date::timestamp AT TIME ZONE 'UTC') AS record_time,
                               point.physical_end_time AS end_time,
                               point.raw_json::text AS raw_json
                          FROM google_health.data_point point
                         WHERE point.connection_id = ?
                           AND COALESCE(point.physical_start_time,
                                        point.civil_date::timestamp AT TIME ZONE 'UTC') >= ?
                           AND COALESCE(point.physical_start_time,
                                        point.civil_date::timestamp AT TIME ZONE 'UTC') < ?
                           AND (
                               (point.representation = ?
                                AND point.data_type IN ('steps', 'distance', 'active-energy-burned', ?))
                               OR
                               (point.representation = ?
                                AND point.data_type IN (
                                    'sleep', 'daily-resting-heart-rate', 'daily-heart-rate-variability',
                                    'weight', 'body-fat', 'daily-oxygen-saturation',
                                    'daily-respiratory-rate', 'daily-vo2-max',
                                    'daily-sleep-temperature-derivations', 'core-body-temperature',
                                    'blood-glucose'))
                           )
                         ORDER BY record_time, point.id
                        """, (rs, rowNum) -> new MetricRecordRow(
                        rs.getString("data_type"),
                        rs.getString("representation"),
                        instant(rs, "record_time"),
                        instant(rs, "end_time"),
                        rs.getString("raw_json")
                ), CONNECTION_ID, timestamp(start), timestamp(end), StoredPoint.Representation.DAILY_ROLLUP.name(),
                DataTypePolicy.HEART_RATE_DATA_TYPE, StoredPoint.Representation.RAW.name());
    }

    public List<MetricRecordRow> findIntradayMetricRecords(Instant start, Instant end) {
        return jdbc.query("""
                        SELECT data_type, representation, physical_start_time, physical_end_time, raw_json::text
                        FROM google_health.data_point
                        WHERE connection_id = ? AND representation = 'RAW'
                          AND physical_start_time >= ? AND physical_start_time < ?
                          AND data_type IN ('weight', 'body-fat', 'core-body-temperature', 'blood-glucose')
                        ORDER BY physical_start_time, id
                        """, (rs, rowNum) -> new MetricRecordRow(
                        rs.getString("data_type"), rs.getString("representation"),
                        instant(rs, "physical_start_time"), instant(rs, "physical_end_time"),
                        rs.getString("raw_json")), CONNECTION_ID, timestamp(start), timestamp(end));
    }

    public List<GroupedMetricRecordRow> findGroupedMetricRecords(LocalDate start, LocalDate end, String timeZone) {
        var zone = ZoneId.of(timeZone);
        // Bound stored date/timestamp columns directly so their existing indexes remain usable.
        return jdbc.query("""
                        SELECT data_type, representation, physical_start_time, physical_end_time, raw_json, CASE
                            WHEN representation = 'DAILY_ROLLUP' OR data_type LIKE 'daily-%' THEN civil_date
                            ELSE (physical_start_time AT TIME ZONE ?)::date END AS metric_date
                        FROM google_health.data_point point
                        WHERE connection_id = ? AND (
                            (civil_date >= ? AND civil_date < ? AND (
                                (representation = 'DAILY_ROLLUP'
                                 AND data_type IN ('steps', 'distance', 'active-energy-burned', 'heart-rate'))
                                OR (representation = 'RAW' AND data_type IN (
                                    'daily-resting-heart-rate', 'daily-heart-rate-variability',
                                    'daily-oxygen-saturation', 'daily-respiratory-rate', 'daily-vo2-max',
                                    'daily-sleep-temperature-derivations'))))
                            OR (physical_start_time >= ? AND physical_start_time < ?
                                AND representation = 'RAW' AND data_type IN (
                                    'sleep', 'weight', 'body-fat', 'core-body-temperature', 'blood-glucose')))
                        ORDER BY metric_date, physical_start_time, id
                        """, (rs, rowNum) -> new GroupedMetricRecordRow(
                        rs.getObject("metric_date", LocalDate.class),
                        new MetricRecordRow(rs.getString("data_type"), rs.getString("representation"),
                                instant(rs, "physical_start_time"), instant(rs, "physical_end_time"),
                                rs.getString("raw_json"))), timeZone, CONNECTION_ID, start, end,
                timestamp(start.atStartOfDay(zone).toInstant()), timestamp(end.atStartOfDay(zone).toInstant()));
    }

    /**
     * Average the samples themselves, never averages of unequal time buckets.
     */
    public List<HeartRateDay> findHeartRateDays(Instant start, Instant end, String timeZone) {
        return findHeartRateDaysForRanges(List.of(new HeartRateTimeRange(start, end)), timeZone);
    }

    /**
     * No query at all when daily rollups cover the selected dates. Gaps are read in one bounded query.
     */
    public List<HeartRateDay> findHeartRateDaysForRanges(List<HeartRateTimeRange> ranges, String timeZone) {
        if (ranges.isEmpty()) return List.of();
        var params = new MapSqlParameterSource().addValue("timeZone", timeZone).addValue("connectionId", CONNECTION_ID);
        var values = new ArrayList<String>();
        for (int index = 0; index < ranges.size(); index++) {
            values.add("(CAST(:start" + index + " AS timestamptz), CAST(:end" + index + " AS timestamptz))");
            params.addValue("start" + index, timestamp(ranges.get(index).start()));
            params.addValue("end" + index, timestamp(ranges.get(index).end()));
        }
        String sql = "WITH requested_ranges(start_time, end_time) AS (VALUES " + String.join(",", values) + ") " + """
                SELECT (sample.sample_time AT TIME ZONE :timeZone)::date AS metric_date,
                       AVG(sample.beats_per_minute) AS average_bpm,
                       MIN(sample.beats_per_minute) AS minimum_bpm, MAX(sample.beats_per_minute) AS maximum_bpm
                FROM requested_ranges requested
                JOIN google_health.heart_rate_sample sample
                  ON sample.sample_time >= requested.start_time AND sample.sample_time < requested.end_time
                 AND sample.connection_id = :connectionId
                GROUP BY metric_date ORDER BY metric_date
                """;
        return namedJdbc.query(sql, params, (rs, rowNum) -> new HeartRateDay(rs.getObject("metric_date", LocalDate.class),
                rs.getDouble("average_bpm"), rs.getDouble("minimum_bpm"), rs.getDouble("maximum_bpm")));
    }

    public List<HeartRateBucket> findHeartRateBuckets(Instant start, Instant end, int bucketSeconds) {
        return jdbc.query("""
                SELECT date_bin(make_interval(secs => ?), sample.sample_time,
                                TIMESTAMPTZ '1970-01-01 00:00:00+00') AS bucket_time,
                       AVG(sample.beats_per_minute) AS average_bpm,
                       MIN(sample.beats_per_minute) AS minimum_bpm,
                       MAX(sample.beats_per_minute) AS maximum_bpm
                  FROM google_health.heart_rate_sample sample
                 WHERE sample.connection_id = ?
                   AND sample.sample_time >= ?
                   AND sample.sample_time < ?
                 GROUP BY bucket_time
                 ORDER BY bucket_time
                """, (rs, rowNum) -> new HeartRateBucket(
                instant(rs, "bucket_time"),
                rs.getDouble("average_bpm"),
                rs.getInt("minimum_bpm"),
                rs.getInt("maximum_bpm")
        ), bucketSeconds, CONNECTION_ID, timestamp(start), timestamp(end));
    }

    private static void addCalendarRecordFilter(StringBuilder sql, MapSqlParameterSource params,
                                                String prefix, LocalDate date, Instant physical,
                                                String operator) {
        if (date == null) return;
        Instant civilBoundary = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant indexBoundary = operator.equals(">=")
                ? (physical.isBefore(civilBoundary) ? physical : civilBoundary)
                : (physical.isAfter(civilBoundary) ? physical : civilBoundary);
        // This conservative range is indexable; the following predicate still enforces
        // each record's exact physical, civil, or calendar boundary.
        sql.append(" AND ").append(DATA_POINT_RECORD_TIME).append(' ').append(operator)
                .append(" :").append(prefix).append("IndexBoundary\n");
        params.addValue(prefix + "IndexBoundary", timestamp(indexBoundary), Types.TIMESTAMP);
        sql.append(" AND ((point.physical_start_time IS NOT NULL AND point.physical_start_time ")
                .append(operator).append(" :").append(prefix).append("Physical)")
                .append(" OR (point.physical_start_time IS NULL AND point.civil_date IS NULL AND point.civil_start_time IS NOT NULL")
                .append(" AND point.civil_start_time ").append(operator).append(" :").append(prefix).append("Civil)")
                .append(" OR (point.physical_start_time IS NULL AND point.civil_date IS NOT NULL")
                .append(" AND point.civil_date ").append(operator).append(" :").append(prefix).append("Date))\n");
        params.addValue(prefix + "Physical", timestamp(physical), Types.TIMESTAMP)
                .addValue(prefix + "Civil", date.atStartOfDay(), Types.TIMESTAMP)
                .addValue(prefix + "Date", date, Types.DATE);
    }

    private static void addEqualsFilter(StringBuilder sql,
                                        MapSqlParameterSource params,
                                        String column,
                                        String parameter,
                                        Object value,
                                        int sqlType) {
        if (value == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" = :").append(parameter).append('\n');
        params.addValue(parameter, value, sqlType);
    }

    private static void addRangeFilter(StringBuilder sql,
                                       MapSqlParameterSource params,
                                       String column,
                                       String parameter,
                                       Instant value,
                                       String operator) {
        if (value == null) {
            return;
        }
        sql.append(" AND ").append(column).append(' ').append(operator).append(" :").append(parameter).append('\n');
        params.addValue(parameter, timestamp(value), Types.TIMESTAMP);
    }

    private static void addRecordCursorFilter(StringBuilder sql,
                                              String recordTime,
                                              int kindOrder,
                                              String id,
                                              Instant cursorTime) {
        if (cursorTime == null) {
            return;
        }
        sql.append("""
                 AND %1$s <= :cursorTime
                 AND (%1$s < :cursorTime
                      OR (%1$s = :cursorTime AND %2$d > :cursorKind)
                      OR (%1$s = :cursorTime AND %2$d = :cursorKind AND %3$s < :cursorId))
                """.formatted(recordTime, kindOrder, id));
    }

    public Optional<RecordRow> findRecord(RecordRow.RecordKind kind, long id) {
        String sql = kind == RecordRow.RecordKind.DATA_POINT
                ? """
                SELECT point.id, ? AS kind, point.data_type, point.representation,
                       COALESCE(point.physical_start_time,
                                point.civil_start_time AT TIME ZONE 'UTC',
                                point.civil_date::timestamp AT TIME ZONE 'UTC') AS record_time,
                       point.original_start_time AS original_time, NULL::text AS value_summary,
                       point.data_source_id, source.device_display_name, source.application_name,
                       source.platform, COALESCE(owner.route_state,'NOT_APPLICABLE') AS route_state, owner.route_error,
                       CASE WHEN point.physical_start_time IS NOT NULL THEN 'PHYSICAL'
                            WHEN %s THEN 'CALENDAR_DATE'
                            ELSE 'CIVIL' END AS temporal_kind,
                       point.civil_date, point.civil_start_time AS civil_time, owner.gps_track_id,
                       point.raw_json::text AS raw_json, %s AS value_json, point.physical_end_time AS end_time
                  FROM google_health.data_point point
                  LEFT JOIN google_health.route_ownership owner ON owner.identity_key=point.identity_key AND owner.connection_id=point.connection_id AND point.data_type='exercise'
                  LEFT JOIN google_health.data_source source ON source.id = point.data_source_id
                 WHERE point.connection_id = ? AND point.id = ?
                """.formatted(CALENDAR_RECORD_PREDICATE, RECORD_VALUE_JSON)
                : """
                SELECT sample.id, ? AS kind, ? AS data_type,
                       ? AS representation, sample.sample_time AS record_time,
                       sample.original_sample_time AS original_time,
                       sample.beats_per_minute::text || ' bpm' AS value_summary,
                       sample.data_source_id, source.device_display_name, source.application_name,
                       source.platform, NULL::text AS route_state, NULL::text AS route_error,
                       'PHYSICAL' AS temporal_kind, NULL::date AS civil_date,
                       NULL::timestamp AS civil_time, NULL::bigint AS gps_track_id,
                       COALESCE(sample.raw_json,jsonb_build_object(
                           'heartRate', jsonb_strip_nulls(jsonb_build_object(
                               'beatsPerMinute', sample.beats_per_minute,
                               'sampleTime', jsonb_strip_nulls(jsonb_build_object(
                                   'physicalTime', sample.original_sample_time,
                                   'utcOffset', sample.utc_offset,
                                   'civilTime', sample.civil_time
                               )),
                               'metadata', jsonb_strip_nulls(jsonb_build_object(
                                   'motionContext', sample.motion_context,
                                   'sensorLocation', sample.sensor_location
                               ))
                           ))
                       ))::text AS raw_json,
                       jsonb_build_object('heartRate', jsonb_build_object(
                           'beatsPerMinute', sample.beats_per_minute))::text AS value_json,
                       NULL::timestamptz AS end_time
                  FROM google_health.heart_rate_sample sample
                  LEFT JOIN google_health.data_source source ON source.id = sample.data_source_id
                 WHERE sample.connection_id = ? AND sample.id = ?
                """;
        Object[] params = kind == RecordRow.RecordKind.DATA_POINT
                ? new Object[]{kind.name(), CONNECTION_ID, id}
                : new Object[]{kind.name(), DataTypePolicy.HEART_RATE_DATA_TYPE, DataTypePolicy.Mode.HEART_RATE_SAMPLE.name(),
                CONNECTION_ID, id};
        return jdbc.query(sql, (rs, rowNum) -> mapRecordRow(rs), params).stream().findFirst();
    }

    private void ensureConnectionRow() {
        jdbc.update("""
                INSERT INTO google_health.connection (id)
                VALUES (?)
                ON CONFLICT (id) DO NOTHING
                """, CONNECTION_ID);
    }

    private boolean hasMirrorState() {
        Boolean result = jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM google_health.data_point WHERE connection_id = ?
                        ) OR EXISTS (
                            SELECT 1 FROM google_health.heart_rate_sample WHERE connection_id = ?
                        ) OR EXISTS (
                            SELECT 1 FROM google_health.data_source WHERE connection_id = ?
                        ) OR EXISTS (
                            SELECT 1 FROM google_health.sync_state WHERE connection_id = ?
                        ) OR EXISTS (SELECT 1 FROM google_health.history_audit)
                          OR EXISTS (SELECT 1 FROM google_health.history_work)
                          OR EXISTS (SELECT 1 FROM google_health.recent_pass)
                          OR EXISTS (SELECT 1 FROM google_health.route_ownership)
                        """, Boolean.class,
                CONNECTION_ID, CONNECTION_ID, CONNECTION_ID, CONNECTION_ID);
        return Boolean.TRUE.equals(result);
    }

    private AccountConnection mapConnection(ResultSet rs) throws SQLException {
        return new AccountConnection(
                rs.getString("google_user_id"),
                parseScopes(rs.getString("granted_scopes")),
                rs.getString("refresh_token_ciphertext"),
                AccountConnection.ConnectionState.valueOf(rs.getString("state")),
                rs.getString("last_error"),
                instant(rs, "connected_at"),
                instant(rs, "refresh_token_expires_at"),
                instant(rs, "disconnected_at"),
                instant(rs, "updated_at"),
                rs.getLong("authorization_generation")
        );
    }

    private static ExercisePoint mapExercisePoint(ResultSet rs) throws SQLException {
        return new ExercisePoint(
                rs.getLong("id"),
                rs.getString("google_resource_name"),
                instant(rs, "physical_start_time"),
                instant(rs, "physical_end_time"),
                rs.getString("fingerprint"),
                rs.getString("raw_json"),
                rs.getString("tcx_relative_path"),
                ExercisePoint.RouteState.valueOf(rs.getString("route_state")),
                nullableLong(rs, "indexed_file_id"),
                nullableLong(rs, "gps_track_id")
        );
    }

    private static RecordRow mapRecordRow(ResultSet rs) throws SQLException {
        String sourceName = rs.getString("device_display_name");
        if (sourceName == null || sourceName.isBlank()) sourceName = rs.getString("application_name");
        return new RecordRow(
                rs.getLong("id"),
                RecordRow.RecordKind.valueOf(rs.getString("kind")),
                rs.getString("data_type"),
                rs.getString("representation"),
                instant(rs, "record_time"),
                RecordRow.TemporalKind.valueOf(rs.getString("temporal_kind")),
                rs.getObject("civil_date", LocalDate.class),
                rs.getObject("civil_time", LocalDateTime.class) == null ? null
                        : rs.getObject("civil_time", LocalDateTime.class).toString(),
                rs.getString("original_time"),
                rs.getString("value_summary"),
                nullableLong(rs, "data_source_id"),
                sourceName,
                rs.getString("platform"),
                rs.getString("route_state"),
                rs.getString("route_error"),
                nullableLong(rs, "gps_track_id"),
                rs.getString("raw_json"),
                instant(rs, "end_time"),
                rs.getString("value_json")
        );
    }

    private MapSqlParameterSource scope(String account, DataTypePolicy.Policy policy) {
        return new MapSqlParameterSource().addValue("account", account).addValue("type", policy.dataType())
                .addValue("representation", policy.representation())
                .addValue("calendar", SyncWindowPolicy.CALENDAR_POLICY)
                .addValue("key", SyncWindowPolicy.KEY_POLICY);
    }

    private static final String SYNC_SCOPE = "account_id=:account AND data_type=:type AND representation=:representation "
                                             + "AND calendar_policy=:calendar AND key_policy=:key";

    public long synchronizationGeneration(String account) {
        jdbc.update("INSERT INTO google_health.sync_control(account_id) VALUES (?) ON CONFLICT DO NOTHING", account);
        return jdbc.queryForObject("SELECT generation FROM google_health.sync_control WHERE account_id=?", Long.class, account);
    }

    /**
     * Must be called within the same short transaction as the mirror write/checkpoint.
     */
    public void guardSynchronization(AccountConnection connection, long generation) {
        var current = jdbc.queryForList("SELECT google_user_id,authorization_generation,state FROM google_health.connection WHERE id=1 FOR UPDATE");
        var control = jdbc.queryForList("SELECT generation FROM google_health.sync_control WHERE account_id=? FOR UPDATE", connection.googleUserId());
        if (current.isEmpty() || control.isEmpty()
            || !connection.googleUserId().equals(current.getFirst().get("google_user_id"))
            || ((Number) current.getFirst().get("authorization_generation")).longValue() != connection.authorizationGeneration()
            || !"CONNECTED".equals(current.getFirst().get("state"))
            || ((Number) control.getFirst().get("generation")).longValue() != generation) {
            throw new CancellationException("Synchronization ownership was revoked");
        }
    }

    public int rotation(String account, boolean history) {
        return jdbc.queryForObject("SELECT " + (history ? "history_rotation" : "recent_rotation")
                                   + " FROM google_health.sync_control WHERE account_id=?", Integer.class, account);
    }

    public void rotate(String account, boolean history, int value) {
        jdbc.update("UPDATE google_health.sync_control SET " + (history ? "history_rotation" : "recent_rotation") + "=? WHERE account_id=?", value, account);
    }

    public Instant cooldown() {
        return jdbc.queryForObject("SELECT MAX(cooldown_until) FROM google_health.sync_control", (rs, n) -> instant(rs, "max"));
    }

    public void saveCooldown(Instant until) {
        jdbc.update("UPDATE google_health.sync_control SET cooldown_until=GREATEST(cooldown_until,?),updated_at=now()", timestamp(until));
    }

    public boolean isFinal(String account, DataTypePolicy.Policy policy, LocalDate day) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM google_health.history_audit WHERE "
                                                            + SYNC_SCOPE + " AND native_day=:day AND outcome IN ('DATA','EMPTY'))", scope(account, policy).addValue("day", day), Boolean.class));
    }

    public record RecentPass(UUID id, Instant startedAt, Instant start, Instant end, Instant nextEnd, String state,
                             Instant nextDueAt, long cycles, long skipped, long handedOver) {
    }

    public Optional<RecentPass> recentPass(String account, DataTypePolicy.Policy policy) {
        return namedJdbc.query("SELECT * FROM google_health.recent_pass WHERE " + SYNC_SCOPE, scope(account, policy),
                (rs, n) -> new RecentPass(UUID.fromString(rs.getString("id")), instant(rs, "started_at"), instant(rs, "target_start"),
                        instant(rs, "target_end"), instant(rs, "next_end"), rs.getString("state"), instant(rs, "next_due_at"),
                        rs.getLong("cycles"), rs.getLong("skipped"), rs.getLong("handed_over"))).stream().findFirst();
    }

    public void startRecentPass(String account, DataTypePolicy.Policy policy, UUID passId, Instant now, Instant start, Instant end) {
        namedJdbc.update("""
                INSERT INTO google_health.recent_pass(account_id,data_type,representation,calendar_policy,key_policy,
                   id,started_at,target_start,target_end,next_end,state)
                VALUES(:account,:type,:representation,:calendar,:key,:id,:now,:start,:end,:end,'ACTIVE')
                ON CONFLICT(account_id,data_type,representation,calendar_policy,key_policy) DO UPDATE SET
                   id=EXCLUDED.id,started_at=EXCLUDED.started_at,target_start=EXCLUDED.target_start,target_end=EXCLUDED.target_end,
                   next_end=EXCLUDED.next_end,state='ACTIVE',completed_at=NULL,next_due_at=NULL,cycles=0,
                   fetched=0,changed=0,deleted=0,skipped=0,handed_over=0
                """, scope(account, policy).addValue("id", passId).addValue("now", timestamp(now))
                .addValue("start", timestamp(start)).addValue("end", timestamp(end)));
    }

    public void advanceRecentPass(String account, DataTypePolicy.Policy policy, UUID id, Instant next,
                                  long fetched, long changed, long deleted, boolean skipped, boolean handed) {
        int count = namedJdbc.update("UPDATE google_health.recent_pass SET next_end=:next,fetched=fetched+:fetched,"
                                     + "changed=changed+:changed,deleted=deleted+:deleted,skipped=skipped+:skipped,handed_over=handed_over+:handed"
                                     + " WHERE " + SYNC_SCOPE + " AND id=:id AND state='ACTIVE'",
                scope(account, policy).addValue("id", id).addValue("next", timestamp(next)).addValue("fetched", fetched)
                        .addValue("changed", changed).addValue("deleted", deleted).addValue("skipped", skipped ? 1 : 0).addValue("handed", handed ? 1 : 0));
        if (count != 1) throw new CancellationException("Recent pass was revoked");
    }

    public void finishRecentPass(String account, DataTypePolicy.Policy policy, UUID id, Instant now, Instant due) {
        namedJdbc.update("UPDATE google_health.recent_pass SET state=CASE WHEN skipped=0 THEN 'COMPLETED' ELSE 'COMPLETED_WITH_ERRORS' END,"
                         + "completed_at=:now,next_due_at=NULL WHERE " + SYNC_SCOPE + " AND id=:id AND state='ACTIVE'",
                scope(account, policy).addValue("id", id).addValue("now", timestamp(now)).addValue("due", timestamp(due)));
    }

    public long countContentChanges(DataTypePolicy.Policy policy, Map<String, String> fingerprints) {
        if (fingerprints.isEmpty()) return 0;
        String table = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "heart_rate_sample" : "data_point";
        String streamScope = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "" : POINT_IDENTITY_QUERY_SCOPE;
        return jdbc.queryForObject("SELECT count(*) FROM unnest(?::char(64)[],?::char(64)[]) incoming(key,fp) LEFT JOIN google_health." + table
                                   + " stored ON stored.connection_id=1 AND stored.identity_key=incoming.key" + streamScope
                                   + " WHERE stored.fingerprint IS DISTINCT FROM incoming.fp", Long.class,
                identityQueryParameters(policy, fingerprints, null));
    }

    private Object[] identityQueryParameters(DataTypePolicy.Policy policy, Map<String, String> fingerprints, UUID traversal) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(new SqlArrayValue("text", fingerprints.keySet().toArray(Object[]::new)));
        parameters.add(new SqlArrayValue("text", fingerprints.values().toArray(Object[]::new)));
        if (policy.mode() != DataTypePolicy.Mode.HEART_RATE_SAMPLE) {
            parameters.add(policy.dataType());
            parameters.add(StoredRepresentationMapper.from(policy.mode()).name());
        }
        if (traversal != null) parameters.add(traversal);
        return parameters.toArray();
    }

    public void addRecentPassCounts(String account, DataTypePolicy.Policy policy, long fetched, long changed, long deleted) {
        namedJdbc.update("UPDATE google_health.recent_pass SET fetched=fetched+:fetched,changed=changed+:changed,deleted=deleted+:deleted WHERE " + SYNC_SCOPE + " AND state='ACTIVE'",
                scope(account, policy).addValue("fetched", fetched).addValue("changed", changed).addValue("deleted", deleted));
    }

    public void sealRecentEpoch(String account, UUID id, Instant now, Instant due) {
        jdbc.update("UPDATE google_health.recent_pass SET completed_at=?,next_due_at=? WHERE account_id=? AND id=? AND next_due_at IS NULL", timestamp(now), timestamp(due), account, id);
    }

    public void recentCycle(String account, DataTypePolicy.Policy policy) {
        namedJdbc.update("UPDATE google_health.recent_pass SET cycles=cycles+1 WHERE " + SYNC_SCOPE + " AND state='ACTIVE'", scope(account, policy));
    }

    public void fastStatus(String account, DataTypePolicy.Policy policy, Instant start, Instant end, boolean completed, boolean limited) {
        namedJdbc.update("UPDATE google_health.recent_pass SET fast_start=:start,fast_end=:end,budget_limited=:limited,"
                         + "fast_completed_at=CASE WHEN :complete THEN now() ELSE fast_completed_at END WHERE " + SYNC_SCOPE,
                scope(account, policy).addValue("start", timestamp(start)).addValue("end", timestamp(end))
                        .addValue("limited", limited).addValue("complete", completed));
    }

    public void handOverFailures(String account, Instant cutoff) {
        jdbc.update("UPDATE google_health.recent_failure SET state='HANDED_OVER' WHERE account_id=? AND cell_end<=? AND state IN ('WAITING','SKIPPED')", account, timestamp(cutoff));
    }

    public String recentFailureState(String account, DataTypePolicy.Policy policy, Instant start, Instant end, Instant now) {
        var states = namedJdbc.queryForList("SELECT state FROM google_health.recent_failure WHERE " + SYNC_SCOPE
                                            + " AND attempt_start<:end AND attempt_end>:start AND (state='SKIPPED' OR (state='WAITING' AND next_retry_at>:now))",
                scope(account, policy).addValue("start", timestamp(start)).addValue("end", timestamp(end)).addValue("now", timestamp(now)), String.class);
        return states.contains("SKIPPED") ? "SKIPPED" : states.isEmpty() ? null : "WAITING";
    }

    public boolean recentFailureSlowRetry(String account, DataTypePolicy.Policy policy, Instant start, Instant end, Instant now) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM google_health.recent_failure WHERE " + SYNC_SCOPE
                + " AND attempt_start<:end AND attempt_end>:start AND state='WAITING' AND next_retry_at>:now AND attempts>=:limit)",
                scope(account, policy).addValue("start", timestamp(start)).addValue("end", timestamp(end))
                        .addValue("now", timestamp(now)).addValue("limit", workProperties.getWorkFailureLimit()), Boolean.class));
    }

    public void failRecent(String account, DataTypePolicy.Policy policy, String purpose, Instant cellStart, Instant cellEnd,
                           Instant start, Instant end, int limit, Instant retry, String error) {
        failRecent(account, policy, purpose, cellStart, cellEnd, start, end, limit, retry, error, false);
    }

    public void recordRecentFailure(String account, DataTypePolicy.Policy policy, String purpose, Instant cellStart, Instant cellEnd,
                                    Instant start, Instant end, boolean recoverable, Instant now, String error) {
        var params = scope(account, policy).addValue("purpose", purpose).addValue("cellStart", timestamp(cellStart)).addValue("cellEnd", timestamp(cellEnd));
        Integer attempts = namedJdbc.queryForObject("SELECT COALESCE(MAX(attempts),0) FROM google_health.recent_failure WHERE "
                + SYNC_SCOPE + " AND purpose=:purpose AND cell_start=:cellStart AND cell_end=:cellEnd", params, Integer.class);
        var decision = workRetryPolicy.afterFailure(attempts + 1, recoverable, now);
        failRecent(account, policy, purpose, cellStart, cellEnd, start, end, workProperties.getWorkFailureLimit(), decision.retryAt(), error, recoverable);
        log.info("Google Health recent failure saved: stream={}, attempts={}, automaticRetry={}, nextRetryAt={}",
                policy.dataType(), attempts + 1, decision.automaticRetry(), decision.retryAt());
    }

    private void failRecent(String account, DataTypePolicy.Policy policy, String purpose, Instant cellStart, Instant cellEnd,
                            Instant start, Instant end, int limit, Instant retry, String error, boolean recoverable) {
        namedJdbc.update("""
                INSERT INTO google_health.recent_failure(account_id,data_type,representation,calendar_policy,key_policy,purpose,
                  cell_start,cell_end,attempt_start,attempt_end,attempts,next_retry_at,state,safe_error)
                VALUES(:account,:type,:representation,:calendar,:key,:purpose,:cellStart,:cellEnd,:start,:end,1,
                  CASE WHEN NOT :recoverable AND :limit=1 THEN NULL ELSE CAST(:retry AS timestamptz) END,
                  CASE WHEN NOT :recoverable AND :limit=1 THEN 'SKIPPED' ELSE 'WAITING' END,:error)
                ON CONFLICT(account_id,data_type,representation,calendar_policy,key_policy,purpose,cell_start,cell_end)
                DO UPDATE SET attempts=google_health.recent_failure.attempts+1,
                  attempt_start=EXCLUDED.attempt_start,attempt_end=EXCLUDED.attempt_end,
                  next_retry_at=CASE WHEN NOT :recoverable AND google_health.recent_failure.attempts+1>=:limit THEN NULL ELSE EXCLUDED.next_retry_at END,
                  state=CASE WHEN NOT :recoverable AND google_health.recent_failure.attempts+1>=:limit THEN 'SKIPPED' ELSE 'WAITING' END,safe_error=EXCLUDED.safe_error
                """, scope(account, policy).addValue("purpose", purpose).addValue("cellStart", timestamp(cellStart)).addValue("cellEnd", timestamp(cellEnd))
                .addValue("start", timestamp(start)).addValue("end", timestamp(end)).addValue("limit", limit).addValue("retry", timestamp(retry)).addValue("error", error).addValue("recoverable", recoverable));
    }

    public void deferRecentReload(HistoryWork work, String account, DataTypePolicy.Policy policy, Instant start, Instant end) {
        var failures = namedJdbc.queryForList("SELECT state,attempts,next_retry_at,safe_error FROM google_health.recent_failure WHERE "
                + SYNC_SCOPE + " AND attempt_start<:end AND attempt_end>:start AND state IN ('WAITING','SKIPPED')"
                + " ORDER BY CASE WHEN state='SKIPPED' THEN 0 ELSE 1 END,next_retry_at DESC LIMIT 1",
                scope(account, policy).addValue("start", timestamp(start)).addValue("end", timestamp(end)));
        if (failures.isEmpty()) throw new IllegalStateException("Incomplete reload has no saved recent failure");
        var failure = failures.getFirst();
        boolean parked = failure.get("state").equals("SKIPPED");
        if (jdbc.update("UPDATE google_health.history_work SET state=?,attempts=GREATEST(attempts,?),next_retry_at=?,safe_error=?,"
                        + "generation=generation+1,updated_at=now() WHERE id=? AND generation=? AND state='ACTIVE'",
                parked ? "PARKED" : "WAITING", failure.get("attempts"), parked ? null : failure.get("next_retry_at"),
                failure.get("safe_error"), work.id(), work.generation()) != 1) throw new CancellationException("Work was revoked");
        log.info("Google Health reload deferred: stream={}, automaticRetry={}, nextRetryAt={}",
                policy.dataType(), !parked, parked ? null : failure.get("next_retry_at"));
    }

    public void clearCoveredFailures(String account, DataTypePolicy.Policy policy, Instant start, Instant end) {
        namedJdbc.update("DELETE FROM google_health.recent_failure WHERE " + SYNC_SCOPE
                         + " AND cell_start>=:start AND cell_end<=:end", scope(account, policy).addValue("start", timestamp(start)).addValue("end", timestamp(end)));
    }

    public record HistoryWork(UUID id, LocalDate day, String lane, long generation, String state, int attempts, Instant retryAt,
                              boolean replaceMissing) {
        public HistoryWork(UUID id, LocalDate day, String lane, long generation, String state, int attempts, Instant retryAt) {
            this(id, day, lane, generation, state, attempts, retryAt, false);
        }
    }

    public record HistoryChild(UUID id, UUID traversal, Instant start, Instant end, String state, String token, int pages, long fetched) {
    }

    public Optional<HistoryWork> dueWork(String account, DataTypePolicy.Policy policy, String lane, Instant now) {
        return namedJdbc.query("SELECT * FROM google_health.history_work WHERE " + SYNC_SCOPE
                               + " AND lane=:lane AND state IN ('ACTIVE','WAITING') AND (next_retry_at IS NULL OR next_retry_at<=:now) ORDER BY native_day DESC LIMIT 1",
                scope(account, policy).addValue("lane", lane).addValue("now", timestamp(now)), (rs, n) -> mapWork(rs)).stream().findFirst();
    }

    private static HistoryWork mapWork(ResultSet rs) throws SQLException {
        return new HistoryWork(UUID.fromString(rs.getString("id")), rs.getObject("native_day", LocalDate.class), rs.getString("lane"),
                rs.getLong("generation"), rs.getString("state"), rs.getInt("attempts"), instant(rs, "next_retry_at"), rs.getBoolean("replace_missing"));
    }

    /** Return the newest gap explicitly: the scheduler may use it to bound later searches. */
    public Optional<LocalDate> newestUnmarked(String account, DataTypePolicy.Policy policy, LocalDate floor, LocalDate before) {
        return namedJdbc.query("""
                                       SELECT day::date AS day FROM generate_series(CAST(:before AS date)-1,CAST(:floor AS date),interval '-1 day') day
                                        WHERE NOT EXISTS(SELECT 1 FROM google_health.history_audit WHERE
                                       """ + SYNC_SCOPE + " AND native_day=day::date AND outcome IN ('DATA','EMPTY'))"
                               + " AND NOT EXISTS(SELECT 1 FROM google_health.history_work WHERE " + SYNC_SCOPE
                               + " AND lane='HISTORY' AND day::date>=(start_bound AT TIME ZONE 'UTC')::date AND day::date<(end_bound AT TIME ZONE 'UTC')::date AND state IN ('ACTIVE','WAITING','PARKED')) ORDER BY day DESC LIMIT 1",
                scope(account, policy).addValue("floor", floor).addValue("before", before), (rs, n) -> rs.getObject("day", LocalDate.class)).stream().findFirst();
    }

    public HistoryWork createWork(String account, DataTypePolicy.Policy policy, LocalDate day, String lane,
                                  List<SyncWindowPolicy.Bounds> cells) {
        return createWork(account, policy, day, lane, cells,
                lane.equals("HISTORY") && historicalReloadRequested(account, policy, day), "ACTIVE");
    }

    private HistoryWork createWork(String account, DataTypePolicy.Policy policy, LocalDate day, String lane,
                                   List<SyncWindowPolicy.Bounds> cells, boolean replaceMissing, String state) {
        UUID id = UUID.randomUUID();
        var params = scope(account, policy).addValue("id", id).addValue("day", day).addValue("lane", lane)
                .addValue("state", state).addValue("replace", replaceMissing)
                .addValue("start", timestamp(cells.stream().map(c -> c.start()).min(Instant::compareTo).orElseThrow()))
                .addValue("end", timestamp(cells.stream().map(c -> c.end()).max(Instant::compareTo).orElseThrow()));
        namedJdbc.update("DELETE FROM google_health.history_work WHERE " + SYNC_SCOPE
                         + " AND native_day=:day AND lane=:lane AND state IN ('COMPLETED','REVOKED')", params);
        namedJdbc.update("INSERT INTO google_health.history_work(id,account_id,data_type,representation,calendar_policy,key_policy,native_day,lane,start_bound,end_bound,state,replace_missing)"
                         + " VALUES(:id,:account,:type,:representation,:calendar,:key,:day,:lane,:start,:end,:state,:replace)", params);
        for (var cell : cells) insertChild(id, cell.start(), cell.end());
        return new HistoryWork(id, day, lane, 0, state, 0, null, replaceMissing);
    }

    private void insertChild(UUID parent, Instant start, Instant end) {
        jdbc.update("INSERT INTO google_health.history_child(id,parent_id,traversal_id,start_bound,end_bound,state,query_fingerprints,validated) VALUES(?,?,?,?,?,'ACTIVE','{}'::jsonb,0)",
                UUID.randomUUID(), parent, UUID.randomUUID(), timestamp(start), timestamp(end));
    }

    public void splitChild(HistoryWork parent, HistoryChild child, Instant split) {
        guardWork(parent, child);
        jdbc.update("DELETE FROM google_health.history_child WHERE id=?", child.id());
        insertChild(parent.id(), child.start(), split);
        insertChild(parent.id(), split, child.end());
    }

    public List<HistoryChild> children(UUID parent) {
        return jdbc.query("SELECT id,traversal_id,start_bound,end_bound,state,page_token,page_count,fetched FROM google_health.history_child WHERE parent_id=? ORDER BY start_bound DESC", (rs, n) ->
                new HistoryChild(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("traversal_id")), instant(rs, "start_bound"), instant(rs, "end_bound"),
                        rs.getString("state"), rs.getString("page_token"), rs.getInt("page_count"), rs.getLong("fetched")), parent);
    }

    public void revokeWork(HistoryWork work) {
        jdbc.update("UPDATE google_health.history_work SET state='REVOKED',generation=generation+1 WHERE id=?", work.id());
        jdbc.update("UPDATE google_health.history_child SET query_fingerprints=NULL WHERE parent_id=?", work.id());
    }

    public void activateWork(HistoryWork work) {
        if (jdbc.update("UPDATE google_health.history_work SET state='ACTIVE',next_retry_at=NULL WHERE id=? AND generation=? AND state IN ('ACTIVE','WAITING')",
                work.id(), work.generation()) != 1) throw new CancellationException("Work was revoked");
    }

    public void guardWork(HistoryWork parent, HistoryChild child) {
        var owner = jdbc.queryForList("SELECT id FROM google_health.history_work WHERE id=? AND generation=? AND state='ACTIVE' FOR UPDATE", parent.id(), parent.generation());
        if (owner.isEmpty()) throw new CancellationException("Historical owner was revoked");
        if (child != null && jdbc.queryForList("SELECT id FROM google_health.history_child WHERE id=? AND parent_id=? AND traversal_id=? AND state='ACTIVE' FOR UPDATE",
                child.id(), parent.id(), child.traversal()).isEmpty()) throw new CancellationException("Traversal was revoked");
    }

    public void guardUnmarkedHistory(HistoryWork work, String account, DataTypePolicy.Policy policy) {
        Boolean completed = namedJdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM google_health.history_audit,"
                + " (SELECT start_bound,end_bound FROM google_health.history_work WHERE id=:id) bounds WHERE "
                + SYNC_SCOPE + " AND outcome IN ('DATA','EMPTY')"
                + " AND native_day>=(bounds.start_bound AT TIME ZONE 'UTC')::date"
                + " AND native_day<(bounds.end_bound AT TIME ZONE 'UTC')::date)",
                scope(account, policy).addValue("id", work.id()), Boolean.class);
        if (Boolean.TRUE.equals(completed)) throw new CancellationException("Historical day is already complete");
    }

    public Map<String, String> queryFingerprints(HistoryChild child) {
        String json = jdbc.queryForObject("SELECT query_fingerprints::text FROM google_health.history_child WHERE id=? AND traversal_id=?",
                String.class, child.id(), child.traversal());
        if (json == null) return null;
        Map<String, String> result = new HashMap<>();
        objectMapper.readTree(json).properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        return result;
    }

    public void restartHistoricalChild(HistoryWork parent, HistoryChild child) {
        guardWork(parent, child);
        jdbc.update("DELETE FROM google_health.history_child WHERE id=?", child.id());
        insertChild(parent.id(), child.start(), child.end());
    }

    public void checkpointHistoricalChild(HistoryChild child, String next, long fetched, long changed, long validated, int pages,
                                          Map<String, String> fingerprints) {
        checkpointChild(child, next, fetched, changed, 0, pages);
        if (jdbc.update("UPDATE google_health.history_child SET query_fingerprints=?::jsonb,validated=COALESCE(validated,0)+? WHERE id=? AND traversal_id=?",
                next == null ? null : objectMapper.writeValueAsString(fingerprints), validated, child.id(), child.traversal()) != 1)
            throw new SyncValidationException(SyncValidationException.Reason.PAGE_CHECKPOINT_REJECTED);
    }

    public void checkpointChild(HistoryChild child, String next, long fetched, long changed, int pages) {
        checkpointChild(child, next, fetched, changed, fetched, pages);
    }

    public void checkpointChild(HistoryChild child, String next, long fetched, long changed, long stamped, int pages) {
        String tokenHash = next == null ? null : StableFingerprint.of(next);
        int count = jdbc.update("UPDATE google_health.history_child SET page_token=?,page_count=?,fetched=fetched+?,changed=changed+?,stamped=stamped+?,"
                                + "state=CASE WHEN CAST(? AS text) IS NULL THEN 'COMPLETED' ELSE 'ACTIVE' END,token_hashes=CASE WHEN CAST(? AS text) IS NULL THEN token_hashes ELSE array_append(token_hashes,?) END,updated_at=now()"
                                + " WHERE id=? AND traversal_id=? AND state='ACTIVE' AND (CAST(? AS text) IS NULL OR NOT (?=ANY(token_hashes)))",
                next, pages, fetched, changed, stamped, next, tokenHash, tokenHash, child.id(), child.traversal(), tokenHash, tokenHash);
        if (count != 1) throw new SyncValidationException(SyncValidationException.Reason.PAGE_CHECKPOINT_REJECTED);
    }

    public void failWork(HistoryWork work, int limit, Instant retry, String error) {
        failWork(work, work.attempts() + 1 < limit, retry, error);
    }

    public void recordWorkFailure(HistoryWork work, boolean recoverable, Instant now, String error) {
        var decision = workRetryPolicy.afterFailure(work.attempts() + 1, recoverable, now);
        failWork(work, decision.automaticRetry(), decision.retryAt(), error);
        log.info("Google Health historical failure saved: lane={}, attempts={}, automaticRetry={}, nextRetryAt={}",
                work.lane(), work.attempts() + 1, decision.automaticRetry(), decision.retryAt());
    }

    private void failWork(HistoryWork work, boolean automaticRetry, Instant retry, String error) {
        if (jdbc.update("UPDATE google_health.history_work SET attempts=attempts+1,state=?,"
                        + "next_retry_at=?,safe_error=?,generation=generation+1,updated_at=now() WHERE id=? AND generation=? AND state='ACTIVE'",
                automaticRetry ? "WAITING" : "PARKED", automaticRetry ? timestamp(retry) : null, error, work.id(), work.generation()) != 1) throw new CancellationException("Work was revoked");
        jdbc.update("""
                INSERT INTO google_health.history_audit(account_id,data_type,representation,calendar_policy,key_policy,native_day,
                    start_bound,end_bound,outcome,work_id,traversal_ids,fetched,changed,stamped,deleted,started_at)
                SELECT w.account_id,w.data_type,w.representation,w.calendar_policy,w.key_policy,w.native_day,w.start_bound,w.end_bound,
                    'FAILED',w.id,array_agg(c.traversal_id),COALESCE(SUM(c.fetched),0),COALESCE(SUM(c.changed),0),COALESCE(SUM(c.stamped),0),0,w.started_at
                  FROM google_health.history_work w JOIN google_health.history_child c ON c.parent_id=w.id
                 WHERE w.id=? AND w.lane='HISTORY' GROUP BY w.id
                """, work.id());
    }

    public void completeWork(HistoryWork work, boolean historical, long deleted) {
        completeWork(work, historical, deleted, Map.of(work.day(), deleted));
    }

    public void completeWork(HistoryWork work, boolean historical, long deleted, Map<LocalDate, Long> deletedDays) {
        completeWork(work, historical, deleted, deletedDays, false);
    }

    public void completeHistoricalImport(HistoryWork work) {
        if (work.replaceMissing()) throw new IllegalArgumentException("Replacement work needs maintenance finalization");
        completeWork(work, true, 0, Map.of(), true);
    }

    private void completeWork(HistoryWork work, boolean historical, long deleted, Map<LocalDate, Long> deletedDays, boolean importOnce) {
        Map<String, Long> dayCounts = new HashMap<>();
        deletedDays.forEach((day, count) -> dayCounts.put(day.toString(), count));
        String dailyDeleted = objectMapper.writeValueAsString(dayCounts);
        guardWork(work, null);
        List<HistoryChild> children = children(work.id());
        if (children.isEmpty() || children.stream().anyMatch(c -> !c.state().equals("COMPLETED")))
            throw new IllegalStateException("Incomplete historical children");
        if (historical) jdbc.update("""
                INSERT INTO google_health.history_audit(account_id,data_type,representation,calendar_policy,key_policy,native_day,
                    start_bound,end_bound,outcome,work_id,traversal_ids,fetched,changed,stamped,deleted,started_at)
                SELECT w.account_id,w.data_type,w.representation,w.calendar_policy,w.key_policy,d.day::date,
                    d.day AT TIME ZONE 'UTC',(d.day+interval '1 day') AT TIME ZONE 'UTC',
                    CASE WHEN (CASE WHEN ? THEN stats.validated ELSE stats.stamped END)=0 THEN 'EMPTY' ELSE 'DATA' END,w.id,stats.traversals,
                    stats.fetched,stats.changed,CASE WHEN ? THEN 0 ELSE stats.stamped END,
                    COALESCE(jsonb_extract_path_text(?::jsonb,d.day::date::text)::bigint,0),w.started_at
                  FROM google_health.history_work w
                  CROSS JOIN LATERAL generate_series((w.start_bound AT TIME ZONE 'UTC')::date::timestamp,
                     (w.end_bound AT TIME ZONE 'UTC')::date::timestamp-interval '1 day',interval '1 day') d(day)
                  CROSS JOIN LATERAL (SELECT array_agg(c.traversal_id) traversals,SUM(c.fetched) fetched,
                    SUM(c.changed) changed,SUM(c.stamped) stamped,SUM(COALESCE(c.validated,c.stamped)) validated
                    FROM google_health.history_child c WHERE c.parent_id=w.id) stats
                 WHERE w.id=?
                """, importOnce, importOnce, dailyDeleted, work.id());
        jdbc.update("UPDATE google_health.history_work SET state='COMPLETED',safe_error=NULL,next_retry_at=NULL,updated_at=now() WHERE id=?", work.id());
    }

    public void checkQueryConflicts(DataTypePolicy.Policy policy, Map<String, String> fingerprints, UUID traversal) {
        if (traversal == null || fingerprints.isEmpty()) return;
        String table = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "heart_rate_sample" : "data_point";
        String streamScope = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "" : POINT_IDENTITY_QUERY_SCOPE;
        // Healthy pages usually have no conflict. Aggregate the page instead of favoring an early EXISTS hit.
        Boolean conflict = jdbc.queryForObject("SELECT count(*)>0 FROM unnest(?::char(64)[],?::char(64)[]) incoming(key,fp)"
                                               + " JOIN google_health." + table + " stored ON stored.connection_id=1 AND stored.identity_key=incoming.key"
                                               + streamScope
                                               + " WHERE stored.last_seen_run=? AND stored.fingerprint IS DISTINCT FROM incoming.fp", Boolean.class,
                identityQueryParameters(policy, fingerprints, traversal));
        if (Boolean.TRUE.equals(conflict)) throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_TRAVERSAL_IDENTITY);
    }

    public void checkQueryConflict(DataTypePolicy.Policy policy, String key, String fingerprint, UUID traversal) {
        if (traversal == null) return;
        String table = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "heart_rate_sample" : "data_point";
        if (!jdbc.queryForList("SELECT id FROM google_health." + table + " WHERE connection_id=1 AND identity_key=? AND last_seen_run=? AND fingerprint<>?",
                key, traversal, fingerprint).isEmpty()) throw new SyncValidationException(SyncValidationException.Reason.DUPLICATE_TRAVERSAL_IDENTITY);
    }

    private String nativePredicate(DataTypePolicy.Policy policy) {
        return switch (policy.windowTimeDomain()) {
            case DATE -> "civil_date>=:startDate AND civil_date<:endDate";
            case CIVIL -> "civil_start_time>=:civilStart AND civil_start_time<:civilEnd";
            case PHYSICAL -> (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "sample_time" :
                    policy.recordType() == DataTypePolicy.RecordType.SESSION_END ? "physical_end_time" : "physical_start_time") + ">=:start AND "
                             + (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "sample_time" :
                    policy.recordType() == DataTypePolicy.RecordType.SESSION_END ? "physical_end_time" : "physical_start_time") + "<:end";
        };
    }

    public int replaceRecent(DataTypePolicy.Policy policy, Instant start, Instant end, Set<String> keys) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("start", timestamp(start)).addValue("end", timestamp(end))
                .addValue("civilStart", civilTime(start)).addValue("civilEnd", civilTime(end))
                .addValue("startDate", start.atOffset(ZoneOffset.UTC).toLocalDate()).addValue("endDate", end.atOffset(ZoneOffset.UTC).toLocalDate())
                .addValue("type", policy.dataType()).addValue("representation", policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? policy.representation() : StoredRepresentationMapperName(policy))
                .addValue("keys", new SqlArrayValue("text", keys.toArray(Object[]::new)));
        String predicate = "connection_id=1 AND " + nativePredicate(policy) + " AND identity_key<>ALL(CAST(:keys AS text[]))";
        if (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE)
            return namedJdbc.update("DELETE FROM google_health.heart_rate_sample WHERE " + predicate, params);
        predicate += " AND data_type=:type AND representation=:representation";
        if (policy.dataType().equals("exercise"))
            namedJdbc.update("UPDATE google_health.route_ownership SET present=FALSE,updated_at=now() WHERE identity_key IN (SELECT identity_key FROM google_health.data_point WHERE " + predicate + ")", params);
        return namedJdbc.update("DELETE FROM google_health.data_point WHERE " + predicate, params);
    }

    private static String StoredRepresentationMapperName(DataTypePolicy.Policy policy) {
        return StoredRepresentationMapper.from(policy.mode()).name();
    }

    public Map<LocalDate, Long> missingCountsByDay(DataTypePolicy.Policy policy, HistoryChild child) {
        String table = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "heart_rate_sample" : "data_point";
        String nativeTime = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "sample_time" : switch (policy.windowTimeDomain()) {
            case DATE -> "civil_date";
            case CIVIL -> "civil_start_time";
            case PHYSICAL -> policy.recordType() == DataTypePolicy.RecordType.SESSION_END ? "physical_end_time" : "physical_start_time";
        };
        String day = policy.windowTimeDomain() == DataTypePolicy.WindowTimeDomain.PHYSICAL ? "(" + nativeTime + " AT TIME ZONE 'UTC')::date" : nativeTime + "::date";
        var params = new MapSqlParameterSource().addValue("start", timestamp(child.start())).addValue("end", timestamp(child.end()))
                .addValue("civilStart", civilTime(child.start())).addValue("civilEnd", civilTime(child.end()))
                .addValue("startDate", child.start().atOffset(ZoneOffset.UTC).toLocalDate()).addValue("endDate", child.end().atOffset(ZoneOffset.UTC).toLocalDate())
                .addValue("stamp", child.traversal()).addValue("type", policy.dataType()).addValue("representation", policy.representation());
        Map<LocalDate, Long> result = new HashMap<>();
        namedJdbc.query("SELECT " + day + " AS day,count(*) AS count FROM google_health." + table + " WHERE connection_id=1 AND " + nativePredicate(policy)
                        + (table.equals("data_point") ? " AND data_type=:type AND representation=:representation" : "") + " AND last_seen_run IS DISTINCT FROM :stamp GROUP BY " + day, params,
                rs -> {
                    result.put(rs.getObject("day", LocalDate.class), rs.getLong("count"));
                });
        return result;
    }

    public void retireMissingHistoricalExercises(Instant start, Instant end, UUID traversal) {
        jdbc.update("UPDATE google_health.route_ownership SET present=FALSE,updated_at=now() WHERE identity_key IN "
                    + "(SELECT identity_key FROM google_health.data_point WHERE connection_id=1 AND data_type='exercise' AND representation='RAW' "
                    + "AND civil_start_time>=? AND civil_start_time<? AND last_seen_run IS DISTINCT FROM ?)", civilTime(start), civilTime(end), traversal);
    }

    @Transactional
    public void reloadDays(String account, List<DataTypePolicy.Policy> policies, LocalDate start, LocalDate end, LocalDate cutoff, boolean failedOnly) {
        String current = jdbc.queryForObject("SELECT google_user_id FROM google_health.connection WHERE id=1 AND state='CONNECTED' FOR UPDATE", String.class);
        if (!account.equals(current)) throw new GoogleHealthAccountMismatchException();
        jdbc.update("UPDATE google_health.sync_control SET generation=generation+1 WHERE account_id=?", account);
        for (var policy : policies) {
            var params = scope(account, policy).addValue("start", start).addValue("end", end);
            if (!failedOnly) namedJdbc.update("UPDATE google_health.history_audit SET outcome='SUPERSEDED_BY_RELOAD' WHERE " + SYNC_SCOPE
                                              + " AND native_day>=:start AND native_day<:end AND outcome IN ('DATA','EMPTY')", params);
            if (!failedOnly) {
                namedJdbc.update("""
                        INSERT INTO google_health.history_audit(account_id,data_type,representation,calendar_policy,key_policy,
                            native_day,start_bound,end_bound,reason,outcome,work_id,traversal_ids,fetched,changed,stamped,deleted,started_at)
                        VALUES(:account,:type,:representation,:calendar,:key,:start,:startBound,:endBound,
                            'USER_RELOAD','RELOAD_REQUESTED',:id,ARRAY[]::uuid[],0,0,0,0,now())
                        """, scope(account, policy).addValue("start", start).addValue("id", UUID.randomUUID())
                        .addValue("startBound", timestamp(SyncWindowPolicy.midnight(start)))
                        .addValue("endBound", timestamp(SyncWindowPolicy.midnight(end))));
            }
            var overlaps = namedJdbc.queryForList("SELECT * FROM google_health.history_work WHERE " + SYNC_SCOPE
                                                  + " AND start_bound<:endBound AND end_bound>:startBound" + (failedOnly ? " AND state IN ('PARKED','WAITING')" : ""),
                    params.addValue("startBound", timestamp(SyncWindowPolicy.midnight(start)))
                            .addValue("endBound", timestamp(SyncWindowPolicy.midnight(end))));
            for (var previous : overlaps) {
                UUID oldId = (UUID) previous.get("id");
                LocalDate previousStart = ((Timestamp) previous.get("start_bound")).toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
                LocalDate previousEnd = ((Timestamp) previous.get("end_bound")).toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
                jdbc.update("DELETE FROM google_health.history_work WHERE id=?", oldId);
                if (previousStart.isBefore(start)) restoreOutsideReload(account, policy, previousStart, start, previous);
                if (previousEnd.isAfter(end)) restoreOutsideReload(account, policy, end, previousEnd, previous);
            }
            namedJdbc.update("DELETE FROM google_health.recent_failure WHERE " + SYNC_SCOPE
                             + " AND cell_start<:endBound AND cell_end>:startBound", params.addValue("startBound", timestamp(SyncWindowPolicy.midnight(start)))
                    .addValue("endBound", timestamp(SyncWindowPolicy.midnight(end))));
            if (policy.dataType().equals("exercise"))
                jdbc.update("UPDATE google_health.route_ownership SET route_attempts=0,route_next_retry_at=NULL,route_state='PENDING'"
                            + " WHERE account_id=? AND native_day>=? AND native_day<? AND route_state='FAILED'", account, start, end);
            if (!failedOnly) for (LocalDate day = start.isBefore(cutoff) ? cutoff : start; day.isBefore(end); day = day.plusDays(1)) {
                var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(),
                        SyncWindowPolicy.midnight(day), SyncWindowPolicy.midnight(day.plusDays(1)));
                createWork(account, policy, day, "RELOAD", SyncWindowPolicy.cells(policy, bounds));
            }
        }
    }

    private void restoreOutsideReload(String account, DataTypePolicy.Policy policy, LocalDate start, LocalDate end, Map<String, Object> previous) {
        var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(),
                SyncWindowPolicy.midnight(start),
                SyncWindowPolicy.midnight(end));
        String state = previous.get("state").equals("ACTIVE") ? "WAITING" : (String) previous.get("state");
        var replacement = createWork(account, policy, end.minusDays(1), (String) previous.get("lane"), List.of(bounds),
                Boolean.TRUE.equals(previous.get("replace_missing")), state);
        jdbc.update("UPDATE google_health.history_work SET state=?,attempts=?,next_retry_at=?,safe_error=? WHERE id=?",
                state, previous.get("attempts"), previous.get("next_retry_at"), previous.get("safe_error"), replacement.id());
    }

    public LocalDate emptyBatchStart(String account, DataTypePolicy.Policy policy, LocalDate newest, LocalDate floor) {
        if (historicalReloadRequested(account, policy, newest)) return newest;
        var params = scope(account, policy).addValue("newest", newest);
        var last = namedJdbc.queryForList("SELECT outcome FROM google_health.history_audit WHERE " + SYNC_SCOPE
                                          + " AND outcome IN ('DATA','EMPTY') ORDER BY completed_at DESC,id DESC LIMIT 1", params, String.class);
        if (last.isEmpty() || !last.getFirst().equals("EMPTY")) return newest;
        LocalDate lower = newest.minusDays(Math.min(MAX_EMPTY_BATCH_DAYS, policy.maxWindowDays()) - 1L);
        if (lower.isBefore(floor)) lower = floor;
        params.addValue("lower", lower);
        var barrier = namedJdbc.query("SELECT max(d::date) AS day FROM generate_series(CAST(:lower AS date),CAST(:newest AS date),interval '1 day') d WHERE"
                                      + " EXISTS(SELECT 1 FROM google_health.history_audit WHERE " + SYNC_SCOPE + " AND native_day=d::date AND outcome IN ('DATA','EMPTY')) OR"
                                      + " EXISTS(SELECT 1 FROM google_health.history_audit WHERE " + SYNC_SCOPE
                                      + " AND outcome='RELOAD_REQUESTED' AND native_day<=d::date AND end_bound>(d::date::timestamp AT TIME ZONE 'UTC')) OR"
                                      + " EXISTS(SELECT 1 FROM google_health.history_work WHERE " + SYNC_SCOPE + " AND lane='HISTORY' AND state IN ('ACTIVE','WAITING','PARKED')"
                                      + " AND d::date>=(start_bound AT TIME ZONE 'UTC')::date AND d::date<(end_bound AT TIME ZONE 'UTC')::date)", params,
                (rs, n) -> rs.getObject("day", LocalDate.class)).getFirst();
        LocalDate start = barrier == null ? lower : barrier.plusDays(1);
        if (!start.isBefore(newest)) return newest;
        return start;
    }

    private boolean historicalReloadRequested(String account, DataTypePolicy.Policy policy, LocalDate day) {
        return Boolean.TRUE.equals(namedJdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM google_health.history_audit WHERE "
                + SYNC_SCOPE + " AND outcome='RELOAD_REQUESTED' AND native_day<=:day AND end_bound>:bound)",
                scope(account, policy).addValue("day", day).addValue("bound", timestamp(SyncWindowPolicy.midnight(day))), Boolean.class));
    }

    public HistoryWork reduceHistoryToDay(String account, DataTypePolicy.Policy policy, HistoryWork previous) {
        guardWork(previous, null);
        jdbc.update("DELETE FROM google_health.history_work WHERE id=?", previous.id());
        var bounds = new SyncWindowPolicy.Bounds(policy.windowTimeDomain(),
                SyncWindowPolicy.midnight(previous.day()),
                SyncWindowPolicy.midnight(previous.day().plusDays(1)));
        return createWork(account, policy, previous.day(), "HISTORY", SyncWindowPolicy.cells(policy, bounds));
    }

    public GHSynchronizationStatus synchronizationStatus(
            ConnectorProperties properties, Instant now) {
        var connection = findConnection().orElse(null);
        if (connection == null || connection.googleUserId() == null)
            return new GHSynchronizationStatus(now, cooldown(), List.of());
        String account = connection.googleUserId();
        var cutoff = SyncWindowPolicy.cutoff(properties, now);
        List<GHStreamSyncStatus> streams = new ArrayList<>();
        for (var policy : DataTypePolicy.allowedBy(connection.grantedScopes())) {
            var params = scope(account, policy).addValue("floor", properties.getHistoryStartDate()).addValue("cutoff", cutoff);
            long total = Math.max(0, ChronoUnit.DAYS.between(properties.getHistoryStartDate(), cutoff));
            Long finalized = namedJdbc.queryForObject("SELECT count(*) FROM google_health.history_audit WHERE " + SYNC_SCOPE
                                                      + " AND native_day>=:floor AND native_day<:cutoff AND outcome IN ('DATA','EMPTY')", params, Long.class);
            var gap = namedJdbc.query("SELECT d::date AS day FROM generate_series(CAST(:cutoff AS date)-1,CAST(:floor AS date),interval '-1 day') d WHERE NOT EXISTS("
                                      + "SELECT 1 FROM google_health.history_audit WHERE " + SYNC_SCOPE + " AND native_day=d::date AND outcome IN ('DATA','EMPTY')) LIMIT 1", params,
                    (rs, n) -> rs.getObject("day", LocalDate.class)).stream().findFirst().orElse(null);
            LocalDate backTo = total == 0 ? null : gap == null ? properties.getHistoryStartDate() : gap.plusDays(1).equals(cutoff) ? null : gap.plusDays(1);
            var failures = namedJdbc.query("""
                                                   SELECT lane,state,start_bound AS start,end_bound AS end,attempts,next_retry_at,safe_error
                                                     FROM google_health.history_work WHERE
                                                   """ + SYNC_SCOPE + " AND state IN ('WAITING','PARKED') AND end_bound>(CAST(:floor AS date)::timestamp AT TIME ZONE 'UTC') UNION ALL SELECT 'RECENT',state,attempt_start,attempt_end,attempts,next_retry_at,safe_error"
                                           + " FROM google_health.recent_failure WHERE " + SYNC_SCOPE + " AND state IN ('WAITING','SKIPPED') AND cell_end>(CAST(:floor AS date)::timestamp AT TIME ZONE 'UTC') ORDER BY start DESC LIMIT 100", params,
                    (rs, n) -> new GHFailedRange(rs.getString("lane"), rs.getString("state"), instant(rs, "start"), instant(rs, "end"), rs.getInt("attempts"), instant(rs, "next_retry_at"), rs.getString("safe_error")));
            var pass = recentPass(account, policy).orElse(null);
            var fast = namedJdbc.query("SELECT fast_start,fast_end,fast_completed_at,budget_limited FROM google_health.recent_pass WHERE " + SYNC_SCOPE, params,
                    (rs, n) -> new Object[]{instant(rs, "fast_start"), instant(rs, "fast_end"), instant(rs, "fast_completed_at"), rs.getBoolean("budget_limited")}).stream().findFirst().orElse(new Object[]{null, null, null, false});
            Long parked = namedJdbc.queryForObject("SELECT COALESCE(SUM(GREATEST(0,LEAST((end_bound AT TIME ZONE 'UTC')::date,CAST(:cutoff AS date))-GREATEST((start_bound AT TIME ZONE 'UTC')::date,CAST(:floor AS date)))),0) FROM google_health.history_work WHERE " + SYNC_SCOPE + " AND lane='HISTORY' AND state='PARKED' AND end_bound>(CAST(:floor AS date)::timestamp AT TIME ZONE 'UTC')", params, Long.class);
            var active = namedJdbc.query("SELECT start_bound,end_bound FROM google_health.history_work WHERE " + SYNC_SCOPE + " AND lane='HISTORY' AND state='ACTIVE' LIMIT 1", params,
                    (rs, n) -> new Instant[]{instant(rs, "start_bound"), instant(rs, "end_bound")}).stream().findFirst().orElse(new Instant[]{null, null});
            String timeColumn = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "sample_time" : switch (policy.windowTimeDomain()) {
                case DATE -> "civil_date";
                case CIVIL -> "civil_start_time";
                case PHYSICAL -> policy.recordType() == DataTypePolicy.RecordType.SESSION_END ? "physical_end_time" : "physical_start_time";
            };
            String timeValue = policy.windowTimeDomain() == DataTypePolicy.WindowTimeDomain.DATE ? "civil_date::timestamp AT TIME ZONE 'UTC'" :
                    policy.windowTimeDomain() == DataTypePolicy.WindowTimeDomain.CIVIL ? "civil_start_time AT TIME ZONE 'UTC'" : timeColumn;
            String table = policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE ? "heart_rate_sample" : "data_point";
            String filter = table.equals("heart_rate_sample") ? "" : " AND data_type=:type AND representation=:representation";
            var newest = namedJdbc.query("SELECT " + timeValue + " AS newest FROM google_health." + table + " WHERE connection_id=1" + filter
                                         + " AND " + timeColumn + " IS NOT NULL ORDER BY " + timeColumn + " DESC LIMIT 1", params, (rs, n) -> instant(rs, "newest")).stream().findFirst().orElse(null);
            streams.add(new GHStreamSyncStatus(policy.dataType(), policy.representation(),
                    SyncWindowPolicy.CALENDAR_POLICY,
                    SyncWindowPolicy.KEY_POLICY, policy.windowTimeDomain().name(),
                    (Instant) fast[0], (Instant) fast[1], (Instant) fast[2], (Boolean) fast[3], newest, pass, backTo, Math.max(0, total - finalized), parked, active[0], active[1], failures));
        }
        return new GHSynchronizationStatus(now, cooldown(), List.copyOf(streams));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Set<String> parseScopes(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            Set<String> scopes = new LinkedHashSet<>();
            objectMapper.readTree(json).forEach(node -> scopes.add(node.asText()));
            return Set.copyOf(scopes);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored Google Health scopes are invalid", exception);
        }
    }

    private String toJson(Set<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Set.of() : values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not encode Google Health scopes", exception);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private static LocalDateTime floorCivilTime(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
    }

    private static LocalDateTime civilTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static int sum(int[][] counts) {
        int total = 0;
        for (int[] batch : counts) {
            for (int count : batch) {
                if (count > 0) {
                    total += count;
                } else if (count == Statement.SUCCESS_NO_INFO) {
                    total += 1;
                }
            }
        }
        return total;
    }

    public record WindowProgress(
            Instant start,
            Instant end,
            String pageToken,
            UUID runId,
            long imported,
            int pageCount,
            boolean pagesComplete,
            boolean backfillComplete) {

        public WindowProgress(
                Instant start,
                Instant end,
                String pageToken,
                UUID runId,
                long imported,
                boolean pagesComplete,
                boolean backfillComplete) {
            this(start, end, pageToken, runId, imported, 0, pagesComplete, backfillComplete);
        }
    }
}
