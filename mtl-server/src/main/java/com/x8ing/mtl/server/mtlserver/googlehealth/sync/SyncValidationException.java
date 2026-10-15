package com.x8ing.mtl.server.mtlserver.googlehealth.sync;

/**
 * Validation reasons contain no provider values, resource names or query bounds.
 */
public final class SyncValidationException extends IllegalArgumentException {

    public enum Reason {
        MISSING_PAYLOAD("Google Health point is missing its expected payload"),
        MISSING_REQUIRED_FIELD("Google Health field is missing"),
        MISSING_QUERY_TIME("Google Health point has no time value for its query boundary"),
        MISSING_START_TIME("Google Health point has no usable start time"),
        INVALID_HEART_RATE("Google Health heart rate BPM is invalid"),
        NON_POSITIVE_HEART_RATE("Google Health heart rate BPM must be positive"),
        INVALID_CIVIL_TIME("Google Health civil time is invalid"),
        INVALID_UTC_OFFSET("Google Health UTC offset is invalid; supported offsets are UTC-12 through UTC+14"),
        INVALID_CIVIL_DATE("Google Health civil date is invalid"),
        INVALID_TIMESTAMP("Google Health timestamp is invalid"),
        QUERY_RANGE_MISMATCH("Google Health point lies outside its query range"),
        DUPLICATE_QUERY_IDENTITY("Conflicting duplicate identity in one query"),
        DUPLICATE_TRAVERSAL_IDENTITY("Conflicting duplicate identity in one traversal"),
        CHUNK_BYTE_LIMIT("Recent chunk exceeds byte limit"),
        CHUNK_ROW_LIMIT("Recent chunk exceeds row limit"),
        CHUNK_PAGE_LIMIT("Recent chunk exceeds page limit"),
        HISTORY_PAGE_LIMIT("Historical page limit exceeded"),
        HISTORY_BYTE_LIMIT("Historical page exceeds byte limit"),
        HISTORY_ROW_LIMIT("Historical page exceeds row limit"),
        HISTORY_VALIDATION_LIMIT("Historical query validation exceeds work-state limit"),
        REPEATED_PAGE_TOKEN("Repeated pagination token"),
        PAGE_CHECKPOINT_REJECTED("Repeated pagination token or revoked traversal");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Reason reason;

    public SyncValidationException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public SyncValidationException(Reason reason, Throwable cause) {
        super(reason.message(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
