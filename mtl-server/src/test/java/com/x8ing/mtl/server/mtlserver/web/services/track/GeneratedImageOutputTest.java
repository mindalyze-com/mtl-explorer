package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedImageOutputTest {
    private final MediaProcessLimiter limiter = new MediaProcessLimiter(new MediaProcessProperties());

    @Test
    void timesOutAndCleansUpBothTemporaryFiles() {
        ProcessBuilder builder = new ProcessBuilder("sleep", "60");
        var exception = assertThrows(ResponseStatusException.class, () -> GeneratedImageOutput.create(
                builder, limiter, MediaType.IMAGE_JPEG_VALUE, Duration.ofMillis(100)));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
        assertEquals("Image conversion timed out", exception.getReason());
        assertFalse(builder.redirectOutput().file().exists());
        assertFalse(builder.redirectError().file().exists());
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            try (var ignored = limiter.acquire()) {
                // A timed-out converter must release the shared process slot.
            }
        });
    }

    @Test
    void drainsLargeDiagnosticsWithoutDeadlockingOrMixingThemIntoTheImage() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c",
                "head -c 131072 /dev/zero >&2; printf '\\377\\330\\377synthetic\\377\\331'");
        Path output;
        try (var result = GeneratedImageOutput.create(builder, limiter, MediaType.IMAGE_JPEG_VALUE)) {
            output = result.path();
            assertEquals(14, Files.size(output));
            assertFalse(builder.redirectError().file().exists());
        }
        assertFalse(Files.exists(output));
    }

    @Test
    void acceptsPngAndDeletesItsOutputAfterServing() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c",
                "printf '\\211PNG\\r\\n\\032\\nsynthetic\\000\\000\\000\\000IEND\\256B\\140\\202'");
        Path output;
        try (var result = GeneratedImageOutput.create(builder, limiter, MediaType.IMAGE_PNG_VALUE)) {
            output = result.path();
            assertTrue(Files.size(output) > 0);
        }
        assertFalse(Files.exists(output));
    }

    @Test
    void cleansUpWhenTheConverterCannotStart() {
        ProcessBuilder builder = new ProcessBuilder("/no-such-mtl-image-converter");
        assertThrows(java.io.IOException.class,
                () -> GeneratedImageOutput.create(builder, limiter, MediaType.IMAGE_JPEG_VALUE));
        assertFalse(builder.redirectOutput().file().exists());
        assertFalse(builder.redirectError().file().exists());
    }
}
