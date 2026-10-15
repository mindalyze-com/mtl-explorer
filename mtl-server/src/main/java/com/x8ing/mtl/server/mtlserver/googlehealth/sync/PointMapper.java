package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.*;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PointMapper {

    private static final Duration MIN_UTC_OFFSET = Duration.ofHours(-12);
    private static final Duration MAX_UTC_OFFSET = Duration.ofHours(14);
    private static final Pattern DATA_POINT_RESOURCE_NAME = Pattern.compile(
            "users/[^/\\s]+/dataTypes/([^/\\s]+)/dataPoints/[^/\\s]+");

    private final DataSourceResolver dataSourceResolver;

    public PointMapper(DataSourceResolver dataSourceResolver) {
        this.dataSourceResolver = dataSourceResolver;
    }

    public void useAccount(String account) {
        dataSourceResolver.useAccount(account);
    }

    public StoredPoint mapPoint(DataTypePolicy.Policy policy,
                                JsonNode point,
                                String dataSourceFamily) {
        return mapPoint(policy, point, dataSourceFamily, true);
    }

    public StoredPoint validatePoint(DataTypePolicy.Policy policy, JsonNode point, String family) {
        return mapPoint(policy, point, family, false);
    }

    private StoredPoint mapPoint(DataTypePolicy.Policy policy, JsonNode point, String dataSourceFamily, boolean resolve) {
        JsonNode payload = point.path(policy.jsonFieldName());
        if (payload.isMissingNode() || payload.isNull()) {
            throw new SyncValidationException(SyncValidationException.Reason.MISSING_PAYLOAD);
        }
        PointTime time = extractTime(point, payload, policy.mode());
        requireQueryTime(policy, time);
        Long dataSourceId = resolve ? dataSourceResolver.resolve(point.path("dataSource")) : null;
        String contentFingerprint = StableFingerprint.ofCanonicalJson(point);
        String resourceName = firstText(point, "name", "dataPointName");
        String identityKey = identityKey(policy, resourceName, time, dataSourceFamily, contentFingerprint);
        return new StoredPoint(
                policy.dataType(),
                StoredRepresentationMapper.from(policy.mode()),
                identityKey,
                contentFingerprint,
                resourceName,
                dataSourceId,
                time.physicalStart(),
                time.physicalEnd(),
                time.originalStart(),
                time.originalEnd(),
                time.civilStart(),
                time.civilEnd(),
                time.utcOffsetStart(),
                time.utcOffsetEnd(),
                time.civilDate(),
                dataSourceFamily,
                point.toString()
        );
    }

    public HeartRateSample mapHeartRate(JsonNode point) {
        return mapHeartRate(point, true);
    }

    public HeartRateSample validateHeartRate(JsonNode point) {
        return mapHeartRate(point, false);
    }

    public HeartRateSample resolveSource(HeartRateSample sample, JsonNode source) {
        return sample.withDataSourceId(dataSourceResolver.resolve(source));
    }

    public StoredPoint resolveSource(StoredPoint point, JsonNode source) {
        return point.withDataSourceId(dataSourceResolver.resolve(source));
    }

    public static boolean needsQueryFingerprint(DataTypePolicy.Policy policy, String resourceName) {
        return hasCompleteResourceName(resourceName, policy.dataType())
               || policy.mode() == DataTypePolicy.Mode.RECONCILED_INTERVAL
               || policy.mode() == DataTypePolicy.Mode.DAILY_ROLLUP;
    }

    private HeartRateSample mapHeartRate(JsonNode point, boolean resolve) {
        JsonNode payload = point.path("heartRate");
        JsonNode sampleTime = payload.path("sampleTime");
        String originalTime = requiredText(sampleTime, "physicalTime");
        Instant physicalTime = parseInstant(originalTime);
        int beatsPerMinute;
        try {
            beatsPerMinute = Integer.parseInt(requiredText(payload, "beatsPerMinute"));
        } catch (NumberFormatException exception) {
            throw new SyncValidationException(SyncValidationException.Reason.INVALID_HEART_RATE, exception);
        }
        Long dataSourceId = resolve ? dataSourceResolver.resolve(point.path("dataSource")) : null;
        JsonNode metadata = payload.path("metadata");
        validateOffset(text(sampleTime, "utcOffset"));
        if (beatsPerMinute <= 0) throw new SyncValidationException(SyncValidationException.Reason.NON_POSITIVE_HEART_RATE);
        String fingerprint = StableFingerprint.ofCanonicalJson(point);
        String resourceName = firstText(point, "name", "dataPointName");
        String identity = StableFingerprint.of(SyncWindowPolicy.KEY_POLICY + "|heart-rate|RAW|"
                                               + (hasCompleteResourceName(resourceName, DataTypePolicy.HEART_RATE_DATA_TYPE)
                ? "name|" + resourceName
                : physicalTime + "|" + StableFingerprint.ofCanonicalJson(point.path("dataSource")) + "|" + fingerprint));
        return new HeartRateSample(
                dataSourceId,
                physicalTime,
                originalTime,
                text(sampleTime, "utcOffset"),
                parseCivilDateTime(sampleTime.path("civilTime")),
                beatsPerMinute,
                firstText(metadata, "motionContext", "heartRateContext"),
                firstText(metadata, "sensorLocation", "measurementLocation"),
                fingerprint, identity, resourceName, point.toString()
        );
    }

    private static void requireQueryTime(DataTypePolicy.Policy policy, PointTime time) {
        boolean hasQueryTime = switch (policy.windowTimeDomain()) {
            case DATE -> time.civilDate() != null;
            case CIVIL -> time.civilStart() != null;
            case PHYSICAL -> policy.recordType() == DataTypePolicy.RecordType.SESSION_END
                    ? time.physicalEnd() != null : time.physicalStart() != null;
        };
        if (!hasQueryTime) {
            throw new SyncValidationException(SyncValidationException.Reason.MISSING_QUERY_TIME);
        }
        if (time.physicalStart() == null && time.civilStart() == null && time.civilDate() == null) {
            throw new SyncValidationException(SyncValidationException.Reason.MISSING_START_TIME);
        }
    }

    private static String identityKey(DataTypePolicy.Policy policy,
                                      String resourceName,
                                      PointTime time,
                                      String dataSourceFamily,
                                      String contentFingerprint) {
        String identity;
        String scope = SyncWindowPolicy.KEY_POLICY + "|" + SyncWindowPolicy.CALENDAR_POLICY + "|"
                       + policy.dataType() + "|" + policy.representation() + "|";
        if (hasCompleteResourceName(resourceName, policy.dataType())) {
            identity = scope + "name|" + resourceName;
        } else if (policy.mode() == DataTypePolicy.Mode.DAILY_ROLLUP) {
            identity = scope + time.civilStart() + "|" + time.civilEnd();
        } else if (policy.mode() == DataTypePolicy.Mode.RECONCILED_INTERVAL) {
            identity = scope + time.physicalStart() + "|" + time.physicalEnd();
        } else {
            identity = scope + time.physicalStart() + "|" + time.physicalEnd() + "|"
                       + time.civilStart() + "|" + time.civilEnd() + "|" + contentFingerprint;
        }
        return StableFingerprint.of(identity);
    }

    public static boolean hasCompleteResourceName(String resourceName, String dataType) {
        if (resourceName == null) return false;
        var matcher = DATA_POINT_RESOURCE_NAME.matcher(resourceName);
        // A collection path without a point ID is shared by unrelated observations.
        return matcher.matches() && matcher.group(1).equals(dataType);
    }

    private static PointTime extractTime(JsonNode point, JsonNode payload, DataTypePolicy.Mode mode) {
        if (mode == DataTypePolicy.Mode.DAILY_ROLLUP) {
            LocalDateTime civilStart = parseCivilDateTime(point.path("civilStartTime"));
            LocalDateTime civilEnd = parseCivilDateTime(point.path("civilEndTime"));
            return new PointTime(null, null, null, null, civilStart, civilEnd,
                    null, null, civilStart == null ? null : civilStart.toLocalDate());
        }
        JsonNode interval = payload.path("interval");
        if (!interval.isMissingNode() && !interval.isNull()) {
            String start = text(interval, "startTime");
            String end = text(interval, "endTime");
            LocalDateTime civilStart = civilTimeOrOffset(interval.path("civilStartTime"), start,
                    text(interval, "startUtcOffset"));
            LocalDateTime civilEnd = civilTimeOrOffset(interval.path("civilEndTime"), end,
                    text(interval, "endUtcOffset"));
            return new PointTime(parseInstantNullable(start), parseInstantNullable(end), start, end,
                    civilStart, civilEnd, text(interval, "startUtcOffset"), text(interval, "endUtcOffset"),
                    civilStart == null ? null : civilStart.toLocalDate());
        }
        JsonNode sampleTime = payload.path("sampleTime");
        if (!sampleTime.isMissingNode() && !sampleTime.isNull()) {
            String physical = text(sampleTime, "physicalTime");
            String offset = text(sampleTime, "utcOffset");
            LocalDateTime civil = civilTimeOrOffset(sampleTime.path("civilTime"), physical, offset);
            return new PointTime(parseInstantNullable(physical), parseInstantNullable(physical), physical, physical,
                    civil, civil, offset, offset, civil == null ? null : civil.toLocalDate());
        }
        LocalDate date = parseDate(payload.path("date"));
        return new PointTime(null, null, null, null,
                date == null ? null : date.atStartOfDay(),
                date == null ? null : date.plusDays(1).atStartOfDay(),
                null, null, date);
    }

    static LocalDateTime parseCivilDateTime(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        LocalDate date = parseDate(node.path("date"));
        if (date == null) return null;
        JsonNode time = node.path("time");
        if (time.isMissingNode() || time.isNull() || time.isEmpty()) return date.atStartOfDay();
        try {
            return LocalDateTime.of(date, LocalTime.of(
                    time.path("hours").asInt(0),
                    time.path("minutes").asInt(0),
                    time.path("seconds").asInt(0),
                    time.path("nanos").asInt(0)));
        } catch (DateTimeException exception) {
            throw new SyncValidationException(SyncValidationException.Reason.INVALID_CIVIL_TIME, exception);
        }
    }

    private static LocalDateTime civilTimeOrOffset(JsonNode civilTime, String physicalTime, String utcOffset) {
        validateOffset(utcOffset);
        LocalDateTime suppliedCivilTime = parseCivilDateTime(civilTime);
        if (suppliedCivilTime != null || physicalTime == null || utcOffset == null || utcOffset.isBlank()) {
            return suppliedCivilTime;
        }
        // Google can omit output-only civil fields while supplying the timestamp and protobuf UTC offset.
        try {
            Duration offset = parseOffset(utcOffset);
            if (offset.compareTo(MIN_UTC_OFFSET) < 0 || offset.compareTo(MAX_UTC_OFFSET) > 0) {
                throw new DateTimeException("UTC offset is outside the supported range");
            }
            return parseInstant(physicalTime).plus(offset).atOffset(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeException exception) {
            log.warn("Google Health civil time could not be derived from its timestamp and UTC offset: type={}",
                    exception.getClass().getSimpleName());
            throw new SyncValidationException(SyncValidationException.Reason.INVALID_UTC_OFFSET, exception);
        }
    }

    private static Duration parseOffset(String value) {
        return value.contains(":") ? Duration.ofSeconds(ZoneOffset.of(value).getTotalSeconds())
                : Duration.parse("PT" + value.toUpperCase(Locale.ROOT));
    }

    private static void validateOffset(String value) {
        if (value == null || value.isBlank()) return;
        try {
            Duration offset = parseOffset(value);
            if (offset.compareTo(MIN_UTC_OFFSET) < 0 || offset.compareTo(MAX_UTC_OFFSET) > 0) {
                throw new DateTimeException("Unsupported UTC offset");
            }
        } catch (DateTimeException exception) {
            log.warn("Google Health offset validation failed: type={}", exception.getClass().getSimpleName());
            throw new SyncValidationException(SyncValidationException.Reason.INVALID_UTC_OFFSET, exception);
        }
    }

    private static LocalDate parseDate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        int year = node.path("year").asInt(0);
        int month = node.path("month").asInt(0);
        int day = node.path("day").asInt(0);
        if (year == 0 || month == 0 || day == 0) return null;
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException exception) {
            throw new SyncValidationException(SyncValidationException.Reason.INVALID_CIVIL_DATE, exception);
        }
    }

    private static Instant parseInstantNullable(String value) {
        return value == null || value.isBlank() ? null : parseInstant(value);
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw new SyncValidationException(SyncValidationException.Reason.INVALID_TIMESTAMP, exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw new SyncValidationException(SyncValidationException.Reason.MISSING_REQUIRED_FIELD);
        }
        return value;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record PointTime(
            Instant physicalStart,
            Instant physicalEnd,
            String originalStart,
            String originalEnd,
            LocalDateTime civilStart,
            LocalDateTime civilEnd,
            String utcOffsetStart,
            String utcOffsetEnd,
            LocalDate civilDate
    ) {
    }
}
