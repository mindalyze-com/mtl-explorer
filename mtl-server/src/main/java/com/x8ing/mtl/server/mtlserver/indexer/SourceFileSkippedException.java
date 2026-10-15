package com.x8ing.mtl.server.mtlserver.indexer;

/** A stable empty source is an informational outcome, separate from pattern exclusion. */
public final class SourceFileSkippedException extends RuntimeException {
    public SourceFileSkippedException() {
        super("Empty file skipped. Any earlier imported data is retained. It will be processed when its contents change.");
    }
}
