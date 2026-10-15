package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Holds a completed preview on disk until its HTTP response has been written.
 */
final class GeneratedImageOutput implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(GeneratedImageOutput.class);
    private static final Duration CONVERSION_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_DIAGNOSTIC_BYTES = 4096;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] JPEG_END = {(byte) 0xff, (byte) 0xd9};
    private static final byte[] PNG_END = {0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82};

    private final Path path;

    private GeneratedImageOutput(Path path) {
        this.path = path;
    }

    static GeneratedImageOutput create(ProcessBuilder builder, MediaProcessLimiter limiter,
                                       String contentType) throws IOException {
        return create(builder, limiter, contentType, CONVERSION_TIMEOUT);
    }

    static GeneratedImageOutput create(ProcessBuilder builder, MediaProcessLimiter limiter,
                                       String contentType, Duration timeout) throws IOException {
        Path output = Files.createTempFile("mtl-image-", ".preview");
        Path diagnostics = null;
        Process process = null;
        boolean completed = false;
        long started = System.nanoTime();
        try {
            diagnostics = Files.createTempFile("mtl-image-", ".stderr");
            builder.redirectOutput(output.toFile()).redirectError(diagnostics.toFile());
            process = limiter.start(builder);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Image conversion timed out");
            }
            if (process.exitValue() != 0 || !isCompleteImage(output, contentType)) {
                log.warn("Image conversion failed: exitCode={}, outputBytes={}, decoder={}",
                        process.exitValue(), Files.size(output), readDiagnostics(diagnostics));
                throw new ConversionFailure(process.exitValue());
            }
            log.info("Image conversion completed: outputBytes={}, durationMs={}",
                    Files.size(output), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            completed = true;
            return new GeneratedImageOutput(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Image conversion interrupted: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Image conversion was interrupted", e);
        } catch (IOException | RuntimeException e) {
            log.warn("Image conversion failed: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("Image conversion failure", e);
            throw e;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            deleteTemporaryFile(diagnostics);
            if (!completed) deleteTemporaryFile(output);
        }
    }

    private static boolean isCompleteImage(Path path, String contentType) throws IOException {
        boolean png = MediaType.IMAGE_PNG_VALUE.equals(contentType);
        byte[] signature = png ? PNG_SIGNATURE : JPEG_SIGNATURE;
        byte[] end = png ? PNG_END : JPEG_END;
        try (var input = Files.newInputStream(path)) {
            long size = Files.size(path);
            if (size < signature.length + end.length) return false;
            if (!Arrays.equals(signature, input.readNBytes(signature.length))) return false;
            input.skipNBytes(size - signature.length - end.length);
            return Arrays.equals(end, input.readNBytes(end.length));
        }
    }

    private static String readDiagnostics(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return new String(input.readNBytes(MAX_DIAGNOSTIC_BYTES), StandardCharsets.UTF_8)
                    .replaceAll("[\\p{Cntrl}]+", " ").trim();
        }
    }

    private static void deleteTemporaryFile(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove temporary image output {}: {}: {}",
                    path.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            log.debug("Temporary image cleanup failure", e);
        }
    }

    Path path() {
        return path;
    }

    static final class ConversionFailure extends ResponseStatusException {
        private final int exitCode;

        private ConversionFailure(int exitCode) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "Image preview could not be generated");
            this.exitCode = exitCode;
        }

        int exitCode() {
            return exitCode;
        }
    }

    @Override
    public void close() {
        deleteTemporaryFile(path);
    }
}
