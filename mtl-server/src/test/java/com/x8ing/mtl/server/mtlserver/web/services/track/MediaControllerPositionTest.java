package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaBoundsRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTimeCorrectionRequest;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.MediaTrendScope;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
            mock(ImagePreviewService.class));

    @Test
    void mutableBoundsResponseIsNotStoredByBrowserCaches() {
        var request = new MediaBoundsRequest(47, 8, 48, 9, MediaTrendScope.ALL_INDEXED, List.of());
        when(mediaTrendService.getInBounds(request)).thenReturn(List.of());

        var response = controller.getFilteredMediaInBounds(request);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void timeCorrectionUsesNoContentContract() {
        var request = new MediaTimeCorrectionRequest(List.of(7L), 3600);

        var response = controller.saveTimeCorrections(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(mediaPositionService).saveTimeCorrection(request);
    }

}
