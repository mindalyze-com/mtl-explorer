package com.x8ing.mtl.server.mtlserver.indexer;

/**
 * A recognized source-content problem. The file is skipped; processing continues.
 */
public final class SourceFileProblemException extends IllegalStateException {
    public SourceFileProblemException(String message, Throwable cause) {
        super(message, cause);
    }
}
