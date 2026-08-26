package com.x8ing.mtl.server.mtlserver.web.services.track;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/** Hard limits for temporary browser-compatible video streams. */
@Data
@Component
@ConfigurationProperties(prefix = "mtl.video-transcode")
public class VideoTranscodeProperties {

    private static final long GIBIBYTE = 1024L * 1024L * 1024L;

    private boolean enabled = true;
    private String ffmpegCommand = "ffmpeg";
    private String ffprobeCommand = "ffprobe";
    private Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), "mtl-explorer-video-transcodes");
    private int maxActiveSessions = 1;
    private int maxCompletedSessions = 1;
    private int maxSessions = 4;
    private long maxSessionBytes = 3L * GIBIBYTE;
    private long maxTotalBytes = 4L * GIBIBYTE;
    private Duration disconnectGrace = Duration.ofMinutes(5);
    private Duration completedExpiry = Duration.ofMinutes(30);
    private Duration maxRuntime = Duration.ofHours(6);
    private Duration probeTimeout = Duration.ofSeconds(20);
    private Duration monitorInterval = Duration.ofSeconds(1);
    private int segmentSeconds = 4;
    private int maxThreads = 1;

    void validate() {
        if (tempDirectory == null) throw new IllegalStateException("Video transcode temp directory is required");
        if (maxActiveSessions < 1) throw new IllegalStateException("max-active-sessions must be positive");
        if (maxCompletedSessions < 0) throw new IllegalStateException("max-completed-sessions cannot be negative");
        if (maxSessions < maxActiveSessions + maxCompletedSessions) {
            throw new IllegalStateException("max-sessions must cover active and completed sessions");
        }
        if (maxSessionBytes < 1 || maxTotalBytes < maxSessionBytes) {
            throw new IllegalStateException("Video transcode byte limits are invalid");
        }
        requirePositive(disconnectGrace, "disconnect-grace");
        requirePositive(completedExpiry, "completed-expiry");
        requirePositive(maxRuntime, "max-runtime");
        requirePositive(probeTimeout, "probe-timeout");
        requirePositive(monitorInterval, "monitor-interval");
        if (segmentSeconds < 1) throw new IllegalStateException("segment-seconds must be positive");
        if (maxThreads < 1) throw new IllegalStateException("max-threads must be positive");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be positive");
        }
    }
}
