package com.x8ing.mtl.server.mtlserver.analysis.segment;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.x8ing.mtl.server.mtlserver.metrics.bucket.XMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentCoreService {
    private static final long MAX_CACHED_POINTS = 250_000;
    private final SegmentAttemptResolver resolver;
    private final Cache<String, SegmentCoreAnalysis> cache = Caffeine.newBuilder()
            .maximumWeight(MAX_CACHED_POINTS)
            .weigher((String key, SegmentCoreAnalysis value) -> Math.max(1, value.sourcePointCount()))
            .expireAfterAccess(Duration.ofMinutes(10)).build();

    public SegmentCoreAnalysis get(String reference, Integer maxBuckets, XMode axis) {
        long started = System.nanoTime();
        var attempt = resolver.resolve(reference);
        log.info("Segment chart projection starting: trackId={} axis={} requestedBuckets={}",
                attempt.identity().trackId(), axis, maxBuckets);
        try {
            var result = SegmentChartSeries.build(attempt, full(attempt), maxBuckets, axis);
            log.info("Segment chart projection completed: trackId={} sourcePoints={} displayPoints={} durationMs={}",
                    attempt.identity().trackId(), result.sourcePointCount(), result.points().size(),
                    SegmentAttemptResolver.elapsedMillis(started));
            return result;
        } catch (RuntimeException exception) {
            log.error("Segment chart projection failed: trackId={} type={}",
                    attempt.identity().trackId(), exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }

    SegmentCoreAnalysis full(SegmentAttemptResolver.ResolvedAttempt attempt) {
        long started = System.nanoTime();
        log.info("Segment core analysis starting: trackId={}", attempt.identity().trackId());
        try {
            var result = cache.get(attempt.identity().reference(), ignored -> SegmentCoreCalculator.calculate(attempt));
            log.info("Segment core analysis completed: trackId={} points={} durationMs={}", attempt.identity().trackId(),
                    result.sourcePointCount(), SegmentAttemptResolver.elapsedMillis(started));
            return result;
        } catch (RuntimeException exception) {
            log.error("Segment core analysis failed: trackId={} type={}", attempt.identity().trackId(),
                    exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }
}
