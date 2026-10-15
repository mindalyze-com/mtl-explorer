package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.exercise.TrackScopeService;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.*;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.HealthMetricPayload;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncCoordinator;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.*;
import java.util.*;

@Service
@Slf4j
public class HealthQueryService {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private static final double SECONDS_PER_HOUR = 3_600d;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int DATA_POINT_KIND_ORDER = 0;
    private static final int HEART_RATE_KIND_ORDER = 1;
    private static final Duration DEFAULT_METRIC_RANGE = Duration.ofDays(30);
    private static final Duration DETAILED_HEART_RATE_RANGE = Duration.ofDays(31);
    private static final Duration REAUTHORIZATION_NOTICE = Duration.ofDays(1);
    private static final int FIFTEEN_MINUTE_BUCKET_SECONDS = 15 * 60;
    private static final int ONE_HOUR_BUCKET_SECONDS = 60 * 60;
    private static final int MAX_SAFE_ERROR_LENGTH = 300;
    static final List<MetricDefinition> METRIC_DEFINITIONS = List.of(
            metric("steps", "Steps", GHMetricSeries.Category.ACTIVITY, "steps",
                    GHMetricSeries.ChartType.COLUMN, 0),
            metric("distance", "Distance", GHMetricSeries.Category.ACTIVITY, "km",
                    GHMetricSeries.ChartType.COLUMN, 1),
            metric("active-energy-burned", "Active energy", GHMetricSeries.Category.ACTIVITY, "kcal",
                    GHMetricSeries.ChartType.COLUMN, 0),
            metric("sleep", "Sleep", GHMetricSeries.Category.SLEEP, "h",
                    GHMetricSeries.ChartType.COLUMN, 1),
            metric(DataTypePolicy.HEART_RATE_DATA_TYPE, "Heart rate",
                    GHMetricSeries.Category.HEART_AND_VITALS, "bpm",
                    GHMetricSeries.ChartType.RANGE, 0),
            metric("daily-resting-heart-rate", "Resting heart rate",
                    GHMetricSeries.Category.HEART_AND_VITALS, "bpm", GHMetricSeries.ChartType.LINE, 0),
            metric("daily-heart-rate-variability", "Heart rate variability",
                    GHMetricSeries.Category.HEART_AND_VITALS, "ms", GHMetricSeries.ChartType.LINE, 1),
            metric("daily-oxygen-saturation", "Blood oxygen",
                    GHMetricSeries.Category.HEART_AND_VITALS, "%", GHMetricSeries.ChartType.LINE, 1),
            metric("daily-respiratory-rate", "Respiratory rate",
                    GHMetricSeries.Category.HEART_AND_VITALS, "breaths/min", GHMetricSeries.ChartType.LINE, 1),
            metric("weight", "Weight", GHMetricSeries.Category.BODY, "kg",
                    GHMetricSeries.ChartType.LINE, 1),
            metric("body-fat", "Body fat", GHMetricSeries.Category.BODY, "%",
                    GHMetricSeries.ChartType.LINE, 1),
            metric("daily-vo2-max", "Cardio fitness", GHMetricSeries.Category.BODY, "ml/kg/min",
                    GHMetricSeries.ChartType.LINE, 1),
            metric("daily-sleep-temperature-derivations", "Nightly temperature",
                    GHMetricSeries.Category.HEART_AND_VITALS, "°C", GHMetricSeries.ChartType.LINE, 1),
            metric("core-body-temperature", "Core body temperature",
                    GHMetricSeries.Category.HEART_AND_VITALS, "°C", GHMetricSeries.ChartType.LINE, 1),
            metric("blood-glucose", "Blood glucose", GHMetricSeries.Category.HEART_AND_VITALS, "mg/dL",
                    GHMetricSeries.ChartType.LINE, 1)
    );
    private static final Map<String, MetricDefinition> METRIC_DEFINITION_BY_TYPE = definitionsByType();

    private final ConnectorProperties properties;
    private final MirrorRepository repository;
    private final SyncCoordinator syncCoordinator;
    private final SyncScheduler syncScheduler;
    private final TrackScopeService trackScopeService;
    private final ObjectMapper objectMapper;
    private final GoogleHealthMirrorCache mirrorCache;

    public HealthQueryService(
            ConnectorProperties properties,
            MirrorRepository repository,
            SyncCoordinator syncCoordinator,
            SyncScheduler syncScheduler,
            TrackScopeService trackScopeService,
            ObjectMapper objectMapper,
            GoogleHealthMirrorCache mirrorCache) {
        this.properties = properties;
        this.repository = repository;
        this.syncCoordinator = syncCoordinator;
        this.syncScheduler = syncScheduler;
        this.trackScopeService = trackScopeService;
        this.objectMapper = objectMapper;
        this.mirrorCache = mirrorCache;
    }

    public GHStatusResponse status() {
        AccountConnection connection = repository.findConnection().orElse(null);
        if (!properties.isEnabled()) {
            Set<String> scopes = connection == null ? Set.of() : connection.grantedScopes();
            SyncCoordinator.JobSnapshot idleJob = SyncCoordinator.JobSnapshot.idle();
            return new GHStatusResponse(
                    ConnectorProperties.ConfigurationState.DISABLED,
                    connection == null ? AccountConnection.ConnectionState.DISCONNECTED : connection.state(),
                    GHStatusResponse.AuthorizationAction.NONE,
                    scopes,
                    scopeCategories(scopes),
                    connection == null ? null : connection.connectedAt(),
                    connection == null ? null : connection.refreshTokenExpiresAt(),
                    null,
                    connection == null ? null : connection.lastError(),
                    toResponse(mirrorCache.mirrorSummary(idleJob)),
                    mirrorCache.mirrorSnapshotAt(),
                    repository.routeSummary(),
                    idleJob,
                    repository.findAllSyncStates(), mirrorCache.synchronizationStatus());
        }
        Set<String> scopes = connection == null ? Set.of() : connection.grantedScopes();
        List<GHSyncState> syncStates = repository.findAllSyncStates();
        boolean connected = connection != null
                            && connection.state() == AccountConnection.ConnectionState.CONNECTED;
        Instant now = Instant.now();
        SyncCoordinator.JobSnapshot job = syncCoordinator.status();
        return new GHStatusResponse(
                properties.configurationState(),
                connection == null ? AccountConnection.ConnectionState.DISCONNECTED : connection.state(),
                authorizationAction(connection, now),
                scopes,
                scopeCategories(scopes),
                connection == null ? null : connection.connectedAt(),
                connection == null ? null : connection.refreshTokenExpiresAt(),
                properties.isConfigured() && connected ? syncScheduler.nextRegularSync() : null,
                connection == null ? null : connection.lastError(),
                toResponse(mirrorCache.mirrorSummary(job)),
                mirrorCache.mirrorSnapshotAt(),
                repository.routeSummary(),
                job,
                syncStates, mirrorCache.synchronizationStatus());
    }

    public List<GHTypeSummary> summaries() {
        return mirrorCache.typeSummaries();
    }

    public List<GHDataSourceSummary> sources() {
        return mirrorCache.dataSources();
    }

    private static GHMirrorSummary toResponse(MirrorSummaryRow row) {
        if (row == null) {
            return GHMirrorSummary.empty();
        }
        return new GHMirrorSummary(row.recordCount(), row.earliestRecord(), row.latestRecord());
    }

    static GHStatusResponse.AuthorizationAction authorizationAction(AccountConnection connection, Instant now) {
        if (connection == null || connection.state() == AccountConnection.ConnectionState.DISCONNECTED) {
            return GHStatusResponse.AuthorizationAction.NONE;
        }
        if (connection.state() == AccountConnection.ConnectionState.RECONNECT_REQUIRED) {
            return GHStatusResponse.AuthorizationAction.REAUTHORIZE;
        }
        Instant expiresAt = connection.refreshTokenExpiresAt();
        if (expiresAt == null) {
            return GHStatusResponse.AuthorizationAction.NONE;
        }
        if (!expiresAt.isAfter(now)) {
            return GHStatusResponse.AuthorizationAction.REAUTHORIZE;
        }
        return expiresAt.isBefore(now.plus(REAUTHORIZATION_NOTICE))
                ? GHStatusResponse.AuthorizationAction.EXPIRING_SOON
                : GHStatusResponse.AuthorizationAction.NONE;
    }

    public GHRecordsResponse records(
            String dataType,
            String representation,
            Instant start,
            Instant end,
            Long dataSourceId,
            String cursor,
            Integer requestedPageSize) {
        return records(dataType, representation, start, end, null, null, null,
                dataSourceId, cursor, requestedPageSize);
    }

    public GHRecordsResponse records(String dataType, String representation, Instant start, Instant end,
                                     LocalDate startDate, LocalDate endDate, String timeZone,
                                     Long dataSourceId, String cursor, Integer requestedPageSize) {
        LocalDate endExclusive = null;
        if (startDate != null || endDate != null) {
            if (start != null || end != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose calendar dates or timestamp bounds, not both");
            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be on or after start date");
            }
            try {
                ZoneId zone = ZoneId.of(timeZone == null ? "UTC" : timeZone);
                endExclusive = endDate == null ? null : endDate.plusDays(1);
                start = startDate == null ? null : startDate.atStartOfDay(zone).toInstant();
                end = endExclusive == null ? null : endExclusive.atStartOfDay(zone).toInstant();
            } catch (DateTimeException exception) {
                log.warn("Invalid Google Health records calendar range: errorType={}, error={}",
                        exception.getClass().getSimpleName(), safeError(exception));
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Calendar range or time zone is invalid");
            }
        } else if (start != null && end != null && !start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End timestamp must be after start timestamp");
        }
        int pageSize = requestedPageSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(requestedPageSize, MAX_PAGE_SIZE));
        Cursor decodedCursor = decodeCursor(cursor);
        List<RecordRow> rows = startDate == null && endExclusive == null ? repository.findRecords(
                dataType, representation, start, end, dataSourceId,
                decodedCursor == null ? null : decodedCursor.time(),
                decodedCursor == null ? null : decodedCursor.kindOrder(),
                decodedCursor == null ? null : decodedCursor.id(), pageSize + 1)
                : repository.findRecords(
                dataType, representation, start, end, startDate, endExclusive, dataSourceId,
                decodedCursor == null ? null : decodedCursor.time(),
                decodedCursor == null ? null : decodedCursor.kindOrder(),
                decodedCursor == null ? null : decodedCursor.id(),
                pageSize + 1);
        boolean hasNextPage = rows.size() > pageSize;
        List<RecordRow> pageRows = hasNextPage ? rows.subList(0, pageSize) : rows;
        List<GHRecordResponse> records = pageRows.stream()
                .map(row -> toResponse(row, false))
                .toList();
        String nextCursor = hasNextPage && !pageRows.isEmpty()
                ? encodeCursor(pageRows.getLast())
                : null;
        return new GHRecordsResponse(records, nextCursor);
    }

    public GHRecordResponse record(String recordId) {
        RecordKey key = parseRecordKey(recordId);
        RecordRow row = repository.findRecord(key.kind(), key.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Google Health record not found"));
        return toResponse(row, true);
    }

    public List<GHMetricSeries> metrics(Instant requestedStart, Instant requestedEnd) {
        Instant end = requestedEnd == null ? Instant.now() : requestedEnd;
        Instant start = requestedStart == null ? end.minus(DEFAULT_METRIC_RANGE) : requestedStart;
        Duration range = Duration.between(start, end);
        if (range.isNegative() || range.isZero()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Health metric range must be at least one second");
        }

        Map<String, List<GHMetricPoint>> pointsByType = new HashMap<>();
        int parseFailureCount = 0;
        RuntimeException firstParseFailure = null;
        MetricRecordRow firstFailedRow = null;
        for (MetricRecordRow row : repository.findMetricRecords(start, end)) {
            MetricDefinition definition = METRIC_DEFINITION_BY_TYPE.get(row.dataType());
            if (definition == null) continue;
            try {
                metricPoint(row, objectMapper).ifPresent(point -> pointsByType
                        .computeIfAbsent(row.dataType(), ignored -> new ArrayList<>())
                        .add(point));
            } catch (RuntimeException exception) {
                parseFailureCount++;
                if (firstParseFailure == null) {
                    firstParseFailure = exception;
                    firstFailedRow = row;
                }
                log.debug("Skipping an unreadable Google Health metric value: dataType={}, time={}",
                        row.dataType(), row.recordTime(), exception);
            }
        }
        if (parseFailureCount > 0) {
            log.warn("Skipped unreadable Google Health metric values: count={}, firstDataType={}, firstTime={}, "
                     + "firstErrorType={}, firstError={}",
                    parseFailureCount, firstFailedRow.dataType(), firstFailedRow.recordTime(),
                    firstParseFailure.getClass().getSimpleName(), safeError(firstParseFailure));
        }

        if (range.compareTo(DETAILED_HEART_RATE_RANGE) <= 0) {
            List<HeartRateBucket> heartRateBuckets = repository.findHeartRateBuckets(
                    start, end, heartRateBucketSeconds(range));
            if (!heartRateBuckets.isEmpty()) {
                pointsByType.put(DataTypePolicy.HEART_RATE_DATA_TYPE, heartRateBuckets.stream()
                        .map(bucket -> new GHMetricPoint(
                                bucket.time(), bucket.average(),
                                (double) bucket.minimum(), (double) bucket.maximum()))
                        .toList());
            }
        }

        return METRIC_DEFINITIONS.stream()
                .filter(definition -> pointsByType.containsKey(definition.dataType()))
                .map(definition -> new GHMetricSeries(
                        definition.dataType(),
                        definition.title(),
                        definition.category(),
                        definition.unit(),
                        definition.chartType(),
                        definition.decimals(),
                        pointsByType.get(definition.dataType()).stream()
                                .sorted(Comparator.comparing(GHMetricPoint::time))
                                .toList()))
                .toList();
    }

    static Optional<GHMetricPoint> metricPoint(MetricRecordRow row, ObjectMapper objectMapper) {
        return metricPoint(row, objectMapper, false);
    }

    static Optional<GHMetricPoint> metricPoint(MetricRecordRow row, ObjectMapper objectMapper, boolean preciseSleepDuration) {
        JsonNode payload = objectMapper.readTree(row.rawJson())
                .path(DataTypePolicy.jsonFieldName(row.dataType()));
        if ((payload.isMissingNode() || payload.isNull()) && !row.dataType().equals("sleep")) {
            throw new IllegalArgumentException("Health metric is missing its data-type payload");
        }
        if (row.dataType().equals(DataTypePolicy.HEART_RATE_DATA_TYPE)
            && row.representation().equals(DataTypePolicy.Mode.DAILY_ROLLUP.name())) {
            return metricRangePoint(row, payload, objectMapper);
        }
        Double value = metricValue(row, payload, objectMapper);
        if (preciseSleepDuration && row.dataType().equals("sleep") && number(payload.path("summary"), "minutesAsleep") == null
            && row.recordTime() != null && row.endTime() != null && !row.endTime().isBefore(row.recordTime())) {
            Duration duration = Duration.between(row.recordTime(), row.endTime());
            value = (duration.getSeconds() + duration.getNano() / NANOS_PER_SECOND) / SECONDS_PER_HOUR;
        }
        if (value == null || !Double.isFinite(value)) return Optional.empty();
        return Optional.of(new GHMetricPoint(row.recordTime(), value, null, null));
    }

    private static Optional<GHMetricPoint> metricRangePoint(MetricRecordRow row, JsonNode payload, ObjectMapper objectMapper) {
        var heartRate = objectMapper.treeToValue(payload, HealthMetricPayload.HeartRate.class);
        Double average = heartRate.beatsPerMinuteAvg();
        if (average == null || !Double.isFinite(average)) return Optional.empty();
        return Optional.of(new GHMetricPoint(
                row.recordTime(), average,
                finite(heartRate.beatsPerMinuteMin()),
                finite(heartRate.beatsPerMinuteMax())));
    }

    private static Double metricValue(MetricRecordRow row, JsonNode payload, ObjectMapper objectMapper) {
        return switch (row.dataType()) {
            case "steps" -> {
                var steps = objectMapper.treeToValue(payload, HealthMetricPayload.Steps.class);
                yield first(steps.countSum(), steps.count());
            }
            case "distance" -> {
                var distance = objectMapper.treeToValue(payload, HealthMetricPayload.Distance.class);
                yield divide(first(distance.millimetersSum(), distance.millimeters()), 1_000_000d);
            }
            case "active-energy-burned" -> {
                var energy = objectMapper.treeToValue(payload, HealthMetricPayload.ActiveEnergy.class);
                yield first(energy.kcalSum(), energy.kcal());
            }
            case "sleep" -> sleepHours(row, payload, objectMapper);
            case "heart-rate" -> objectMapper.treeToValue(payload, HealthMetricPayload.HeartRate.class).beatsPerMinute();
            case "daily-resting-heart-rate" -> objectMapper.treeToValue(payload, HealthMetricPayload.RestingHeartRate.class).beatsPerMinute();
            case "daily-heart-rate-variability" -> number(
                    payload,
                    "averageHeartRateVariabilityMilliseconds",
                    "deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds");
            case "daily-oxygen-saturation" -> number(payload, "averagePercentage");
            case "daily-respiratory-rate" -> number(payload, "breathsPerMinute");
            case "weight" -> divide(objectMapper.treeToValue(payload, HealthMetricPayload.Weight.class).weightGrams(), 1_000d);
            case "body-fat" -> number(payload, "percentage");
            case "daily-vo2-max" -> number(payload, "vo2Max");
            case "daily-sleep-temperature-derivations" -> number(payload, "nightlyTemperatureCelsius");
            case "core-body-temperature" -> number(payload, "temperatureCelsius");
            case "blood-glucose" -> number(payload, "bloodGlucoseMilligramsPerDeciliter");
            default -> null;
        };
    }

    private static Double sleepHours(MetricRecordRow row, JsonNode payload, ObjectMapper objectMapper) {
        JsonNode summary = payload.path("summary");
        var sleep = summary.isMissingNode() || summary.isNull() ? null
                : objectMapper.treeToValue(summary, HealthMetricPayload.SleepSummary.class);
        Double minutes = sleep == null ? null : finite(sleep.minutesAsleep());
        if (minutes != null) return minutes / 60d;
        if (row.recordTime() == null || row.endTime() == null || row.endTime().isBefore(row.recordTime())) return null;
        return Duration.between(row.recordTime(), row.endTime()).toMinutes() / 60d;
    }

    private static Double number(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) return value.doubleValue();
            if (value.isTextual()) {
                double parsed = value.asDouble(Double.NaN);
                if (Double.isFinite(parsed)) return parsed;
            }
        }
        return null;
    }

    private static Double first(Double preferred, Double alternative) {
        Double value = finite(preferred);
        return value == null ? finite(alternative) : value;
    }

    private static Double finite(Double value) {
        return value != null && Double.isFinite(value) ? value : null;
    }

    private static Double divide(Double value, double divisor) {
        return value == null ? null : value / divisor;
    }

    private static int heartRateBucketSeconds(Duration range) {
        if (range.compareTo(Duration.ofDays(7)) <= 0) return FIFTEEN_MINUTE_BUCKET_SECONDS;
        return ONE_HOUR_BUCKET_SECONDS;
    }

    private static MetricDefinition metric(
            String dataType,
            String title,
            GHMetricSeries.Category category,
            String unit,
            GHMetricSeries.ChartType chartType,
            int decimals) {
        return new MetricDefinition(dataType, title, category, unit, chartType, decimals);
    }

    private static Map<String, MetricDefinition> definitionsByType() {
        Map<String, MetricDefinition> definitions = new LinkedHashMap<>();
        for (MetricDefinition definition : METRIC_DEFINITIONS) definitions.put(definition.dataType(), definition);
        return Map.copyOf(definitions);
    }

    private GHRecordResponse toResponse(RecordRow row, boolean includePayload) {
        GHRecordValue measurement = recordValue(row);
        String summary = row.valueSummary();
        if (measurement != null) {
            NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
            format.setMaximumFractionDigits(METRIC_DEFINITION_BY_TYPE.get(row.dataType()).decimals());
            summary = format.format(measurement.value()) + " " + measurement.unit();
        }
        return new GHRecordResponse(
                row.recordId(),
                row.dataType(),
                row.representation(),
                row.recordTime(),
                row.temporalKind(),
                row.civilDate() == null ? null : row.civilDate().toString(),
                row.civilTime(),
                row.originalTime(),
                measurement,
                summary,
                row.dataSourceId(),
                row.sourceName(),
                row.sourcePlatform(),
                row.routeState(),
                row.routeError(),
                row.gpsTrackId(),
                trackScopeService.canonicalTrackId(row.gpsTrackId()),
                includePayload ? parsePayload(row.recordId(), row.rawJson()) : null);
    }

    private GHRecordValue recordValue(RecordRow row) {
        MetricDefinition definition = METRIC_DEFINITION_BY_TYPE.get(row.dataType());
        String valueJson = row.valueJson() == null ? row.rawJson() : row.valueJson();
        if (definition == null || valueJson == null) return null;
        try {
            return metricPoint(new MetricRecordRow(row.dataType(), row.representation(), row.recordTime(),
                    row.endTime(), valueJson), objectMapper, true)
                    .map(point -> new GHRecordValue(point.value(), definition.unit(), point.minimum(), point.maximum()))
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Unreadable Google Health record measurement: recordId={}, dataType={}, errorType={}",
                    row.recordId(), row.dataType(), exception.getClass().getSimpleName());
            log.debug("Google Health record measurement parse failure: recordId={}", row.recordId(), exception);
            return null;
        }
    }

    private Map<String, Object> parsePayload(String recordId, String rawJson) {
        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            log.error("Stored Google Health record payload is invalid: recordId={}", recordId, exception);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored Google Health record is invalid",
                    exception);
        }
    }

    private static Map<String, Boolean> scopeCategories(Set<String> scopes) {
        Map<String, Boolean> categories = new LinkedHashMap<>();
        categories.put("activityAndFitness", scopes.contains(OAuthScopes.ACTIVITY_AND_FITNESS));
        categories.put("healthMetricsAndMeasurements", scopes.contains(OAuthScopes.HEALTH_METRICS_AND_MEASUREMENTS));
        categories.put("sleep", scopes.contains(OAuthScopes.SLEEP));
        categories.put("location", scopes.contains(OAuthScopes.LOCATION));
        return categories;
    }

    private static String encodeCursor(RecordRow row) {
        int kindOrder = row.kind() == RecordRow.RecordKind.DATA_POINT
                ? DATA_POINT_KIND_ORDER
                : HEART_RATE_KIND_ORDER;
        String raw = row.recordTime().getEpochSecond() + ":" + row.recordTime().getNano()
                     + ":" + kindOrder + ":" + row.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length != 4) throw new IllegalArgumentException();
            long epochSecond = Long.parseLong(parts[0]);
            int nano = Integer.parseInt(parts[1]);
            int kindOrder = Integer.parseInt(parts[2]);
            long id = Long.parseLong(parts[3]);
            if ((kindOrder != DATA_POINT_KIND_ORDER && kindOrder != HEART_RATE_KIND_ORDER) || id <= 0L) {
                throw new IllegalArgumentException();
            }
            return new Cursor(
                    Instant.ofEpochSecond(epochSecond, nano),
                    kindOrder,
                    id);
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Health cursor is invalid",
                    exception);
        }
    }

    private static RecordKey parseRecordKey(String recordId) {
        try {
            if (recordId.startsWith("point:")) {
                return new RecordKey(RecordRow.RecordKind.DATA_POINT,
                        Long.parseLong(recordId.substring("point:".length())));
            }
            if (recordId.startsWith("heart:")) {
                return new RecordKey(RecordRow.RecordKind.HEART_RATE_SAMPLE,
                        Long.parseLong(recordId.substring("heart:".length())));
            }
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Health record id is invalid",
                    exception);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google Health record id is invalid");
    }

    private static String safeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return throwable.getClass().getSimpleName();
        String normalized = message.replaceAll("[\\r\\n\\t]", " ");
        return normalized.length() <= MAX_SAFE_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_SAFE_ERROR_LENGTH);
    }

    private record Cursor(Instant time, int kindOrder, long id) {
    }

    private record RecordKey(RecordRow.RecordKind kind, long id) {
    }

    record MetricDefinition(
            String dataType,
            String title,
            GHMetricSeries.Category category,
            String unit,
            GHMetricSeries.ChartType chartType,
            int decimals
    ) {
    }
}
