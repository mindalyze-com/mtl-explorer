package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTimeCorrectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaControllerPositionTest {

    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final TrackMediaService trackMediaService = mock(TrackMediaService.class);
    private final MediaPositionService mediaPositionService = mock(MediaPositionService.class);
    private final MediaTrendService mediaTrendService = mock(MediaTrendService.class);
    private final MediaController controller = new MediaController(
            mediaRepository,
            trackMediaService,
            mediaPositionService,
            mediaTrendService,
            mock(VideoThumbnailService.class),
            mediaProcessLimiter());

    @Test
    void mutableBoundsResponseIsNotStoredByBrowserCaches() {
        when(mediaRepository.findMediaInBoundsRaw(47.0, 8.0, 48.0, 9.0)).thenReturn(List.of());

        var response = controller.getMediaInBounds(47.0, 8.0, 48.0, 9.0);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void timeCorrectionUsesNoContentContract() {
        var request = new MediaTimeCorrectionRequest(List.of(7L), 3600);

        var response = controller.saveTimeCorrections(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(mediaPositionService).saveTimeCorrection(request);
    }

    private static MediaProcessLimiter mediaProcessLimiter() {
        return new MediaProcessLimiter(new MediaProcessProperties());
    }
}
