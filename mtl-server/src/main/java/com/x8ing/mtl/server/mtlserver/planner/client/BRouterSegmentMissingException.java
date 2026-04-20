package com.x8ing.mtl.server.mtlserver.planner.client;

/**
 * Thrown when BRouter reports a missing {@code .rd5} segment data file.
 * The controller catches this specifically to trigger an on-demand prewarm
 * and return a user-friendly 503 instead of a raw 502.
 */
public class BRouterSegmentMissingException extends BRouterException {

    private final double fromLat;
    private final double fromLng;
    private final double toLat;
    private final double toLng;

    public BRouterSegmentMissingException(String message, double fromLat, double fromLng,
                                          double toLat, double toLng) {
        super(message);
        this.fromLat = fromLat;
        this.fromLng = fromLng;
        this.toLat = toLat;
        this.toLng = toLng;
    }

    public double getFromLat() {
        return fromLat;
    }

    public double getFromLng() {
        return fromLng;
    }

    public double getToLat() {
        return toLat;
    }

    public double getToLng() {
        return toLng;
    }

    /**
     * Bounding box covering both endpoints (useful for triggering prewarm).
     */
    public double getMinLat() {
        return Math.min(fromLat, toLat);
    }

    public double getMaxLat() {
        return Math.max(fromLat, toLat);
    }

    public double getMinLng() {
        return Math.min(fromLng, toLng);
    }

    public double getMaxLng() {
        return Math.max(fromLng, toLng);
    }
}
