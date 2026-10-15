package com.x8ing.mtl.server.mtlserver.indexer.event;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingFileIndexerObserverTest {

    @Test
    void changingSourcesRequestReplayInsteadOfReportingAnImportFailure() {
        OnCompletion completion = org.mockito.Mockito.mock(OnCompletion.class);
        var observer = new ProcessingFileIndexerObserver(NOPLogger.NOP_LOGGER, "Media", (index, id, changed) -> {
            throw new com.x8ing.mtl.server.mtlserver.indexer.SourceFileChangedException("synthetic source changed");
        }, (index, id) -> {
        });
        observer.onChangedFile("MEDIA", 1L, completion);
        org.mockito.Mockito.verify(completion).sourceChanged(1L, "synthetic source changed");
        org.mockito.Mockito.verify(completion, org.mockito.Mockito.never()).failed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sourceProblemsWarnOnceButUnexpectedFailuresStillLogErrors() {
        var logger = org.mockito.Mockito.mock(org.slf4j.Logger.class);
        var expected = new com.x8ing.mtl.server.mtlserver.indexer.SourceFileProblemException("Empty media", null);
        var completion = new RecordingCompletion();
        var observer = new ProcessingFileIndexerObserver(logger, "Media", (index, id, changed) -> {
            throw expected;
        },
                (index, id) -> {
                    throw new IllegalStateException("Database unavailable");
                });
        observer.onNewFile("MEDIA", 1L, completion);
        observer.onDeletedFile("MEDIA", 2L, completion);
        org.mockito.Mockito.verify(logger).warn(org.mockito.ArgumentMatchers.contains("file skipped"),
                org.mockito.ArgumentMatchers.eq("Media"), org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("SourceFileProblemException"), org.mockito.ArgumentMatchers.eq("Empty media"));
        org.mockito.Mockito.verify(logger).error(org.mockito.ArgumentMatchers.contains("fileId="),
                org.mockito.ArgumentMatchers.eq("Media"), org.mockito.ArgumentMatchers.eq("delete failed for"),
                org.mockito.ArgumentMatchers.eq(2L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isA(IllegalStateException.class));
        assertEquals(List.of(1L, 2L), completion.failedIds);
    }

    @Test
    void reportsLifecycleForCreateChangeAndDelete() {
        List<String> operations = new ArrayList<>();
        RecordingCompletion completion = new RecordingCompletion();
        ProcessingFileIndexerObserver observer = new ProcessingFileIndexerObserver(
                NOPLogger.NOP_LOGGER,
                "Test",
                (index, fileId, changed) -> operations.add(index + ":" + fileId + ":" + changed),
                (index, fileId) -> operations.add(index + ":" + fileId + ":deleted")
        );

        observer.onNewFile("GPS", 1L, completion);
        observer.onChangedFile("GPS", 2L, completion);
        observer.onDeletedFile("GPS", 3L, completion);

        assertEquals(List.of("GPS:1:false", "GPS:2:true", "GPS:3:deleted"), operations);
        assertEquals(List.of(1L, 2L, 3L), completion.startedIds);
        assertEquals(List.of(1L, 2L, 3L), completion.successIds);
    }

    @Test
    void reportsAUsefulFailureMessage() {
        RecordingCompletion completion = new RecordingCompletion();
        ProcessingFileIndexerObserver observer = new ProcessingFileIndexerObserver(
                NOPLogger.NOP_LOGGER,
                "Test",
                (index, fileId, changed) -> {
                    throw new IllegalStateException();
                },
                (index, fileId) -> {
                }
        );

        observer.onNewFile("GPS", 4L, completion);

        assertEquals(List.of(4L), completion.failedIds);
        assertEquals(List.of("IllegalStateException"), completion.failureReasons);
    }

    @Test
    void reportsAndRethrowsErrors() {
        RecordingCompletion completion = new RecordingCompletion();
        AtomicBoolean invoked = new AtomicBoolean();
        ProcessingFileIndexerObserver observer = new ProcessingFileIndexerObserver(
                NOPLogger.NOP_LOGGER,
                "Test",
                (index, fileId, changed) -> {
                    invoked.set(true);
                    throw new AssertionError("fatal");
                },
                (index, fileId) -> {
                }
        );

        assertThrows(AssertionError.class, () -> observer.onChangedFile("GPS", 5L, completion));
        assertTrue(invoked.get());
        assertEquals(List.of(5L), completion.failedIds);
        assertEquals(List.of("fatal"), completion.failureReasons);
    }

    private static final class RecordingCompletion implements OnCompletion {
        private final List<Long> startedIds = new ArrayList<>();
        private final List<Long> successIds = new ArrayList<>();
        private final List<Long> failedIds = new ArrayList<>();
        private final List<String> failureReasons = new ArrayList<>();

        @Override
        public void started(long fileId) {
            startedIds.add(fileId);
        }

        @Override
        public void success(long fileId) {
            successIds.add(fileId);
        }

        @Override
        public void failed(long fileId, String reason) {
            failedIds.add(fileId);
            failureReasons.add(reason);
        }
    }
}
