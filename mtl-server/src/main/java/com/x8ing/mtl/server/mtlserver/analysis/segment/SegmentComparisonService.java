package com.x8ing.mtl.server.mtlserver.analysis.segment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SegmentComparisonService {
    private final SegmentAttemptResolver resolver;
    private final SegmentCoreService core;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SegmentComparison get(String attemptReference, String referenceAttemptReference) {
        long started = System.nanoTime();
        log.info("Segment comparison starting: method={}", SegmentComparisonCalculator.METHOD);
        try {
            var attempt = core.full(resolver.resolve(attemptReference));
            var reference = core.full(resolver.resolve(referenceAttemptReference));
            var result = SegmentComparisonCalculator.compare(attempt, reference);
            log.info("Segment comparison completed: trackId={} referenceTrackId={} points={} ghostPoints={} durationMs={}",
                    attempt.identity().trackId(), reference.identity().trackId(), result.points().size(), result.ghostPoints().size(),
                    SegmentAttemptResolver.elapsedMillis(started));
            return result;
        } catch (RuntimeException exception) {
            log.error("Segment comparison failed: type={}", exception.getClass().getSimpleName(), exception);
            throw exception;
        }
    }
}
