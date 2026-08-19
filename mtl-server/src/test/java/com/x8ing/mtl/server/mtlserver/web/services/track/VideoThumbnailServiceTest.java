package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VideoThumbnailServiceTest {

    @Test
    void buildsOneBoundedJpegPosterCommand() {
        Path video = Path.of("synthetic.mp4");

        List<String> command = VideoThumbnailService.buildCommand(video, 320);

        assertThat(command)
                .startsWith("ffmpeg", "-hide_banner", "-loglevel", "error")
                .containsSubsequence("-map", "0:v:0")
                .containsSubsequence("-frames:v", "1")
                .containsSubsequence(
                        "-vf",
                        "scale=w='min(320,iw)':h='min(320,ih)':force_original_aspect_ratio=decrease")
                .containsSubsequence("-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1");
    }
}
