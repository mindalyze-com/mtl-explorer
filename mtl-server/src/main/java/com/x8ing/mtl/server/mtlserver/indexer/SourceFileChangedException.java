package com.x8ing.mtl.server.mtlserver.indexer;

/**
 * The transaction must roll back and import the latest source revision instead.
 */
public class SourceFileChangedException extends RuntimeException {
    public SourceFileChangedException(String message) {
        super(message);
    }
}
