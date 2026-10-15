package com.x8ing.mtl.server.mtlserver.planner.client;

/**
 * Raised by {@link BRouterClient} for any BRouter-side failure (HTTP, timeout, parse).
 */
public class BRouterException extends RuntimeException {
    public static final String ROUTING_UNAVAILABLE = "routing-unavailable";
    public static final String ROUTING_FAILED = "routing-failed";
    public static final String ROUTING_STORAGE_UNAVAILABLE = "routing-storage-unavailable";
    public static final String ROUTING_TIMEOUT = "routing-timeout";

    private final String errorCode;

    public BRouterException(String message) {
        this(ROUTING_UNAVAILABLE, message, null);
    }

    public BRouterException(String message, Throwable cause) {
        this(ROUTING_UNAVAILABLE, message, cause);
    }

    public BRouterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
