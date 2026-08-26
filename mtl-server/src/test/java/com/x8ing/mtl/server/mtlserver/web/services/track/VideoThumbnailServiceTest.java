package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoThumbnailServiceTest {

    @Test
    void buildsOneBoundedJpegPosterCommand() {
        Path video = Path.of("synthetic.mp4");

        List<String> command = VideoThumbnailService.buildCommand(video, 320);

        assertThat(command)
                .startsWith("ffmpeg", "-hide_banner", "-loglevel", "error")
                .containsSubsequence("-threads", "1", "-i")
                .containsSubsequence("-filter_threads", "1")
                .containsSubsequence("-map", "0:v:0")
                .containsSubsequence("-frames:v", "1")
                .containsSubsequence(
                        "-vf",
                        "scale=w='min(320,iw)':h='min(320,ih)':force_original_aspect_ratio=decrease")
                .containsSubsequence("-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1");
    }

    @Test
    void rejectsThumbnailWorkWhenTheSharedProcessSlotIsBusy() throws Exception {
        MediaProcessProperties properties = new MediaProcessProperties();
        properties.setAcquireTimeout(Duration.ZERO);
        MediaProcessLimiter limiter = new MediaProcessLimiter(properties);
        VideoThumbnailService service = new VideoThumbnailService(limiter);

        try (MediaProcessLimiter.Permit ignored = limiter.acquire()) {
            assertThatThrownBy(() -> service.createThumbnail(Path.of("synthetic.mp4"), 320))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(error.getReason()).contains("busy");
                    });
        }
    }
}
