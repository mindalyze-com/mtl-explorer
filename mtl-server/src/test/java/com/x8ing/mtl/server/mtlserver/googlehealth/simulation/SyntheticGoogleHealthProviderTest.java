package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntheticGoogleHealthProviderTest {
    static final DataTypePolicy.Policy HEART = DataTypePolicy.all().stream()
            .filter(policy -> policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE).findFirst().orElseThrow();

    @Test
    void benchmarkWorkloadKeepsTheCalibratedRangeSeedSampleCountAndPageContents() {
        var first = SyntheticGoogleHealthProvider.benchmark(SyntheticGoogleHealthProvider.BENCHMARK_HISTORY_DAYS);
        var second = SyntheticGoogleHealthProvider.benchmark(SyntheticGoogleHealthProvider.BENCHMARK_HISTORY_DAYS);
        assertThat(first.dataset()).isEqualTo(second.dataset());
        assertThat(first.dataset().start()).isEqualTo(Instant.parse("2025-12-11T00:00:00Z"));
        assertThat(first.dataset().end()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(first.dataset().seed()).isEqualTo(73421L);
        assertThat(first.dataset().sampleInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(first.expectedSamples()).isEqualTo(524_400);
        assertThat(first.workloadFingerprint()).isEqualTo("80b9e1ec43e0d8ab977a86d56b0b4d556d394ff07739283ed2b671c1318ab7d7");
        assertThat(second.workloadFingerprint()).isEqualTo(first.workloadFingerprint());
        Instant start = first.dataset().start(), end = start.plus(Duration.ofDays(1));
        assertThat(first.listRaw(HEART, start, end, null)).isEqualTo(second.listRaw(HEART, start, end, null));
    }

    @Test
    void standardDatasetHasTwoCalendarYearsThreeSecondSamplesAndRepeatableWeeklyGaps() {
        var dataset = SyntheticGoogleHealthProvider.standardDataset();
        assertThat(dataset).isEqualTo(SyntheticGoogleHealthProvider.standardDataset());
        assertThat(Duration.between(dataset.start(), dataset.end())).isEqualTo(Duration.ofDays(731));
        assertThat(dataset.sampleInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(dataset.sources()).isEqualTo(1);
        assertThat(dataset.gaps()).hasSize(105).allSatisfy(gap ->
                assertThat(Duration.between(gap.start(), gap.end())).isBetween(Duration.ofHours(2), Duration.ofHours(36)));
        assertThat(dataset.gaps()).anySatisfy(gap ->
                assertThat(Duration.between(gap.start(), gap.end())).isGreaterThan(Duration.ofDays(1)));
        assertThat(dataset.gaps()).anySatisfy(gap ->
                assertThat(gap.end().atOffset(ZoneOffset.UTC).toLocalDate()).isAfter(gap.start().atOffset(ZoneOffset.UTC).toLocalDate()));
        assertThat(SyntheticGoogleHealthProvider.standard().expectedSamples()).isBetween(15_000_000L, 21_052_800L);
    }

    @Test
    void paginationAndOverlappingRangesDescribeTheSameSamplesAcrossMidnightAndGaps() {
        Instant start = Instant.parse("2024-03-30T23:00:00Z"), end = start.plus(Duration.ofHours(3));
        var gap = new SyntheticGoogleHealthProvider.Gap(start.plusSeconds(3300), start.plusSeconds(4500));
        var dataset = new SyntheticGoogleHealthProvider.Dataset(start, end, Duration.ofSeconds(3), 2, 42,
                ZoneId.of("Europe/Zurich"), false, List.of(gap));
        var provider = new SyntheticGoogleHealthProvider(dataset, 113);
        List<JsonNode> all = read(provider, start, end);
        Instant split = start.plusSeconds(3601);
        List<JsonNode> partitioned = new ArrayList<>(read(provider, start, split));
        partitioned.addAll(read(provider, split, end));
        assertThat(partitioned).containsExactlyElementsOf(all);
        assertThat(all).hasSize(6400).allSatisfy(point -> {
            Instant time = Instant.parse(point.at("/heartRate/sampleTime/physicalTime").asText());
            assertThat(time.isBefore(gap.start()) || !time.isBefore(gap.end())).isTrue();
            assertThat(point.path("heartRate").path("beatsPerMinute").isTextual()).isTrue();
            assertThat(point.has("name")).isFalse();
        });
        assertThat(all.stream().map(point -> point.at("/heartRate/sampleTime/utcOffset").asText()).distinct())
                .containsExactly("3600s", "7200s");
        assertThat(provider.expectedSamples()).isEqualTo(all.size());
        assertThat(provider.counters().bytes()).isZero();
    }

    @Test
    void pageTokensResumeAcrossCyclesAndRejectDifferentQueryBounds() {
        var provider = SyntheticGoogleHealthProvider.standard();
        Instant start = SyntheticGoogleHealthProvider.HISTORY_START.plus(Duration.ofDays(2));
        Instant end = start.plus(Duration.ofDays(1));
        GoogleHealthProvider.Page first = provider.listRaw(HEART, start, end, null);
        GoogleHealthProvider.Page second = provider.listRaw(HEART, start, end, first.nextPageToken());
        provider.beginCycle();
        assertThat(provider.listRaw(HEART, start, end, first.nextPageToken())).isEqualTo(second);
        assertThatThrownBy(() -> provider.listRaw(HEART, start.plusSeconds(1), end, first.nextPageToken()))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getReason()).isEqualTo(ApiException.Reason.INVALID_PAGE_TOKEN));
    }

    @Test
    void allOtherStreamsAreEmptyAndReturnedObjectsAreIndependent() {
        var provider = SyntheticGoogleHealthProvider.standard();
        Instant start = SyntheticGoogleHealthProvider.HISTORY_START.plus(Duration.ofDays(2));
        Instant end = start.plusSeconds(12);
        for (var policy : DataTypePolicy.all()) {
            if (policy.mode() == DataTypePolicy.Mode.HEART_RATE_SAMPLE) continue;
            var page = switch (policy.mode()) {
                case DAILY_ROLLUP ->
                        provider.dailyRollUp(policy, start.atOffset(ZoneOffset.UTC).toLocalDate(), end.plus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC).toLocalDate(), null);
                case RECONCILED_INTERVAL -> provider.reconcile(policy, start, end, null);
                default -> provider.listRaw(policy, start, end, null);
            };
            assertThat(page.points()).isEmpty();
            assertThat(page.hasNextPage()).isFalse();
        }
        var first = provider.listRaw(HEART, start, end, null);
        var original = first.points().getFirst().deepCopy();
        ((tools.jackson.databind.node.ObjectNode) first.points().getFirst().path("heartRate")).put("beatsPerMinute", "999");
        assertThat(provider.listRaw(HEART, start, end, null).points().getFirst()).isEqualTo(original);
    }

    private static List<JsonNode> read(SyntheticGoogleHealthProvider provider, Instant start, Instant end) {
        List<JsonNode> points = new ArrayList<>();
        String token = null;
        do {
            var page = provider.listRaw(HEART, start, end, token);
            points.addAll(page.points());
            token = page.nextPageToken();
        } while (token != null);
        return points;
    }
}
