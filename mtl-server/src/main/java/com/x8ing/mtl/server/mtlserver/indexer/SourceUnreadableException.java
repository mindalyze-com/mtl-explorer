package com.x8ing.mtl.server.mtlserver.indexer;

import java.io.IOException;

/** A source access failure, distinct from a service or database failure. */
public final class SourceUnreadableException extends RuntimeException {
    public SourceUnreadableException(IOException cause) {
        super("The source file could not be read. Check its location and access permissions.", cause);
    }
}
