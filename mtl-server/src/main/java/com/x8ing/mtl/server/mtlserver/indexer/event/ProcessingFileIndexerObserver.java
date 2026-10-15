package com.x8ing.mtl.server.mtlserver.indexer.event;

import com.x8ing.mtl.server.mtlserver.indexer.SourceFileChangedException;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileProblemException;
import com.x8ing.mtl.server.mtlserver.indexer.SourceFileSkippedException;
import org.slf4j.Logger;
import com.x8ing.mtl.server.mtlserver.indexer.SourceUnreadableException;

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

    private final java.util.function.BiConsumer<Long, String> unavailableProcessor;
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
        this(logger, sourceLabel, createOrChangeProcessor, deleteProcessor, (id, message) -> {});
    }

    public ProcessingFileIndexerObserver(Logger logger, String sourceLabel, CreateOrChangeProcessor createOrChangeProcessor,
                                         DeleteProcessor deleteProcessor, java.util.function.BiConsumer<Long, String> unavailableProcessor) {
        this.unavailableProcessor = unavailableProcessor;
        this.logger = logger;
        this.sourceLabel = sourceLabel;
        this.createOrChangeProcessor = createOrChangeProcessor;
        this.deleteProcessor = deleteProcessor;
    }

    @Override
    public void onSourceUnavailable(String index, long fileId, String message) {
        unavailableProcessor.accept(fileId, message);
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
            if (throwable instanceof SourceFileSkippedException) {
                logger.info("{} informational skip: fileId={}, reason={}", sourceLabel, fileId, safeMessage(throwable));
                completion.skipped(fileId, safeMessage(throwable));
                return;
            }
            if (throwable instanceof SourceFileChangedException) {
                logger.warn("{} source changed: fileId={}, reason={}; re-queuing current revision", sourceLabel, fileId, safeMessage(throwable));
                completion.sourceChanged(fileId, safeMessage(throwable));
                return;
            }
            if (throwable instanceof SourceFileProblemException || throwable instanceof SourceUnreadableException) {
                logger.warn("{} file skipped: fileId={}, type={}, reason={}", sourceLabel, fileId,
                        throwable.getClass().getSimpleName(), safeMessage(throwable));
                logger.debug("Source file diagnostic details: fileId={}", fileId, throwable);
            } else {
                logger.error("{} {} fileId={}: {}", sourceLabel, failureDescription, fileId, throwable.toString(), throwable);
            }
            if (throwable instanceof SourceUnreadableException) unavailableProcessor.accept(fileId, safeMessage(throwable));
            if (throwable instanceof SourceFileProblemException || throwable instanceof SourceUnreadableException)
                completion.sourceFailed(fileId, safeMessage(throwable));
            else completion.failed(fileId, safeMessage(throwable));
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
