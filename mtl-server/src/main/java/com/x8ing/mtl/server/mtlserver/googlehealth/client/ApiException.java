package com.x8ing.mtl.server.mtlserver.googlehealth.client;

public class ApiException extends RuntimeException {

    private final int statusCode;
    private final boolean retriable;
    private final Reason reason;

    public ApiException(String message, int statusCode, boolean retriable) {
        this(message, statusCode, retriable, Reason.OTHER);
    }

    public ApiException(String message, int statusCode, boolean retriable, Reason reason) {
        super(message);
        this.statusCode = statusCode;
        this.retriable = retriable;
        this.reason = reason;
    }

    public ApiException(String message, int statusCode, boolean retriable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retriable = retriable;
        this.reason = Reason.TRANSPORT;
    }

    public ApiException(String message,
                        int statusCode,
                        boolean retriable,
                        Reason reason,
                        Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retriable = retriable;
        this.reason = reason;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetriable() {
        return retriable;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_REQUEST,
        INVALID_PAGE_TOKEN,
        AUTHORIZATION,
        MISSING_SCOPE,
        POLICY_DENIED,
        SERVICE_DISABLED,
        RATE_LIMIT,
        SERVER,
        TRANSPORT,
        INVALID_RESPONSE,
        OTHER
    }
}
