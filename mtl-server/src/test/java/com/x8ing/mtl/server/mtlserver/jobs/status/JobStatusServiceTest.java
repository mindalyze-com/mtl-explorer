package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.config.ConfigRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackVariantSelector;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeAutoClassifier;
import com.x8ing.mtl.server.mtlserver.jobs.classifier.activitytype.ActivityTypeClassifierJob;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.duplicate.DuplicateDetectorJob;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.exploration.ExplorationScoreJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directTransactions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JobStatusServiceTest {
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final IndexerStatusService indexers = mock(IndexerStatusService.class);
    private final MtlAppProperties properties = new MtlAppProperties();
    private final ResourceIntensiveJobGuard guard = new ResourceIntensiveJobGuard();
    private final ProcessingJobSupport support = new ProcessingJobSupport(indexers, guard, properties);
    private final BackgroundJobRunner runner = new BackgroundJobRunner(mock(TaskScheduler.class), new ShutdownSignal(), support);

    @Test
    void terminalFailuresRemainFinishedAndReportsAreCollectedFromOwners() {
        when(tracks.countGroupedByDuplicateStatus()).thenReturn(List.of(
                new Object[]{GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, 2L},
                new Object[]{GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE, 1L},
                new Object[]{GpsTrack.DUPLICATE_CHECK_STATUS.FAILED, 2L}));
        var duplicate = duplicate();
        var collector = new JobStatusService(List.of(duplicate), support, runner);
        var summary = collector.getJobSummaries().getFirst();
        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.done()).isEqualTo(3);
        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.attentionRequired()).isEqualTo(2);
        assertThat(summary.state()).isEqualTo(ProcessingDetails.State.COMPLETE);
        assertThat(summary.progressPercent()).isEqualTo(100);
        assertThat(collector.getDependencies()).isEqualTo(duplicate.dependencies(support.context(runner)));
    }

    @Test
    void explorationExecutionAndReportingUseTheSameGateAndPollingRechecksIt() {
        var worker = mock(ExplorationScoreAtomicWorker.class);
        var job = new ExplorationScoreJob(tracks, worker, mock(ConfigRepository.class), indexers,
                directTransactions(), new ShutdownSignal());
        when(tracks.countGroupedByExplorationStatus()).thenReturn(java.util.Collections.singletonList(
                new Object[]{GpsTrack.EXPLORATION_STATUS.SCHEDULED, 12L}));
        when(tracks.existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE.IMPORTED,
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)).thenReturn(true);
        var collector = new JobStatusService(List.of(job), support, runner);
        var report = collector.getJobSummaries().getFirst();
        assertThat(report.state()).isEqualTo(ProcessingDetails.State.WAITING);
        assertThat(report.work().waiting()).isEqualTo(12);
        assertThat(job.reportedBatch().blockers()).isEqualTo(report.blockedBy());
        verifyNoInteractions(worker);
        when(tracks.existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE.IMPORTED,
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)).thenReturn(false);
        report = collector.getJobSummaries().getFirst();
        assertThat(report.state()).isEqualTo(ProcessingDetails.State.QUEUED);
        assertThat(report.work().ready()).isEqualTo(12);
        assertThat(report.blockedBy()).isEmpty();
    }

    @Test
    void activityReportsPartialReadinessAndUnavailableOutcomesSeparately() {
        var counts = mock(GpsTrackRepository.ActivityTypeSummaryCounts.class);
        when(counts.getPending()).thenReturn(160L);
        when(counts.getReady()).thenReturn(40L);
        when(counts.getWaiting()).thenReturn(120L);
        when(counts.getDone()).thenReturn(3L);
        when(counts.getFailed()).thenReturn(2L);
        when(counts.getUnavailable()).thenReturn(1L);
        when(tracks.countActivityTypeSummary(anyString())).thenReturn(counts);
        var job = new ActivityTypeClassifierJob(tracks, mock(GpsTrackVariantSelector.class),
                mock(ActivityTypeAutoClassifier.class), mock(SystemLogService.class), indexers, new ShutdownSignal());
        var report = job.processingStatus(support.context(runner));
        assertThat(report.state()).isEqualTo(ProcessingDetails.State.QUEUED);
        assertThat(report.work()).isEqualTo(new ProcessingDetails.Work(40, 120, 0));
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.unavailable()).isEqualTo(1);
        assertThat(report.attentionRequired()).isEqualTo(1);
        when(indexers.hasIndexPendingWork("GPS")).thenReturn(true);
        report = job.processingStatus(support.context(runner));
        assertThat(report.state()).isEqualTo(ProcessingDetails.State.WAITING);
        assertThat(report.work().waiting()).isEqualTo(160);
    }

    @Test
    void demoWaitsForMediaAndUnknownImportTotalsDoNotLookComplete() {
        properties.setDemoMode(true);
        when(indexers.hasIndexPendingWork("MEDIA")).thenReturn(true);
        var report = duplicate().processingStatus(support.context(runner));
        assertThat(report.state()).isEqualTo(ProcessingDetails.State.WAITING);
        assertThat(report.progressKnown()).isFalse();
        assertThat(report.blockedBy()).extracting(ProcessingDetails.Blocker::id).contains("MEDIA");
        when(tracks.countGroupedByDuplicateStatus()).thenReturn(java.util.Collections.singletonList(
                new Object[]{GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, 3L}));
        report = duplicate().processingStatus(support.context(runner));
        assertThat(report.done()).isEqualTo(3);
        assertThat(report.state()).isEqualTo(ProcessingDetails.State.WAITING);
        assertThat(report.progressKnown()).isFalse();
    }

    @Test
    void genericStatePolicyHandlesMixedWorkInOnePlace() {
        assertThat(ProcessingDetails.state(true, true, new ProcessingDetails.Work(0, 5, 2))).isEqualTo(ProcessingDetails.State.RUNNING);
        assertThat(ProcessingDetails.state(true, false, new ProcessingDetails.Work(1, 5, 2))).isEqualTo(ProcessingDetails.State.QUEUED);
        assertThat(ProcessingDetails.state(true, false, new ProcessingDetails.Work(0, 5, 2))).isEqualTo(ProcessingDetails.State.RETRYING);
        assertThat(ProcessingDetails.state(true, false, new ProcessingDetails.Work(0, 5, 0))).isEqualTo(ProcessingDetails.State.WAITING);
        assertThat(ProcessingDetails.state(true, false, new ProcessingDetails.Work(0, 0, 0))).isEqualTo(ProcessingDetails.State.COMPLETE);
    }

    @Test
    void capacityWaitDoesNotSetExecutingAndLockOwnerIsReported() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var job = duplicate();
        Thread owner = new Thread(() -> support.execute(job, runner, () -> {
            BackgroundJobRunner.batchStarted();
            started.countDown();
            try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return ProcessingDetails.BatchOutcome.finished(false);
        }));
        owner.start();
        try {
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(runner.isExecuting("duplicate")).isTrue();
            var classifier = new ActivityTypeClassifierJob(tracks, mock(GpsTrackVariantSelector.class),
                    mock(ActivityTypeAutoClassifier.class), mock(SystemLogService.class), indexers, new ShutdownSignal());
            var result = support.execute(classifier, runner, () -> { throw new AssertionError("capacity is occupied"); });
            assertThat(runner.isExecuting("activityType")).isFalse();
            assertThat(result.blockers()).singleElement().satisfies(blocker -> {
                assertThat(blocker.kind()).isEqualTo(ProcessingDetails.Kind.LOCK);
                assertThat(blocker.heldBy()).isEqualTo("duplicate");
            });
        } finally {
            release.countDown();
            owner.join(5_000);
        }
        assertThat(runner.isExecuting("duplicate")).isFalse();
        assertThat(guard.heldBy()).isNull();
    }

    private DuplicateDetectorJob duplicate() {
        return new DuplicateDetectorJob(tracks, mock(DuplicateDetectorAtomicWorker.class), indexers, new ShutdownSignal());
    }
}
