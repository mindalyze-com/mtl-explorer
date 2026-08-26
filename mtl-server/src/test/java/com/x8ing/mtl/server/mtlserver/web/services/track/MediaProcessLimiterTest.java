package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MediaProcessLimiterTest {

    private static final long TEST_TIMEOUT_SECONDS = 5L;

    @Test
    void limitsConcurrentOperationsToTheConfiguredProcessCount() throws Exception {
        MediaProcessLimiter limiter = limiter(2);

        try (MediaProcessLimiter.Permit first = limiter.acquire();
             MediaProcessLimiter.Permit second = limiter.acquire()) {
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertBusyWithoutBlocking(limiter);
        }

        try (MediaProcessLimiter.Permit availableAgain = limiter.acquire()) {
            assertThat(availableAgain).isNotNull();
        }
    }

    @Test
    void releasesTheProcessSlotWhenProcessStartupFails() throws Exception {
        MediaProcessLimiter limiter = limiter(1);
        Path missingCommand = Path.of(System.getProperty("java.io.tmpdir"), "missing-" + UUID.randomUUID());

        assertThatThrownBy(() -> limiter.start(new ProcessBuilder(missingCommand.toString())))
                .isInstanceOf(IOException.class);

        try (MediaProcessLimiter.Permit ignored = limiter.acquire()) {
            assertThat(ignored).isNotNull();
        }
    }

    @Test
    void holdsTheProcessSlotUntilTheStartedProcessExits() throws Exception {
        MediaProcessLimiter limiter = limiter(1);
        Process process = limiter.start(new ProcessBuilder("sh", "-c", "sleep 30"));

        try {
            assertBusyWithoutBlocking(limiter);

            process.destroyForcibly();
            assertThat(process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            try (MediaProcessLimiter.Permit acquired = limiter.acquire()) {
                assertThat(acquired).isNotNull();
            }
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void releasesEachPermitOnlyOnce() throws Exception {
        MediaProcessLimiter limiter = limiter(1);
        MediaProcessLimiter.Permit permit = limiter.acquire();

        permit.close();
        permit.close();

        try (MediaProcessLimiter.Permit first = limiter.acquire()) {
            assertBusyWithoutBlocking(limiter);
        }
    }

    @Test
    void rejectsANonPositiveProcessLimit() {
        MediaProcessProperties properties = new MediaProcessProperties();
        properties.setMaxConcurrentProcesses(0);

        assertThatIllegalStateException()
                .isThrownBy(() -> new MediaProcessLimiter(properties))
                .withMessageContaining("max-concurrent-processes");
    }

    @Test
    void rejectsANegativeAcquireTimeout() {
        MediaProcessProperties properties = new MediaProcessProperties();
        properties.setAcquireTimeout(Duration.ofMillis(-1));

        assertThatIllegalStateException()
                .isThrownBy(() -> new MediaProcessLimiter(properties))
                .withMessageContaining("acquire-timeout");
    }

    private static MediaProcessLimiter limiter(int maximumProcesses) {
        MediaProcessProperties properties = new MediaProcessProperties();
        properties.setMaxConcurrentProcesses(maximumProcesses);
        properties.setAcquireTimeout(Duration.ofMillis(50));
        return new MediaProcessLimiter(properties);
    }

    private static void assertBusyWithoutBlocking(MediaProcessLimiter limiter) {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThatThrownBy(limiter::acquire)
                        .isInstanceOf(MediaProcessLimiter.MediaProcessBusyException.class));
    }
}
