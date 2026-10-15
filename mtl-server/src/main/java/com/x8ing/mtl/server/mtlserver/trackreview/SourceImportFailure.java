package com.x8ing.mtl.server.mtlserver.trackreview;

/**
 * Published before rollback; assessment persistence runs only after the failed transaction releases its locks.
 */
public record SourceImportFailure(long sourceId, String sourceHash, String failureType, String detail) {
}
