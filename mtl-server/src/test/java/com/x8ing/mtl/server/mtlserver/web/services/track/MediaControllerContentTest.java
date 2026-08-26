package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerContentTest {

    private static final long MEDIA_ID = 41L;
    private static final byte[] VIDEO_BYTES = "synthetic-video-payload".getBytes();
    private static final byte[] POSTER_BYTES = {(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};

    @TempDir
    Path tempDirectory;

    private MediaRepository mediaRepository;
    private VideoThumbnailService videoThumbnailService;
    private MediaProcessLimiter mediaProcessLimiter;
    private MockMvc mockMvc;
    private Path videoPath;

    @BeforeEach
    void setUp() throws Exception {
        videoPath = tempDirectory.resolve("synthetic.mp4");
        Files.write(videoPath, VIDEO_BYTES);

        mediaRepository = mock(MediaRepository.class);
        videoThumbnailService = mock(VideoThumbnailService.class);
        mediaProcessLimiter = mediaProcessLimiter();
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(mediaFile(videoPath)));

        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(
                mediaRepository,
                mock(TrackMediaService.class),
                mock(MediaPositionService.class),
                mock(MediaTrendService.class),
                videoThumbnailService,
                mediaProcessLimiter)).build();
    }

    @Test
    void servesTheCompleteVideoAsAResource() throws Exception {
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("video/mp4")))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, VIDEO_BYTES.length))
                .andExpect(content().bytes(VIDEO_BYTES));
    }

    @Test
    void servesOneBrowserByteRangeWithoutSerializingAStreamingLambda() throws Exception {
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .header(HttpHeaders.RANGE, "bytes=2-7"))
                .andExpect(status().isPartialContent())
                .andExpect(content().contentType(MediaType.valueOf("video/mp4")))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-7/23"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 6))
                .andExpect(content().bytes("ntheti".getBytes()));
    }

    @Test
    void rejectsAnUnsatisfiedVideoRange() throws Exception {
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .header(HttpHeaders.RANGE, "bytes=999-1000"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */23"));
    }

    @Test
    void returnsAJpegPosterForThumbnailRequests() throws Exception {
        when(videoThumbnailService.createThumbnail(videoPath, 192)).thenReturn(POSTER_BYTES);

        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("maxSize", "192"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, POSTER_BYTES.length))
                .andExpect(content().bytes(POSTER_BYTES));

        verify(videoThumbnailService).createThumbnail(videoPath, 192);
    }

    @Test
    void returnsServiceUnavailableInsteadOfWaitingForImageProcessing() throws Exception {
        Path imagePath = tempDirectory.resolve("synthetic.jpg");
        Files.write(imagePath, "synthetic-image-payload".getBytes());
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(mediaFile(imagePath)));

        try (MediaProcessLimiter.Permit ignored = mediaProcessLimiter.acquire()) {
            mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                            .queryParam("maxSize", "192"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(header().doesNotExist(HttpHeaders.LAST_MODIFIED));
        }
    }

    private static MediaFile mediaFile(Path path) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setName(path.getFileName().toString());
        indexedFile.setFullPath(path.toString());

        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(MEDIA_ID);
        mediaFile.setIndexedFile(indexedFile);
        return mediaFile;
    }

    private static MediaProcessLimiter mediaProcessLimiter() {
        MediaProcessProperties properties = new MediaProcessProperties();
        properties.setAcquireTimeout(Duration.ZERO);
        return new MediaProcessLimiter(properties);
    }
}
