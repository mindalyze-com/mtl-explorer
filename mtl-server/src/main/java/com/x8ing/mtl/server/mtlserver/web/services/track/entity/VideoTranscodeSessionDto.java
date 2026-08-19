package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({
        "sessionId",
        "mediaId",
        "quality",
        "state",
        "playlistUrl",
        "playlistReady",
        "encodedSeconds",
        "sourceDurationSeconds",
        "transcodeSpeed",
        "bytesWritten",
        "message",
        "reused"
})
public record VideoTranscodeSessionDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long mediaId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        VideoTranscodeQuality quality,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        VideoTranscodeSessionState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String playlistUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean playlistReady,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double encodedSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        double sourceDurationSeconds,
        Double transcodeSpeed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long bytesWritten,
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean reused) {
}
