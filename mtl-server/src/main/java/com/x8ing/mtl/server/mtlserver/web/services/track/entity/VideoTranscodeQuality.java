package com.x8ing.mtl.server.mtlserver.web.services.track.entity;

/** Speed-first browser-compatible video profiles. */
public enum VideoTranscodeQuality {
    AUTO(720, 2_500_000, 128_000),
    P480(480, 1_200_000, 96_000),
    P720(720, 2_500_000, 128_000),
    P1080(1080, 5_000_000, 192_000);

    private final int maxHeight;
    private final int videoBitrate;
    private final int audioBitrate;

    VideoTranscodeQuality(int maxHeight, int videoBitrate, int audioBitrate) {
        this.maxHeight = maxHeight;
        this.videoBitrate = videoBitrate;
        this.audioBitrate = audioBitrate;
    }

    public int maxHeight() {
        return maxHeight;
    }

    public int videoBitrate() {
        return videoBitrate;
    }

    public int audioBitrate() {
        return audioBitrate;
    }
}
