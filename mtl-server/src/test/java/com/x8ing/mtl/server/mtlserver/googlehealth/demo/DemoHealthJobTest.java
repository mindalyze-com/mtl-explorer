package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import com.x8ing.mtl.server.mtlserver.config.ShutdownSignal;
import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import com.x8ing.mtl.server.mtlserver.googlehealth.application.GoogleHealthMirrorCache;
import com.x8ing.mtl.server.mtlserver.indexer.IndexerStatusService;
import com.x8ing.mtl.server.mtlserver.jobs.ResourceIntensiveJobGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.x8ing.mtl.server.mtlserver.testsupport.BackgroundJobTestSupport.directRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class DemoHealthJobTest {
    private final DemoHealthAccess access = mock(DemoHealthAccess.class);
    private final IndexerStatusService indexers = mock(IndexerStatusService.class);
    private final DemoHealthRepository repository = mock(DemoHealthRepository.class);
    private final DemoHealthWorker worker = mock(DemoHealthWorker.class);
    private final GoogleHealthMirrorCache cache = mock(GoogleHealthMirrorCache.class);
    private final DemoHealthShowcase showcase = mock(DemoHealthShowcase.class);
    private final GpsTrackRepository tracks = mock(GpsTrackRepository.class);
    private final ShutdownSignal signal = new ShutdownSignal();
    private final DemoHealthJob job = newJob();

    private DemoHealthJob newJob() {
        return new DemoHealthJob(access, new DemoHealthProperties(), indexers,
                new ResourceIntensiveJobGuard(), repository, worker, cache, showcase, tracks, signal, directRunner());
    }

    @Test
    void shutdownFinishesTheCurrentDayAndClearsItsCacheWithoutStartingAnotherDay() {
        when(access.enabled()).thenReturn(true);
        LocalDate today = LocalDate.now(ZoneOffset.UTC), first = today.minusDays(2), second = today.minusDays(1);
        when(repository.firstDay(any())).thenReturn(first);
        when(repository.missingDays(first, second)).thenReturn(List.of(first, second));
        when(worker.day(first, today)).thenAnswer(call -> {
            signal.onContextClosed();
            return true;
        });
        job.scheduled();
        job.scheduled();
        verify(worker).day(first, today);
        verify(worker, never()).day(second, today);
        verify(cache).clear();
        verify(repository).missingDays(first, second);
    }

    @Test
    void doesNoWorkWhenDisabledAndLogsWaitingBeforeLoadingData(CapturedOutput output) {
        job.scheduled();
        verifyNoInteractions(indexers, repository, worker, showcase);
        when(access.enabled()).thenReturn(true);
        when(indexers.isDemoIndexingBusy()).thenReturn(true);
        job.scheduled();
        job.scheduled();
        assertThat(job.summary().progressPercent()).isZero();
        assertThat(job.summary().label()).contains("waiting");
        verifyNoInteractions(repository, worker, showcase);
        assertThat(output.getOut()).contains("INFO", "Synthetic demo health startup completion starting", "waiting for GPS and media indexing");
        assertThat(output.getOut().split("startup completion starting", -1)).hasSize(2);
        assertThat(output.getOut().split("waiting for GPS and media indexing", -1)).hasSize(2);
    }

    @Test
    void retryChecksStoredRecordsAgainAndSkipsTheCompletedDay(CapturedOutput output) {
        when(access.enabled()).thenReturn(true);
        LocalDate today = LocalDate.now(ZoneOffset.UTC), first = today.minusDays(2), second = today.minusDays(1);
        when(repository.firstDay(any())).thenReturn(first);
        when(repository.missingDayCount(first, second)).thenReturn(2L, 0L);
        when(repository.missingDays(first, second)).thenReturn(List.of(first, second), List.of(second));
        when(worker.day(first, today)).thenReturn(true);
        when(worker.day(second, today)).thenThrow(new IllegalStateException("Transient test failure")).thenReturn(true);
        job.scheduled();
        assertThat(job.summary().label()).contains("retrying");
        assertThat(job.summary().progressPercent()).isLessThan(100);
        ReflectionTestUtils.setField(job, "nextCheck", Instant.MIN);
        job.scheduled();
        verify(worker, times(1)).day(first, today);
        verify(worker, times(2)).day(second, today);
        assertThat(job.summary().pending()).isZero();
        assertThat(job.summary().progressPercent()).isEqualTo(100);
        job.scheduled();
        verify(repository, times(2)).missingDays(first, second);
        assertThat(output.getOut()).contains("startup check completed", "incompleteDays=2", "generation batch starting",
                "generation batch completed", "startup completion finished", "completedDays=2", "generation failed");
    }

    @Test
    void everyStartupChecksTheDatabaseAndLeavesCompleteDaysAlone(CapturedOutput output) {
        when(access.enabled()).thenReturn(true);
        LocalDate first = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        when(repository.firstDay(any())).thenReturn(first);
        when(repository.missingDays(first, first)).thenReturn(List.of());
        job.scheduled();
        job.scheduled();
        verify(repository).missingDays(first, first);
        assertThat(job.summary().progressPercent()).isEqualTo(100);
        newJob().scheduled();
        verify(repository, times(2)).missingDays(first, first);
        verifyNoInteractions(worker, cache);
        assertThat(output.getOut()).contains("incompleteDays=0", "completedDays=0, alreadyCompleteDays=1");
    }

    @Test
    void waitsForTrackCalculationsBeforeCheckingStoredHealth() {
        when(access.enabled()).thenReturn(true);
        when(repository.trackCalculationPending()).thenReturn(true);
        job.scheduled();
        verify(repository, never()).firstDay(any());
        verifyNoInteractions(worker);
        assertThat(job.summary().label()).contains("waiting");
    }

    @Test
    void waitsForDuplicateDecisionsEvenWhenTheEnergyQueueIsEmpty() {
        when(access.enabled()).thenReturn(true);
        when(tracks.existsByTrackSourceAndDuplicateStatus(GpsTrack.TRACK_SOURCE.IMPORTED,
                GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET)).thenReturn(true);
        job.scheduled();
        verify(repository, never()).firstDay(any());
        verifyNoInteractions(worker);
        var blockers = job.prerequisites(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext.indexes(indexers));
        assertThat(blockers).extracting(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.Blocker::id)
                .contains("duplicate");
    }

    @Test
    void waitsForClassificationBeforeItsEnergyRequestsExistThenResumes() {
        when(access.enabled()).thenReturn(true);
        when(tracks.countActivityTypePending()).thenReturn(1L);
        job.scheduled();
        verify(repository, never()).firstDay(any());
        verifyNoInteractions(worker);
        var context = com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext.indexes(indexers);
        assertThat(job.prerequisites(context))
                .extracting(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.Blocker::id)
                .contains("activityType").doesNotContain("energy");
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        when(tracks.countActivityTypePending()).thenReturn(0L);
        when(repository.firstDay(any())).thenReturn(yesterday);
        when(repository.missingDayCount(yesterday, yesterday)).thenReturn(1L, 0L);
        when(repository.missingDays(yesterday, yesterday)).thenReturn(List.of(yesterday));
        when(worker.day(yesterday, yesterday.plusDays(1))).thenReturn(true);
        job.scheduled();
        verify(worker).day(yesterday, yesterday.plusDays(1));
        assertThat(job.summary().progressPercent()).isEqualTo(100);
        assertThat(job.dependencies(context)).filteredOn(edge -> edge.fromKind()
                == com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.Kind.JOB)
                .extracting(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.Dependency::fromId)
                .containsExactly("duplicate", "activityType", "energy");
    }
    @Test
    void advertisesSyntheticHealthWhileWaitingAndUsesDaysForMeasuredProgress() {
        when(access.enabled()).thenReturn(true);
        when(indexers.hasIndexPendingWork("MEDIA")).thenReturn(true);
        var runner = new com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner(
                mock(org.springframework.scheduling.TaskScheduler.class), signal);
        var context = new com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext(indexers, runner,
                new ResourceIntensiveJobGuard(), true);
        var report = job.processingStatus(context);
        assertThat(report.label()).isEqualTo("Google Health (synthetic)");
        assertThat(report.unit()).isEqualTo("days");
        assertThat(report.progressKnown()).isFalse();
        assertThat(report.state()).isEqualTo(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.State.WAITING);
        assertThat(report.blockedBy()).extracting(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.Blocker::id).contains("MEDIA");
    }

    @Test
    void publishesCommittedDayProgressWhileTheNextDayIsRunning() {
        when(access.enabled()).thenReturn(true);
        LocalDate today = LocalDate.now(ZoneOffset.UTC), first = today.minusDays(2), second = today.minusDays(1);
        when(repository.firstDay(any())).thenReturn(first);
        when(repository.missingDayCount(first, second)).thenReturn(2L, 0L);
        when(repository.missingDays(first, second)).thenReturn(List.of(first, second));
        var runner = new com.x8ing.mtl.server.mtlserver.jobs.BackgroundJobRunner(
                mock(org.springframework.scheduling.TaskScheduler.class), signal);
        var guard = new ResourceIntensiveJobGuard();
        var reportedJob = new DemoHealthJob(access, new DemoHealthProperties(), indexers,
                guard, repository, worker, cache, showcase, tracks, signal, runner);
        var context = new com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext(indexers, runner, guard, true);
        when(worker.day(first, today)).thenReturn(true);
        when(worker.day(second, today)).thenAnswer(call -> {
            var progress = reportedJob.processingStatus(context);
            assertThat(progress.state()).isEqualTo(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.State.RUNNING);
            assertThat(progress.progressKnown()).isTrue();
            assertThat(progress.done()).isEqualTo(1);
            assertThat(progress.pending()).isEqualTo(1);
            assertThat(progress.total()).isEqualTo(2);
            assertThat(progress.progressPercent()).isEqualTo(50);
            return true;
        });
        reportedJob.scheduled();
        var completed = reportedJob.processingStatus(context);
        assertThat(completed.state()).isEqualTo(com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails.State.COMPLETE);
        assertThat(completed.done()).isEqualTo(2);
        assertThat(completed.progressPercent()).isEqualTo(100);
    }

}
