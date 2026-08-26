package com.x8ing.mtl.server.mtlserver.web.services.track;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Configures the shared limit for ImageMagick and FFmpeg-family child processes. */
@Data
@Component
@ConfigurationProperties(prefix = "mtl.media-process")
public class MediaProcessProperties {

    private int maxConcurrentProcesses = 1;
    private Duration acquireTimeout = Duration.ofMillis(250);

    void validate() {
        if (maxConcurrentProcesses < 1) {
            throw new IllegalStateException("media-process.max-concurrent-processes must be positive");
        }
        if (acquireTimeout == null || acquireTimeout.isNegative()) {
            throw new IllegalStateException("media-process.acquire-timeout must not be negative");
        }
    }
}
