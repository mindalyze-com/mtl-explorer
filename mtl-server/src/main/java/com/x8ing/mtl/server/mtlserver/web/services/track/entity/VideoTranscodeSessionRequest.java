package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"quality"})
public record VideoTranscodeSessionRequest(VideoTranscodeQuality quality) {

    public VideoTranscodeQuality effectiveQuality() {
        return quality == null ? VideoTranscodeQuality.AUTO : quality;
    }
}
