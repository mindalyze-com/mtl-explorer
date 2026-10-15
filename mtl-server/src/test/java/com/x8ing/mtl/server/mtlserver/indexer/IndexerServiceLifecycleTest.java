package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.gpx.GPXDirectoryWatcherService;
import com.x8ing.mtl.server.mtlserver.gpx.GpxProcessingWorker;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaIndexerService;
import com.x8ing.mtl.server.mtlserver.jobs.media.indexer.MediaProcessingWorker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class IndexerServiceLifecycleTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contextCloseSignalsIndexersBeforeWaitingForTheirDestruction(boolean media) {
        FileIndexerImpl indexer = mock(FileIndexerImpl.class);
        Object service = service(media);
        ReflectionTestUtils.setField(service, "fileIndexerImpl", indexer);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "mtl.gpx-watch-directory", "/synthetic", "mtl.media-watch-directory", "/synthetic")));
            if (media) context.registerBean(MediaIndexerService.class, () -> (MediaIndexerService) service);
            else context.registerBean(GPXDirectoryWatcherService.class, () -> (GPXDirectoryWatcherService) service);
            context.refresh();
            context.publishEvent(new ContextClosedEvent(context));
            verify(indexer).requestStop();
            verify(indexer, never()).shutdown();
            if (media) ((MediaIndexerService) service).findMedia();
            else ((GPXDirectoryWatcherService) service).watchGPXDirectoryNonBlocking();
            assertThat(ReflectionTestUtils.getField(service, "fileIndexerImpl")).isSameAs(indexer);
        }
        verify(indexer).shutdown();
        verify(indexer).requestStop();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contextStopsIndexersBeforeTheirStorageDependency(boolean media) {
        List<String> events = new ArrayList<>();
        FileIndexerImpl indexer = mock(FileIndexerImpl.class);
        doAnswer(invocation -> {
            events.add("indexer stopped");
            return null;
        }).when(indexer).shutdown();
        Object service = service(media);
        ReflectionTestUtils.setField(service, "fileIndexerImpl", indexer);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "mtl.gpx-watch-directory", "/synthetic", "mtl.media-watch-directory", "/synthetic")));
            context.registerBean("storage", Storage.class, () -> new Storage(events),
                    definition -> definition.setDestroyMethodName("close"));
            if (media) context.registerBean("indexerService", MediaIndexerService.class,
                    () -> (MediaIndexerService) service, definition -> definition.setDependsOn("storage"));
            else context.registerBean("indexerService", GPXDirectoryWatcherService.class,
                    () -> (GPXDirectoryWatcherService) service, definition -> definition.setDependsOn("storage"));
            context.refresh();
        }

        assertThat(events).containsExactly("indexer stopped", "storage closed");
        verify(indexer).shutdown();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateApplicationReadyCallbackCannotStartAnIndexerAfterShutdown(boolean media) {
        Object service = service(media);
        if (service instanceof MediaIndexerService indexer) {
            indexer.shutdown();
            assertDoesNotThrow(indexer::findMedia);
            indexer.shutdown();
        } else {
            var indexer = (GPXDirectoryWatcherService) service;
            indexer.shutdown();
            assertDoesNotThrow(indexer::watchGPXDirectoryNonBlocking);
            indexer.shutdown();
        }
        assertThat(ReflectionTestUtils.getField(service, "fileIndexerImpl")).isNull();
    }

    private static Object service(boolean media) {
        IndexerRepository repository = mock(IndexerRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        IndexerStatusService status = mock(IndexerStatusService.class);
        return media
                ? new MediaIndexerService(repository, mock(MediaProcessingWorker.class), transactions, status)
                : new GPXDirectoryWatcherService(mock(GpxProcessingWorker.class), repository, transactions,
                mock(TrackProcessingStatusService.class), status);
    }

    static class Storage {
        private final List<String> events;

        Storage(List<String> events) {
            this.events = events;
        }

        public void close() {
            events.add("storage closed");
        }
    }
}
