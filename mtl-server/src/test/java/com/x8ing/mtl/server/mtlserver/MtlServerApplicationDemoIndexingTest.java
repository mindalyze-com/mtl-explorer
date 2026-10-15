package com.x8ing.mtl.server.mtlserver;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreJob;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.sqlformat.LiquibaseIndentFixerJob;
import org.junit.jupiter.api.Test;

import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingJobSupport;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreAtomicWorker;
import org.springframework.scheduling.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static org.mockito.Mockito.*;

class MtlServerApplicationDemoIndexingTest {
    private final MtlAppProperties properties = mock(MtlAppProperties.class);
    private final IndexerStatusService indexing = mock(IndexerStatusService.class);
    private final ShutdownSignal signal = new ShutdownSignal();
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final DuplicateDetectorJob duplicates = spy(new DuplicateDetectorJob(tracks,
            mock(DuplicateDetectorAtomicWorker.class), indexing, signal));
    private final ActivityTypeClassifierJob classifier = spy(new ActivityTypeClassifierJob(tracks,
            mock(GpsTrackVariantSelector.class), mock(ActivityTypeAutoClassifier.class), mock(SystemLogService.class), indexing, signal));
    private final ExplorationScoreJob exploration = spy(new ExplorationScoreJob(tracks,
            mock(ExplorationScoreAtomicWorker.class), mock(ConfigRepository.class), indexing, directTransactions(), signal));
    private final BackgroundJobRunner runner = new BackgroundJobRunner(mock(TaskScheduler.class), signal,
            new ProcessingJobSupport(indexing, new ResourceIntensiveJobGuard(), properties));
    private final MtlServerApplication application = new MtlServerApplication(
            duplicates, classifier, mock(LiquibaseIndentFixerJob.class), mock(GarminExporter.class), exploration, signal, runner);

    @BeforeEach
    void batches() {
        doReturn(ProcessingDetails.BatchOutcome.finished(false)).when(duplicates).reportedBatch();
        doReturn(ProcessingDetails.BatchOutcome.finished(false)).when(classifier).reportedBatch();
        doReturn(ProcessingDetails.BatchOutcome.finished(false)).when(exploration).reportedBatch();
    }

    @Test
    void shutdownStopsImmediateExplorationRerunsAndNewScheduledJobs() {
        doAnswer(call -> {
            signal.onContextClosed();
            return ProcessingDetails.BatchOutcome.finished(true);
        }).when(exploration).reportedBatch();
        application.scheduleExplorationScoreJob();
        runJobs();
        verify(exploration).reportedBatch();
        verify(duplicates, never()).reportedBatch();
        verify(classifier, never()).reportedBatch();
    }

    @Test
    void demoDefersAllThreeJobsUntilIndexingSettles() {
        when(properties.isDemoMode()).thenReturn(true);
        when(indexing.isDemoIndexingBusy()).thenReturn(true);
        runJobs();
        verify(duplicates, never()).reportedBatch();
        verify(classifier, never()).reportedBatch();
        verify(exploration, never()).reportedBatch();
        when(indexing.isDemoIndexingBusy()).thenReturn(false);
        runJobs();
        verify(duplicates).reportedBatch();
        verify(classifier).reportedBatch();
        verify(exploration).reportedBatch();
    }

    @Test
    void nonDemoDoesNotConsultDemoIndexingGuard() {
        runJobs();
        verify(indexing, never()).isDemoIndexingBusy();
        verify(duplicates).reportedBatch();
        verify(classifier).reportedBatch();
        verify(exploration).reportedBatch();
    }

    private void runJobs() {
        application.findDuplicates();
        application.scheduleJobClassifyActivityType();
        application.scheduleExplorationScoreJob();
    }
}
