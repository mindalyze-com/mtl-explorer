package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaCorrelationRepository;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationAtomicWorker;
import com.x8ing.mtl.server.mtlserver.jobs.media.correlation.MediaCorrelationJob;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.ManualMediaLocationRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTimeCorrectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MediaPositionServiceTest {

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final MediaCorrelationRepository correlationRepository = mock(MediaCorrelationRepository.class);
    private final MediaCorrelationAtomicWorker correlationWorker = mock(MediaCorrelationAtomicWorker.class);
    private final MediaPositionService service = new MediaPositionService(
            mediaRepository, correlationRepository, correlationWorker);

    @Test
    void savesManualLocationAndRebuildsOnlyThatMedia() {
        when(mediaRepository.existsById(7L)).thenReturn(true);

        service.setManualLocation(7L, new ManualMediaLocationRequest(47.1, 8.2, "  viewpoint  "));

        verify(correlationRepository).upsertManualLocation(7L, 47.1, 8.2, "viewpoint");
        verify(correlationWorker).rebuildMediaIds(List.of(7L), MediaCorrelationJob.ALGORITHM_VERSION);
    }

    @Test
    void clearingManualLocationPreservesAutomaticSourcesThroughResolution() {
        when(mediaRepository.existsById(7L)).thenReturn(true);
        when(correlationRepository.deleteManualLocation(7L)).thenReturn(1);

        service.clearManualLocation(7L);

        verify(correlationRepository).deleteManualLocation(7L);
        verify(correlationWorker).rebuildMediaIds(List.of(7L), MediaCorrelationJob.ALGORITHM_VERSION);
    }

    @Test
    void rejectsInvalidManualCoordinates() {
        when(mediaRepository.existsById(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.setManualLocation(
                7L, new ManualMediaLocationRequest(91, 8.2, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(correlationRepository, correlationWorker);
    }

    @Test
    void savesDeduplicatedTimeCorrectionsAndRebuildsTheRequestedMedia() {
        when(mediaRepository.countByIdIn(List.of(7L, 8L))).thenReturn(2L);
        when(correlationRepository.saveTimeCorrection(List.of(7L, 8L), 3600)).thenReturn(List.of(7L, 8L));

        service.saveTimeCorrection(new MediaTimeCorrectionRequest(List.of(7L, 8L, 7L), 3600));

        verify(correlationWorker).rebuildMediaIds(List.of(7L, 8L), MediaCorrelationJob.ALGORITHM_VERSION);
    }

    @Test
    void skipsRebuildWhenClearOrCorrectionDoesNotChangeStoredState() {
        when(mediaRepository.existsById(7L)).thenReturn(true);
        when(mediaRepository.countByIdIn(List.of(7L))).thenReturn(1L);
        when(correlationRepository.deleteManualLocation(7L)).thenReturn(0);
        when(correlationRepository.saveTimeCorrection(List.of(7L), 3600)).thenReturn(List.of());

        service.clearManualLocation(7L);
        service.saveTimeCorrection(new MediaTimeCorrectionRequest(List.of(7L), 3600));

        verifyNoInteractions(correlationWorker);
    }

    @Test
    void rejectsRawRequestAboveLimitBeforeDeduplication() {
        List<Long> tooMany = LongStream.rangeClosed(1, MediaPositionService.MAX_BATCH_MEDIA_IDS + 1L)
                .boxed()
                .toList();

        assertThatThrownBy(() -> service.saveTimeCorrection(new MediaTimeCorrectionRequest(tooMany, 3600)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(mediaRepository, correlationRepository, correlationWorker);
    }

    @Test
    void rejectsNullMediaIdAsBadRequest() {
        assertThatThrownBy(() -> service.saveTimeCorrection(
                new MediaTimeCorrectionRequest(java.util.Arrays.asList(7L, null), 3600)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(mediaRepository, correlationRepository, correlationWorker);
    }

    @Test
    void rejectsOutOfRangeTimeCorrection() {
        assertThatThrownBy(() -> service.saveTimeCorrection(
                new MediaTimeCorrectionRequest(List.of(7L), 86_401)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(mediaRepository, correlationRepository, correlationWorker);
    }
}
