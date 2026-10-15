package com.x8ing.mtl.server.mtlserver.googlehealth.simulation;

import com.x8ing.mtl.server.mtlserver.googlehealth.client.ApiException;
import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.DataTypePolicy;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.StableFingerprint;
import com.x8ing.mtl.server.mtlserver.googlehealth.sync.SyncBudget;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Direct object provider. Only one page is allocated; samples are stable across overlapping queries and cycles.
 * Other streams return empty pages. No HTTP client, socket, OAuth token or serialized response is involved.
 */
public final class SyntheticGoogleHealthProvider implements GoogleHealthProvider {
    private static final String TOKEN_SEPARATOR = ":";
    public static final Instant HISTORY_START = Instant.parse("2024-01-01T00:00:00Z");
    public static final Instant HISTORY_END = Instant.parse("2026-01-01T00:00:00Z");
    public static final int BENCHMARK_HISTORY_DAYS = 21;
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(3);
    private static final Duration GAP_PERIOD = Duration.ofDays(7);
    private static final int MIN_GAP_HOURS = 2;
    private static final int MAX_GAP_HOURS = 36;
    private static final long DEFAULT_SEED = 73421L;
    private static final int DEFAULT_PAGE_SIZE = 10_000;
    private final Dataset dataset;
    private final int pageSize;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Long> readsByPurpose = new LinkedHashMap<>();
    private Consumer<Request> beforeRead = request -> {
    };
    private UnaryOperator<ObjectNode> sampleTransform = UnaryOperator.identity();
    private Duration responseDelay = Duration.ZERO;
    private String purpose = "UNSPECIFIED";
    private long attempts, pages, probes, generatedSamples, readNanos;

    public record Dataset(Instant start, Instant end, Duration sampleInterval, int sources, long seed,
                          ZoneId zone, boolean resourceNames, List<Gap> gaps) {
        public Dataset {
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            Objects.requireNonNull(sampleInterval);
            Objects.requireNonNull(zone);
            if (!start.isBefore(end) || sampleInterval.isNegative() || sampleInterval.isZero()
                || sampleInterval.getNano() != 0 || sources < 1) {
                throw new IllegalArgumentException("Dataset needs a nonempty range, whole positive seconds and at least one source");
            }
            List<Gap> merged = new ArrayList<>();
            for (Gap gap : gaps.stream().sorted(Comparator.comparing(Gap::start)).toList()) {
                if (gap.start().isBefore(start) || gap.end().isAfter(end)) throw new IllegalArgumentException("Gap outside dataset");
                if (!merged.isEmpty() && !gap.start().isAfter(merged.getLast().end())) {
                    Gap previous = merged.removeLast();
                    merged.add(new Gap(previous.start(), gap.end().isAfter(previous.end()) ? gap.end() : previous.end()));
                } else merged.add(gap);
            }
            gaps = List.copyOf(merged);
        }
    }

    public record Gap(Instant start, Instant end) {
        public Gap {
            if (!start.isBefore(end)) throw new IllegalArgumentException("Empty gap");
        }
    }

    /**
     * Fixed two-year, three-second dataset with seeded weekly band-off gaps.
     */
    public static Dataset standardDataset() {
        return weeklyGaps(HISTORY_START, HISTORY_END, SAMPLE_INTERVAL, 1, DEFAULT_SEED,
                ZoneId.of("Europe/Zurich"), false);
    }

    public static SyntheticGoogleHealthProvider standard() {
        return new SyntheticGoogleHealthProvider(standardDataset(), DEFAULT_PAGE_SIZE);
    }

    /**
     * Fixed shorter workload for performance comparisons; never recalibrate after an import optimization.
     */
    public static SyntheticGoogleHealthProvider benchmark(int historyDays) {
        if (historyDays < 1 || historyDays > Duration.between(HISTORY_START, HISTORY_END).toDays()) {
            throw new IllegalArgumentException("Benchmark history must contain between one day and two years");
        }
        Dataset dataset = weeklyGaps(HISTORY_END.minus(Duration.ofDays(historyDays)), HISTORY_END,
                SAMPLE_INTERVAL, 1, DEFAULT_SEED, ZoneId.of("Europe/Zurich"), false);
        return new SyntheticGoogleHealthProvider(dataset, DEFAULT_PAGE_SIZE);
    }

    public String workloadFingerprint() {
        return StableFingerprint.of("heart-rate-simulation-v1|" + dataset + "|pageSize=" + pageSize);
    }

    public int pageSize() {
        return pageSize;
    }

    public static Dataset weeklyGaps(Instant start, Instant end, Duration interval, int sources,
                                     long seed, ZoneId zone, boolean resourceNames) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Gap> gaps = new ArrayList<>();
        for (Instant week = start; week.isBefore(end); week = week.plus(GAP_PERIOD)) {
            Instant gapStart = week.plusSeconds(random.nextLong(Duration.ofDays(1).toSeconds()));
            if (!gapStart.isBefore(end)) continue;
            Instant gapEnd = gapStart.plus(Duration.ofHours(random.nextInt(MIN_GAP_HOURS, MAX_GAP_HOURS + 1)));
            gaps.add(new Gap(gapStart, gapEnd.isAfter(end) ? end : gapEnd));
        }
        return new Dataset(start, end, interval, sources, seed, zone, resourceNames, gaps);
    }

    public Dataset dataset() {
        return dataset;
    }

    public long expectedSamples() {
        return expectedSamples(dataset.start(), dataset.end());
    }

    public long expectedSamples(Instant start, Instant end) {
        Instant lower = start.isBefore(dataset.start()) ? dataset.start() : start;
        Instant upper = end.isAfter(dataset.end()) ? dataset.end() : end;
        if (!lower.isBefore(upper)) return 0;
        long ticks = tickAtOrAfter(upper) - tickAtOrAfter(lower);
        for (Gap gap : dataset.gaps()) {
            Instant gapStart = gap.start().isBefore(lower) ? lower : gap.start();
            Instant gapEnd = gap.end().isAfter(upper) ? upper : gap.end();
            if (gapStart.isBefore(gapEnd)) ticks -= tickAtOrAfter(gapEnd) - tickAtOrAfter(gapStart);
        }
        return Math.multiplyExact(ticks, dataset.sources());
    }

    public record Request(DataTypePolicy.Policy policy, Instant start, Instant end, String pageToken,
                          String purpose, long attempt) {
    }

    public SyntheticGoogleHealthProvider(Dataset dataset, int pageSize) {
        this.dataset = Objects.requireNonNull(dataset);
        if (pageSize < 1) throw new IllegalArgumentException("Page size must be positive");
        this.pageSize = pageSize;
    }

    /**
     * Inject quota pauses, invalid tokens or budget yields before returning a page.
     */
    public void beforeRead(Consumer<Request> action) {
        beforeRead = Objects.requireNonNull(action);
    }

    /**
     * Apply deterministic corrections/removals; returning null omits a sample.
     */
    public void transformSamples(UnaryOperator<ObjectNode> transform) {
        sampleTransform = Objects.requireNonNull(transform);
    }

    public void responseDelay(Duration delay) {
        if (delay.isNegative()) throw new IllegalArgumentException("Delay must not be negative");
        responseDelay = delay;
    }

    public Map<String, Long> readsByPurpose() {
        return Map.copyOf(readsByPurpose);
    }

    public long generatedSamples() {
        return generatedSamples;
    }

    public Duration readTime() {
        return Duration.ofNanos(readNanos);
    }

    @Override
    public Page listRaw(DataTypePolicy.Policy policy, Instant start, Instant end, String token) {
        long started = System.nanoTime();
        try {
            admit(new Request(policy, start, end, token, purpose, ++attempts));
            if (policy.mode() != DataTypePolicy.Mode.HEART_RATE_SAMPLE) return empty(token);
            if (!start.isBefore(end)) throw new IllegalArgumentException("Empty query range");
            Instant lower = start.isBefore(dataset.start()) ? dataset.start() : start;
            Instant upper = end.isAfter(dataset.end()) ? dataset.end() : end;
            if (!lower.isBefore(upper)) return empty(token);
            long first = Math.multiplyExact(tickAtOrAfter(lower), dataset.sources());
            long limit = Math.multiplyExact(tickAtOrAfter(upper), dataset.sources());
            String query = StableFingerprint.of(policy.dataType() + "|" + policy.mode() + "|" + start + "|" + end + "|" + dataset);
            long ordinal = token == null || token.isBlank() ? first : readToken(query, token, first, limit);
            List<JsonNode> points = new ArrayList<>(pageSize);
            while (ordinal < limit && points.size() < pageSize) {
                long tick = ordinal / dataset.sources();
                Instant time = dataset.start().plusSeconds(Math.multiplyExact(tick, dataset.sampleInterval().toSeconds()));
                Gap gap = gapAt(time);
                if (gap != null) {
                    ordinal = Math.multiplyExact(tickAtOrAfter(gap.end()), dataset.sources());
                    continue;
                }
                ObjectNode point = sampleTransform.apply(sample(time, tick, (int) (ordinal % dataset.sources())));
                ordinal++;
                if (point != null) points.add(point);
            }
            pages++;
            generatedSamples += points.size();
            return new Page(List.copyOf(points), ordinal < limit ? query + TOKEN_SEPARATOR + ordinal : null);
        } finally {
            readNanos += System.nanoTime() - started;
        }
    }

    @Override
    public Page reconcile(DataTypePolicy.Policy policy, Instant start, Instant end, String token) {
        return listRaw(policy, start, end, token);
    }

    @Override
    public Page dailyRollUp(DataTypePolicy.Policy policy, LocalDate start, LocalDate end, String token) {
        return listRaw(policy, start.atStartOfDay().toInstant(ZoneOffset.UTC), end.atStartOfDay().toInstant(ZoneOffset.UTC), token);
    }

    private void admit(Request request) {
        readsByPurpose.merge(purpose, 1L, Long::sum);
        beforeRead.accept(request);
        SyncBudget.require(responseDelay);
        long deadline = System.nanoTime() + responseDelay.toNanos();
        while (System.nanoTime() < deadline) {
            LockSupport.parkNanos(Math.max(1, deadline - System.nanoTime()));
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Synthetic read cancelled");
        }
    }

    private Page empty(String token) {
        if (token != null && !token.isBlank()) throw invalidToken();
        pages++;
        return new Page(List.of(), null);
    }

    private long tickAtOrAfter(Instant time) {
        if (!time.isAfter(dataset.start())) return 0;
        long seconds = dataset.sampleInterval().toSeconds();
        long tick = Duration.between(dataset.start(), time).getSeconds() / seconds;
        if (dataset.start().plusSeconds(Math.multiplyExact(tick, seconds)).isBefore(time)) tick++;
        return tick;
    }

    private Gap gapAt(Instant time) {
        int low = 0, high = dataset.gaps().size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            Gap gap = dataset.gaps().get(middle);
            if (time.isBefore(gap.start())) high = middle - 1;
            else if (!time.isBefore(gap.end())) low = middle + 1;
            else return gap;
        }
        return null;
    }

    private long readToken(String query, String token, long first, long limit) {
        if (!token.startsWith(query + TOKEN_SEPARATOR)) throw invalidToken();
        try {
            long ordinal = Long.parseLong(token.substring(query.length() + TOKEN_SEPARATOR.length()));
            if (ordinal <= first || ordinal >= limit) throw invalidToken();
            return ordinal;
        } catch (NumberFormatException exception) {
            throw invalidToken();
        }
    }

    private static ApiException invalidToken() {
        return new ApiException("Synthetic page token is invalid for this query", 400, false, ApiException.Reason.INVALID_PAGE_TOKEN);
    }

    private ObjectNode sample(Instant time, long tick, int source) {
        ObjectNode point = json.createObjectNode();
        if (dataset.resourceNames()) point.put("name", "users/synthetic/dataTypes/heart-rate/dataPoints/" + tick + "-" + source);
        ObjectNode dataSource = point.putObject("dataSource");
        dataSource.put("platform", "FITBIT");
        dataSource.put("recordingMethod", "AUTOMATICALLY_RECORDED");
        dataSource.putObject("device").put("displayName", "Synthetic watch " + source);
        var civil = time.atZone(dataset.zone());
        double hour = civil.getHour() + civil.getMinute() / 60.0;
        double activity = hour >= 17 && hour < 18 ? 45 : 0;
        int bpm = (int) Math.round(65 + (hour < 6 ? -8 : 0) + activity + source
                                   + 7 * Math.sin(tick / 37.0 + dataset.seed()) + 3 * Math.sin(tick / 11.0));
        ObjectNode heart = point.putObject("heartRate");
        heart.put("beatsPerMinute", Integer.toString(bpm));
        ObjectNode sampleTime = heart.putObject("sampleTime");
        sampleTime.put("physicalTime", time.toString());
        sampleTime.put("utcOffset", civil.getOffset().getTotalSeconds() + "s");
        ObjectNode civilTime = sampleTime.putObject("civilTime");
        civilTime.putObject("date").put("year", civil.getYear()).put("month", civil.getMonthValue()).put("day", civil.getDayOfMonth());
        civilTime.putObject("time").put("hours", civil.getHour()).put("minutes", civil.getMinute()).put("seconds", civil.getSecond());
        return point;
    }

    @Override
    public void beginCycle() {
        attempts = pages = probes = generatedSamples = readNanos = 0;
        readsByPurpose.clear();
        purpose = "UNSPECIFIED";
    }

    @Override
    public boolean coolingDown() {
        return false;
    }

    @Override
    public void purpose(String value) {
        purpose = Objects.requireNonNull(value);
    }

    @Override
    public void countProbe() {
        probes++;
    }

    @Override
    public Counters counters() {
        // No response bytes are decoded by an object provider.
        return new Counters(attempts, 0, 0, pages, 0, probes, 0);
    }
}
