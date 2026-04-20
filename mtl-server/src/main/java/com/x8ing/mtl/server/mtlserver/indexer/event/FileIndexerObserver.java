package com.x8ing.mtl.server.mtlserver.indexer.event;

/**
 * Observer notified by the FileIndexer after a file was scheduled (row committed) for processing.
 * Only lightweight identifiers are passed – implementers must reload managed entities and perform
 * domain work inside their own @Transactional boundaries.
 */
public interface FileIndexerObserver {

    void onNewFile(String index, long fileId);

    void onDeletedFile(String index, long fileId);

    void onChangedFile(String index, long fileId);

    // New overloads supporting async completion handshake; default to legacy behavior (sync success)
    default void onNewFile(String index, long fileId, OnCompletion completion) {
        onNewFile(index, fileId);
        if (completion != null) completion.success(fileId);
    }

    default void onDeletedFile(String index, long fileId, OnCompletion completion) {
        onDeletedFile(index, fileId);
        if (completion != null) completion.success(fileId);
    }

    default void onChangedFile(String index, long fileId, OnCompletion completion) {
        onChangedFile(index, fileId);
        if (completion != null) completion.success(fileId);
    }
}
