package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Extracts one bounded JPEG poster frame for browser video thumbnails. */
@Service
public class VideoThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(VideoThumbnailService.class);

    static final String FFMPEG_COMMAND = "ffmpeg";
    static final int THUMBNAIL_TIMEOUT_SECONDS = 30;
    static final String JPEG_QUALITY = "3";
    static final String FFMPEG_THREAD_COUNT = "1";

    private final MediaProcessLimiter mediaProcessLimiter;

    public VideoThumbnailService(MediaProcessLimiter mediaProcessLimiter) {
        this.mediaProcessLimiter = mediaProcessLimiter;
    }

    public byte[] createThumbnail(Path mediaPath, int maxSize) {
        Process process;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(mediaPath, maxSize));
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = mediaProcessLimiter.start(processBuilder);
        } catch (MediaProcessLimiter.MediaProcessBusyException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Media processing is busy. Try again shortly.",
                    e);
        } catch (IOException e) {
            log.error("Could not start ffmpeg for video thumbnail {}", mediaPath.getFileName(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Video thumbnail generator is unavailable",
                    e);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var outputFuture = executor.submit(() -> {
                try (var input = process.getInputStream()) {
                    return input.readAllBytes();
                }
            });

            if (!process.waitFor(THUMBNAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Video thumbnail generation timed out");
            }

            byte[] output = outputFuture.get();
            if (process.exitValue() != 0 || !isJpeg(output)) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Video thumbnail could not be generated");
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Video thumbnail generation was interrupted",
                    e);
        } catch (ExecutionException e) {
            process.destroyForcibly();
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Video thumbnail could not be read",
                    e.getCause());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    static List<String> buildCommand(Path mediaPath, int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize must be positive");

        String scaleFilter = "scale=w='min(" + maxSize + ",iw)':h='min(" + maxSize
                             + ",ih)':force_original_aspect_ratio=decrease";
        return List.of(
                FFMPEG_COMMAND,
                "-hide_banner",
                "-loglevel", "error",
                "-nostdin",
                "-threads", FFMPEG_THREAD_COUNT,
                "-i", mediaPath.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-frames:v", "1",
                "-filter_threads", FFMPEG_THREAD_COUNT,
                "-vf", scaleFilter,
                "-an",
                "-q:v", JPEG_QUALITY,
                "-f", "image2pipe",
                "-threads", FFMPEG_THREAD_COUNT,
                "-vcodec", "mjpeg",
                "pipe:1");
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4
               && (bytes[0] & 0xff) == 0xff
               && (bytes[1] & 0xff) == 0xd8
               && (bytes[bytes.length - 2] & 0xff) == 0xff
               && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }
}
