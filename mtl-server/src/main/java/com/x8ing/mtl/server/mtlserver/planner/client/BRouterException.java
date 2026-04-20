package com.x8ing.mtl.server.mtlserver.planner.client;

/**
 * Raised by {@link BRouterClient} for any BRouter-side failure (HTTP, timeout, parse).
 */
public class BRouterException extends RuntimeException {
    public BRouterException(String message) {
        super(message);
    }

    public BRouterException(String message, Throwable cause) {
        super(message, cause);
    }
}
