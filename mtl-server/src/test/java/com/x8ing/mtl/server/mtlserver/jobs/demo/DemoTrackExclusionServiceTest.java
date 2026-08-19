package com.x8ing.mtl.server.mtlserver.jobs.demo;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.repository.gps.GpsTrackRepository;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemoTrackExclusionServiceTest {

    private static final int TARGET_TRACK_COUNT = 1_042;
    private static final double PHOTO_TRACK_DISTANCE_METERS = 50.0;

    private final MtlAppProperties appProperties = mock(MtlAppProperties.class);
    private final GpsTrackRepository repository = mock(GpsTrackRepository.class);
    private final DemoTrackExclusionService service =
            new DemoTrackExclusionService(appProperties, repository);

    @Test
    void doesNothingOutsideDemoMode() {
        service.reconcileDemoTracks();

        verifyNoInteractions(repository);
    }

    @Test
    void restoresPhotoTracksAndPreservesThemWhenTrimming() {
        when(appProperties.isDemoMode()).thenReturn(true);
        when(appProperties.getDemoTargetTrackCount()).thenReturn(TARGET_TRACK_COUNT);
        when(repository.reEnablePhotoMatchedExcludedTracks(
                any(Date.class),
                eq(PHOTO_TRACK_DISTANCE_METERS))).thenReturn(2);
        when(repository.countGoodTracks()).thenReturn(1_044L);

        service.reconcileDemoTracks();

        verify(repository).excludeGoodTracksExceedingOffset(
                TARGET_TRACK_COUNT,
                PHOTO_TRACK_DISTANCE_METERS);
        verify(repository, never()).reEnableNonSuspiciousExcludedTracks(any(Date.class), anyInt());
    }

    @Test
    void fillsTrackDeficitAfterCheckingPhotoTracks() {
        when(appProperties.isDemoMode()).thenReturn(true);
        when(appProperties.getDemoTargetTrackCount()).thenReturn(TARGET_TRACK_COUNT);
        when(repository.countGoodTracks()).thenReturn(1_040L);

        service.reconcileDemoTracks();

        verify(repository).reEnablePhotoMatchedExcludedTracks(
                any(Date.class),
                eq(PHOTO_TRACK_DISTANCE_METERS));
        verify(repository).reEnableNonSuspiciousExcludedTracks(
                any(Date.class),
                eq(2));
        verify(repository, never()).excludeGoodTracksExceedingOffset(
                TARGET_TRACK_COUNT,
                PHOTO_TRACK_DISTANCE_METERS);
    }
}
