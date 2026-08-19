package com.x8ing.mtl.server.mtlserver.web.services.track;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Limits concurrent ImageMagick child processes started by media requests. */
@Data
@Component
@ConfigurationProperties(prefix = "mtl.image-magick")
public class ImageMagickProperties {

    private int maxConcurrentProcesses = 2;

    void validate() {
        if (maxConcurrentProcesses < 1) {
            throw new IllegalStateException("image-magick.max-concurrent-processes must be positive");
        }
    }
}
