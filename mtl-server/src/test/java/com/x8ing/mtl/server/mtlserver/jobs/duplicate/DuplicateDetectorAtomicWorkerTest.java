package com.x8ing.mtl.server.mtlserver.jobs.duplicate;

import com.x8ing.mtl.server.mtlserver.db.entity.gps.GpsTrack;
import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DuplicateDetectorAtomicWorkerTest {

    private static final Duration TIME_TOLERANCE = Duration.ofMinutes(20);
    private static final double DISTANCE_TOLERANCE = 1000.0;

    @Test
    void failedCandidateCannotBecomeUniqueWhenAnotherTrackIsProcessed() {
        GpsTrack failed = track(1L, "synthetic-failed.gpx", 1000,
                "2034-03-20T10:00:00Z", "2034-03-20T10:30:00Z");
        failed.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.FAILED);
        GpsTrack healthy = track(2L, "synthetic-healthy.gpx", 200,
                "2034-03-20T10:00:00Z", "2034-03-20T10:30:00Z");
        var repository = repositoryForSimilarTracks(failed, healthy);
        var worker = new DuplicateDetectorAtomicWorker(repository);

        worker.processOne(healthy.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);

        assertEquals(GpsTrack.DUPLICATE_CHECK_STATUS.FAILED, failed.getDuplicateStatus());
        verifyUnique(repository, healthy);
        verify(repository, never()).updateDuplicateDetection(eq(failed.getId()), any(), any(), any());
    }

    @Test
    void pendingReviewAndAcceptedEstimatedTracksCannotDisplaceHealthyRecordings() {
        for (boolean pendingReview : List.of(true, false)) {
            for (boolean processSuspectFirst : List.of(true, false)) {
                GpsTrack suspect = track(1L, "synthetic-suspect.gpx", 1000,
                        "2034-03-20T10:00:00Z", "2034-03-20T10:30:00Z");
                suspect.setReviewExcluded(pendingReview);
                suspect.setTimingUnreliable(!pendingReview);
                suspect.setTimingEstimated(!pendingReview);
                GpsTrack healthy = track(2L, "synthetic-healthy.gpx", 200,
                        "2034-03-20T10:00:00Z", "2034-03-20T10:30:00Z");
                var repository = repositoryForSimilarTracks(suspect, healthy);
                var worker = new DuplicateDetectorAtomicWorker(repository);
                worker.processOne(processSuspectFirst ? suspect.getId() : healthy.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);
                worker.processOne(processSuspectFirst ? healthy.getId() : suspect.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);
                verifyUnique(repository, healthy);
                verifyUnique(repository, suspect);
                verify(repository, never()).updateDuplicateDetection(any(), any(),
                        eq(GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE), any());
            }
        }
    }

    @Test
    void processOneDoesNotMarkAdjacentFragmentsAsDuplicates() {
        GpsTrack firstFragment = track(
                100003L,
                "OSM_trace_12376326.gpx",
                61,
                "2026-05-31T13:59:17Z",
                "2026-05-31T14:07:06Z");
        GpsTrack secondFragment = track(
                100000L,
                "OSM_trace_12376325.gpx",
                125,
                "2026-05-31T14:07:11Z",
                "2026-05-31T14:19:50Z");
        GpsTrackRepository repository = repositoryForSimilarTracks(firstFragment, secondFragment);
        DuplicateDetectorAtomicWorker worker = new DuplicateDetectorAtomicWorker(repository);

        worker.processOne(secondFragment.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);
        worker.processOne(firstFragment.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);

        verifyUnique(repository, firstFragment);
        verifyUnique(repository, secondFragment);
    }

    @Test
    void processOneStillMarksOverlappingTracksAsDuplicates() {
        GpsTrack betterTrack = track(
                1L,
                "original.gpx",
                200,
                "2026-05-31T10:00:00Z",
                "2026-05-31T10:30:00Z");
        GpsTrack duplicateTrack = track(
                2L,
                "copy.gpx",
                100,
                "2026-05-31T10:05:00Z",
                "2026-05-31T10:25:00Z");
        assertMarkedDuplicate(betterTrack, duplicateTrack);
    }

    @Test
    void processOneKeepsNonAdjacentTracksEligibleAsDuplicates() {
        GpsTrack betterTrack = track(
                1L,
                "original.gpx",
                200,
                "2026-05-31T10:00:00Z",
                "2026-05-31T10:10:00Z");
        GpsTrack duplicateTrack = track(
                2L,
                "copy.gpx",
                100,
                "2026-05-31T10:13:01Z",
                "2026-05-31T10:23:01Z");
        assertMarkedDuplicate(betterTrack, duplicateTrack);
    }

    private static void assertMarkedDuplicate(GpsTrack betterTrack, GpsTrack duplicateTrack) {
        GpsTrackRepository repository = repositoryForSimilarTracks(betterTrack, duplicateTrack);
        DuplicateDetectorAtomicWorker worker = new DuplicateDetectorAtomicWorker(repository);

        worker.processOne(betterTrack.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);

        verifyUnique(repository, betterTrack);
        verify(repository).updateDuplicateDetection(duplicateTrack.getId(), duplicateTrack.getDuplicateStatus(),
                GpsTrack.DUPLICATE_CHECK_STATUS.DUPLICATE, betterTrack.getId());
        // Inputs remain unchanged, so Hibernate cannot flush a whole-track update later.
        assertEquals(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET, betterTrack.getDuplicateStatus());
        assertNull(betterTrack.getDuplicateOf());
        assertEquals(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET, duplicateTrack.getDuplicateStatus());
        assertNull(duplicateTrack.getDuplicateOf());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAll(any());
    }

    private static void verifyUnique(GpsTrackRepository repository, GpsTrack track) {
        verify(repository, atLeastOnce()).updateDuplicateDetection(track.getId(), track.getDuplicateStatus(),
                GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, null);
    }

    @Test
    void changedWinnerLeavesOtherCandidatesUntouched() {
        var winner = track(1L, "winner.gpx", 200, "2034-03-20T10:00:00Z", "2034-03-20T10:30:00Z");
        var candidate = track(2L, "candidate.gpx", 100, "2034-03-20T10:00:00Z", "2034-03-20T10:30:00Z");
        var repository = repositoryForSimilarTracks(winner, candidate);
        when(repository.updateDuplicateDetection(winner.getId(), winner.getDuplicateStatus(),
                GpsTrack.DUPLICATE_CHECK_STATUS.UNIQUE, null)).thenReturn(0);

        new DuplicateDetectorAtomicWorker(repository).processOne(candidate.getId(), TIME_TOLERANCE, DISTANCE_TOLERANCE);

        verify(repository, never()).updateDuplicateDetection(eq(candidate.getId()), any(), any(), any());
    }

    private static GpsTrackRepository repositoryForSimilarTracks(GpsTrack first, GpsTrack second) {
        GpsTrackRepository repository = mock(GpsTrackRepository.class);
        List<Long> similarIds = List.of(first.getId(), second.getId());
        List<GpsTrack> similarTracks = List.of(first, second);

        when(repository.findById(first.getId())).thenReturn(Optional.of(first));
        when(repository.findById(second.getId())).thenReturn(Optional.of(second));
        when(repository.findSimilarTracksWithinTimeRangeForTrack(
                any(Date.class),
                any(Date.class),
                eq(DISTANCE_TOLERANCE),
                eq(first.getId()))).thenReturn(similarIds);
        when(repository.findSimilarTracksWithinTimeRangeForTrack(
                any(Date.class),
                any(Date.class),
                eq(DISTANCE_TOLERANCE),
                eq(second.getId()))).thenReturn(similarIds);
        when(repository.findAllById(similarIds)).thenReturn(similarTracks);
        when(repository.updateDuplicateDetection(any(), any(), any(), any())).thenReturn(1);

        return repository;
    }

    private static GpsTrack track(long id, String fileName, int points, String start, String end) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setName(fileName);

        GpsTrack track = new GpsTrack();
        track.setId(id);
        track.setIndexedFile(indexedFile);
        track.setNumberOfTrackPoints(points);
        track.setStartDate(Date.from(Instant.parse(start)));
        track.setEndDate(Date.from(Instant.parse(end)));
        track.setDuplicateStatus(GpsTrack.DUPLICATE_CHECK_STATUS.NOT_CHECKED_YET);
        return track;
    }
}
