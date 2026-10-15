package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiClient;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthScopes;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.HeartRateSample;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.MirrorRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.persistence.StoredPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PointMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PointMapper mapper = new PointMapper(
            new DataSourceResolver(mock(MirrorRepository.class)));

    @ParameterizedTest
    @CsvSource({"exercise,RAW", "sleep,RAW", "weight,RAW", "steps,RECONCILED_INTERVAL",
            "daily-vo2-max,RAW", "steps,DAILY_ROLLUP"})
    void rejectsMissingQueryTimeBeforeResolvingSources(String dataType, DataTypePolicy.Mode mode) {
        DataSourceResolver sources = mock(DataSourceResolver.class);
        PointMapper checkedMapper = new PointMapper(sources);
        DataTypePolicy.Policy policy = DataTypePolicy.all().stream().filter(value ->
                value.dataType().equals(dataType) && value.mode() == mode).findFirst().orElseThrow();
        var point = objectMapper.createObjectNode();
        point.putObject(policy.jsonFieldName());
        point.putObject("dataSource").put("applicationName", "Synthetic source");

        assertThatThrownBy(() -> checkedMapper.mapPoint(policy, point, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no time value for its query boundary");
        verifyNoInteractions(sources);
    }

    @Test
    void civilExerciseCannotSubstituteAPhysicalTimeForItsRequiredCivilQueryKey() {
        var policy = DataTypePolicy.all().stream().filter(value -> value.dataType().equals("exercise"))
                .findFirst().orElseThrow();
        var point = objectMapper.readTree("""
                {"exercise":{"interval":{"startTime":"2048-09-05T12:00:00Z","endTime":"2048-09-05T13:00:00Z"}}}
                """);
        assertThatThrownBy(() -> mapper.mapPoint(policy, point, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("query boundary");
    }

    @Test
    void derivesMissingExerciseCivilTimesFromEachEndpointOffset() {
        var policy = DataTypePolicy.all().stream().filter(value -> value.dataType().equals("exercise"))
                .findFirst().orElseThrow();
        var point = mapper.mapPoint(policy, objectMapper.readTree("""
                {"exercise":{"interval":{
                  "startTime":"2048-03-29T00:30:00.123456789Z","startUtcOffset":"3600s",
                  "endTime":"2048-03-29T01:30:00.123456789Z","endUtcOffset":"7200s"
                }}}
                """), null);
        assertThat(point.civilStartTime()).isEqualTo(LocalDateTime.parse("2048-03-29T01:30:00.123456789"));
        assertThat(point.civilEndTime()).isEqualTo(LocalDateTime.parse("2048-03-29T03:30:00.123456789"));
        assertThat(point.physicalStartTime()).isEqualTo(Instant.parse("2048-03-29T00:30:00.123456789Z"));
    }

    @ParameterizedTest
    @CsvSource({"0s,2048-09-05T02:00:00", "-18000s,2048-09-04T21:00:00", "19800s,2048-09-05T07:30:00"})
    void offsetFallbackPreservesTheLocalCalendarDate(String offset, String expected) {
        var policy = DataTypePolicy.all().stream().filter(value -> value.dataType().equals("exercise"))
                .findFirst().orElseThrow();
        var point = objectMapper.readTree("""
                {"exercise":{"interval":{"startTime":"2048-09-05T02:00:00Z","startUtcOffset":"%s"}}}
                """.formatted(offset));
        assertThat(mapper.mapPoint(policy, point, null).civilStartTime()).isEqualTo(LocalDateTime.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({"invalid", "999999s"})
    void rejectsInvalidFallbackOffsets(String offset) {
        var policy = DataTypePolicy.all().stream().filter(value -> value.dataType().equals("exercise"))
                .findFirst().orElseThrow();
        var point = objectMapper.readTree("""
                {"exercise":{"interval":{"startTime":"2048-09-05T02:00:00Z","startUtcOffset":"%s"}}}
                """.formatted(offset));
        assertThatThrownBy(() -> mapper.mapPoint(policy, point, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UTC offset is invalid");
    }

    @Test
    void sleepRequiresBothItsPhysicalEndQueryKeyAndAUsableRecordStart() {
        var policy = DataTypePolicy.all().stream().filter(value -> value.dataType().equals("sleep"))
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> mapper.mapPoint(policy, objectMapper.readTree("""
                {"sleep":{"interval":{"startTime":"2048-09-05T12:00:00Z"}}}
                """), null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("query boundary");
        assertThatThrownBy(() -> mapper.mapPoint(policy, objectMapper.readTree("""
                {"sleep":{"interval":{"endTime":"2048-09-05T13:00:00Z"}}}
                """), null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no usable start time");
    }

    @Test
    void keepsExactOriginalHeartRateTimestampAndMetadata() throws Exception {
        HeartRateSample sample = mapper.mapHeartRate(objectMapper.readTree("""
                {"heartRate":{"sampleTime":{"physicalTime":"2026-08-28T10:11:12.123456789Z",
                "utcOffset":"+02:00","civilTime":{"date":{"year":2026,"month":8,"day":28},
                "time":{"hours":12,"minutes":11,"seconds":12,"nanos":123456789}}},
                "beatsPerMinute":"61","metadata":{"motionContext":"resting","sensorLocation":"wrist"}}}
                """));

        assertThat(sample.originalSampleTime()).isEqualTo("2026-08-28T10:11:12.123456789Z");
        assertThat(sample.sampleTime().getNano()).isEqualTo(123456789);
        assertThat(sample.utcOffset()).isEqualTo("+02:00");
        assertThat(sample.civilTime()).isEqualTo(LocalDateTime.parse("2026-08-28T12:11:12.123456789"));
        assertThat(sample.beatsPerMinute()).isEqualTo(61);
        assertThat(sample.motionContext()).isEqualTo("resting");
        assertThat(sample.sensorLocation()).isEqualTo("wrist");
    }

    @Test
    void preservesPhysicalAndCivilDurationsAcrossZurichSpringForward() throws Exception {
        DataTypePolicy.Policy policy = new DataTypePolicy.Policy(
                "exercise", DataTypePolicy.Mode.RAW,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);
        StoredPoint point = mapper.mapPoint(policy, objectMapper.readTree("""
                {"name":"users/me/dataTypes/exercise/dataPoints/spring-forward",
                 "exercise":{"interval":{
                   "startTime":"2026-03-29T00:30:00Z",
                   "endTime":"2026-03-29T01:30:00Z",
                   "civilStartTime":{"date":{"year":2026,"month":3,"day":29},
                                     "time":{"hours":1,"minutes":30}},
                   "civilEndTime":{"date":{"year":2026,"month":3,"day":29},
                                   "time":{"hours":3,"minutes":30}},
                   "startUtcOffset":"+01:00","endUtcOffset":"+02:00"}}}
                """), null);

        assertThat(point.physicalStartTime()).isEqualTo(Instant.parse("2026-03-29T00:30:00Z"));
        assertThat(point.physicalEndTime()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        assertThat(Duration.between(point.physicalStartTime(), point.physicalEndTime()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(point.civilStartTime()).isEqualTo(LocalDateTime.parse("2026-03-29T01:30:00"));
        assertThat(point.civilEndTime()).isEqualTo(LocalDateTime.parse("2026-03-29T03:30:00"));
        assertThat(Duration.between(point.civilStartTime(), point.civilEndTime()))
                .isEqualTo(Duration.ofHours(2));
        assertThat(point.utcOffsetStart()).isEqualTo("+01:00");
        assertThat(point.utcOffsetEnd()).isEqualTo("+02:00");
    }

    @Test
    void preservesARealZeroStepCountAsData() throws Exception {
        DataTypePolicy.Policy policy = new DataTypePolicy.Policy(
                "steps", DataTypePolicy.Mode.RECONCILED_INTERVAL,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);
        StoredPoint point = mapper.mapPoint(policy, objectMapper.readTree("""
                {"steps":{"interval":{"startTime":"2026-08-28T10:00:00Z",
                "endTime":"2026-08-28T10:05:00Z"},"count":"0"}}
                """), ApiClient.ALL_SOURCES);

        assertThat(point.rawJson()).contains("\"count\":\"0\"");
        assertThat(point.physicalStartTime()).isNotNull();
        assertThat(point.representation()).isEqualTo(StoredPoint.Representation.RECONCILED_INTERVAL);
    }

    @Test
    void distinguishesMissingDailyRollupValuesFromARealZero() throws Exception {
        DataTypePolicy.Policy policy = new DataTypePolicy.Policy(
                "steps", DataTypePolicy.Mode.DAILY_ROLLUP,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);

        assertThat(SyncService.shouldPersistPoint(
                policy, objectMapper.readTree("{\"civilStartTime\":{}}"))).isFalse();
        assertThat(SyncService.shouldPersistPoint(
                policy, objectMapper.readTree("{\"steps\":{\"count\":\"0\"}}"))).isTrue();
    }

    @Test
    void namedRollupIdentityUsesTheGoogleName() throws Exception {
        DataTypePolicy.Policy policy = new DataTypePolicy.Policy(
                "steps", DataTypePolicy.Mode.DAILY_ROLLUP,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);
        String pointJson = """
                {"name":"users/me/dataTypes/steps/dataPoints/server-name",
                 "civilStartTime":{"date":{"year":2026,"month":8,"day":27}},
                 "civilEndTime":{"date":{"year":2026,"month":8,"day":28}},
                 "steps":{"count":"123"}}
                """;

        StoredPoint point = mapper.mapPoint(
                policy, objectMapper.readTree(pointJson), ApiClient.ALL_SOURCES);

        var changed = objectMapper.readTree(pointJson.replace("123", "456"));
        assertThat(mapper.mapPoint(policy, changed, ApiClient.ALL_SOURCES).identityKey()).isEqualTo(point.identityKey());
        assertThat(mapper.mapPoint(policy, changed, ApiClient.ALL_SOURCES).fingerprint()).isNotEqualTo(point.fingerprint());

    }

    @Test
    void namedReconciledIdentityUsesTheGoogleName() throws Exception {
        DataTypePolicy.Policy policy = new DataTypePolicy.Policy(
                "steps", DataTypePolicy.Mode.RECONCILED_INTERVAL,
                OAuthScopes.ACTIVITY_AND_FITNESS, 90);
        var source = objectMapper.readTree("""
                {"name":"users/me/dataTypes/steps/dataPoints/server-name",
                 "steps":{"interval":{"startTime":"2026-08-28T10:00:00.123456789Z",
                                         "endTime":"2026-08-28T10:05:00.987654321Z"},
                          "count":"12"}}
                """);

        StoredPoint point = mapper.mapPoint(policy, source, ApiClient.ALL_SOURCES);

        var changed = objectMapper.readTree(source.toString().replace("\"count\":\"12\"", "\"count\":\"13\""));
        assertThat(mapper.mapPoint(policy, changed, ApiClient.ALL_SOURCES).identityKey()).isEqualTo(point.identityKey());
        assertThat(mapper.mapPoint(policy, changed, ApiClient.ALL_SOURCES).fingerprint()).isNotEqualTo(point.fingerprint());

    }

    @Test
    void namelessReconciledKeysIgnoreReturnedValueAndFieldOrder() {
        var policy = DataTypePolicy.all().stream().filter(p -> p.dataType().equals("steps") && p.mode() == DataTypePolicy.Mode.RECONCILED_INTERVAL).findFirst().orElseThrow();
        var first = objectMapper.readTree("""
                {"steps":{"count":"12","interval":{"startTime":"2026-08-28T10:00:00Z","endTime":"2026-08-28T10:05:00Z"}}}
                """);
        var corrected = objectMapper.readTree(first.toString().replace("\"12\"", "\"13\""));
        assertThat(mapper.validatePoint(policy, first, null).identityKey()).isEqualTo(mapper.validatePoint(policy, corrected, null).identityKey());
        assertThat(mapper.validatePoint(policy, first, null).fingerprint()).isNotEqualTo(mapper.validatePoint(policy, corrected, null).fingerprint());
    }

    @Test
    void heartRateNamesPreserveIdentityWhileNamelessObservationsKeepTheirDifferences() {
        var first = objectMapper.readTree("""
                {"name":"users/synthetic/dataTypes/heart-rate/dataPoints/one","heartRate":{"beatsPerMinute":"61","sampleTime":{"physicalTime":"2026-08-28T10:00:00Z"}}}
                """);
        var corrected = objectMapper.readTree(first.toString().replace("\"61\"", "\"62\""));
        assertThat(mapper.validateHeartRate(first).identityKey()).isEqualTo(mapper.validateHeartRate(corrected).identityKey());
        var rawA = objectMapper.createObjectNode();
        rawA.set("heartRate", first.path("heartRate"));
        var rawB = objectMapper.createObjectNode();
        rawB.set("heartRate", corrected.path("heartRate"));
        assertThat(mapper.validateHeartRate(rawA).identityKey()).isNotEqualTo(mapper.validateHeartRate(rawB).identityKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "users/synthetic/dataTypes/respiratory-rate-sleep-summary/dataPoints/",
            "users/synthetic/dataTypes/respiratory-rate-sleep-summary/dataPoints",
            "users/synthetic/dataTypes/weight/dataPoints/point-one",
            "users//dataTypes/respiratory-rate-sleep-summary/dataPoints/point-one",
            "users/synthetic/dataTypes/respiratory-rate-sleep-summary/dataPoints/point-one/extra",
            "users/synthetic/dataTypes/respiratory-rate-sleep-summary/dataPoints/ ",
            "not-a-resource-name"
    })
    void unusableRespiratoryNamesKeepDistinctMeasurementsAndStableReplays(String name) {
        var policy = respiratoryPolicy();
        var first = respiratoryPoint(name, "2048-09-05T07:00:00Z", 14);
        var laterSameDay = respiratoryPoint(name, "2048-09-05T15:00:00Z", 14);
        var nextDay = respiratoryPoint(name, "2048-09-06T07:00:00Z", 14);
        var differentValue = respiratoryPoint(name, "2048-09-05T07:00:00Z", 15);
        var row = mapper.validatePoint(policy, first, null);

        assertThat(row.googleResourceName()).isEqualTo(name);
        assertThat(mapper.validatePoint(policy, first.deepCopy(), null).identityKey()).isEqualTo(row.identityKey());
        assertThat(mapper.validatePoint(policy, laterSameDay, null).identityKey()).isNotEqualTo(row.identityKey());
        assertThat(mapper.validatePoint(policy, nextDay, null).identityKey()).isNotEqualTo(row.identityKey());
        assertThat(mapper.validatePoint(policy, differentValue, null).identityKey()).isNotEqualTo(row.identityKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "dataPointName"})
    void completeRespiratoryNamesPreserveIdentityAcrossContentCorrections(String nameField) {
        String name = "users/synthetic/dataTypes/respiratory-rate-sleep-summary/dataPoints/point-one";
        var first = respiratoryPoint(null, "2048-09-05T07:00:00Z", 14);
        first.put(nameField, name);
        var corrected = first.deepCopy();
        corrected.withObject("respiratoryRateSleepSummary").withObject("fullSleepStats")
                .put("breathsPerMinute", 15);
        var differentRecord = first.deepCopy();
        differentRecord.put(nameField, name.replace("point-one", "point-two"));
        var policy = respiratoryPolicy();

        assertThat(mapper.validatePoint(policy, corrected, null).identityKey())
                .isEqualTo(mapper.validatePoint(policy, first, null).identityKey());
        assertThat(mapper.validatePoint(policy, corrected, null).fingerprint())
                .isNotEqualTo(mapper.validatePoint(policy, first, null).fingerprint());
        assertThat(mapper.validatePoint(policy, differentRecord, null).identityKey())
                .isNotEqualTo(mapper.validatePoint(policy, first, null).identityKey());
    }

    @Test
    void namelessRespiratoryMeasurementsKeepStableDistinctIdentities() {
        var first = respiratoryPoint(null, "2048-09-05T07:00:00Z", 14);
        var second = respiratoryPoint(null, "2048-09-05T15:00:00Z", 14);
        var policy = respiratoryPolicy();
        assertThat(mapper.validatePoint(policy, first.deepCopy(), null).identityKey())
                .isEqualTo(mapper.validatePoint(policy, first, null).identityKey());
        assertThat(mapper.validatePoint(policy, second, null).identityKey())
                .isNotEqualTo(mapper.validatePoint(policy, first, null).identityKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"users/synthetic/dataTypes/heart-rate/dataPoints/",
            "users/synthetic/dataTypes/weight/dataPoints/point-one"})
    void unusableHeartRateNamesAlsoKeepDistinctMeasurements(String name) {
        var first = objectMapper.readTree("""
                {"name":"%s","heartRate":{"beatsPerMinute":"61",
                 "sampleTime":{"physicalTime":"2048-09-05T07:00:00Z"}}}
                """.formatted(name));
        var later = objectMapper.readTree(first.toString().replace("07:00:00", "07:01:00"));
        var differentValue = objectMapper.readTree(first.toString().replace("\"61\"", "\"62\""));
        assertThat(mapper.validateHeartRate(objectMapper.readTree(first.toString())).identityKey())
                .isEqualTo(mapper.validateHeartRate(first).identityKey());
        assertThat(mapper.validateHeartRate(later).identityKey()).isNotEqualTo(mapper.validateHeartRate(first).identityKey());
        assertThat(mapper.validateHeartRate(differentValue).identityKey()).isNotEqualTo(mapper.validateHeartRate(first).identityKey());
    }

    private static DataTypePolicy.Policy respiratoryPolicy() {
        return DataTypePolicy.all().stream().filter(policy ->
                policy.dataType().equals("respiratory-rate-sleep-summary")).findFirst().orElseThrow();
    }

    private ObjectNode respiratoryPoint(String name, String time, double breathsPerMinute) {
        var point = objectMapper.createObjectNode();
        if (name != null) point.put("name", name);
        var payload = point.putObject("respiratoryRateSleepSummary");
        payload.putObject("sampleTime").put("physicalTime", time).put("utcOffset", "0s");
        payload.putObject("fullSleepStats").put("breathsPerMinute", breathsPerMinute);
        return point;
    }

    @Test
    void rejectsFormerEighteenHourOffsetsEvenWhenCivilFieldsArePresent() {
        var point = objectMapper.readTree("""
                {"heartRate":{"beatsPerMinute":"61","sampleTime":{"physicalTime":"2026-08-28T10:00:00Z","utcOffset":"64800s","civilTime":{"date":{"year":2026,"month":8,"day":29}}}}}
                """);
        assertThatThrownBy(() -> mapper.validateHeartRate(point)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UTC-12 through UTC+14");
    }

    @Test
    void canonicalFingerprintIgnoresObjectFieldOrder() throws Exception {
        assertThat(StableFingerprint.ofCanonicalJson(objectMapper.readTree("{\"a\":1,\"b\":2}")))
                .isEqualTo(StableFingerprint.ofCanonicalJson(objectMapper.readTree("{\"b\":2,\"a\":1}")));
    }
}
