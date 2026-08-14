package com.x8ing.mtl.server.mtlserver.indexer.event;

import org.slf4j.Logger;

/**
 * Completion-aware observer shared by indexers that process create, change,
 * and delete events synchronously on the indexer worker thread.
 */
public final class ProcessingFileIndexerObserver implements FileIndexerObserver {

    @FunctionalInterface
    public interface CreateOrChangeProcessor {
        void process(String index, long fileId, boolean changed);
    }

    @FunctionalInterface
    public interface DeleteProcessor {
        void process(String index, long fileId);
    }

    private final Logger logger;
    private final String sourceLabel;
    private final CreateOrChangeProcessor createOrChangeProcessor;
    private final DeleteProcessor deleteProcessor;

    public ProcessingFileIndexerObserver(
            Logger logger,
            String sourceLabel,
            CreateOrChangeProcessor createOrChangeProcessor,
            DeleteProcessor deleteProcessor
    ) {
        this.logger = logger;
        this.sourceLabel = sourceLabel;
        this.createOrChangeProcessor = createOrChangeProcessor;
        this.deleteProcessor = deleteProcessor;
    }

    @Override
    public void onNewFile(String index, long fileId, OnCompletion completion) {
        process(fileId, completion, "processing failed for new", () -> createOrChangeProcessor.process(index, fileId, false));
    }

    @Override
    public void onDeletedFile(String index, long fileId, OnCompletion completion) {
        process(fileId, completion, "delete failed for", () -> deleteProcessor.process(index, fileId));
    }

    @Override
    public void onChangedFile(String index, long fileId, OnCompletion completion) {
        process(fileId, completion, "processing failed for changed", () -> createOrChangeProcessor.process(index, fileId, true));
    }

    @Override
    public void onNewFile(String index, long fileId) {
    }

    @Override
    public void onDeletedFile(String index, long fileId) {
    }

    @Override
    public void onChangedFile(String index, long fileId) {
    }

    private void process(long fileId, OnCompletion completion, String failureDescription, ThrowingAction action) {
        completion.started(fileId);
        try {
            action.run();
            completion.success(fileId);
        } catch (Throwable throwable) {
            logger.error("{} {} fileId={}: {}", sourceLabel, failureDescription, fileId, throwable.toString(), throwable);
            completion.failed(fileId, safeMessage(throwable));
            if (throwable instanceof Error error) {
                throw error;
            }
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}
