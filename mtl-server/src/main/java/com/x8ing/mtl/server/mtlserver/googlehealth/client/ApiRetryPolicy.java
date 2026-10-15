package com.x8ing.mtl.server.mtlserver.googlehealth.client;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class ApiRetryPolicy {

    private static final Duration BASE_DELAY = Duration.ofMillis(500);
    private static final Duration MAX_DELAY = Duration.ofSeconds(8);
    private static final int SERVER_ERROR_STATUS = 500;

    public static boolean isRetriableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= SERVER_ERROR_STATUS;
    }

    public static Duration delayForAttempt(int attempt, Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            return retryAfter;
        }
        long exponential = Math.min(MAX_DELAY.toMillis(), BASE_DELAY.toMillis() * (1L << Math.min(attempt, 8)));
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, exponential / 3L));
        return Duration.ofMillis(Math.min(MAX_DELAY.toMillis(), exponential + jitter));
    }

    private ApiRetryPolicy() {
    }
}
