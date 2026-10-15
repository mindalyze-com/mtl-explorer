package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        mediaProcessLimiter = spy(mediaProcessLimiter());
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(mediaFile(videoPath)));

        mockMvc = MockMvcBuilders.standaloneSetup(new MediaController(
                mediaRepository,
                mock(TrackMediaService.class),
                mock(MediaPositionService.class),
                mock(MediaTrendService.class),
                videoThumbnailService,
                new ImagePreviewService(mediaProcessLimiter, mock(ApplicationEventPublisher.class)))).build();
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
    void downloadsUnchangedHeicDespiteBrowserAcceptAndPreviewSize() throws Exception {
        Path path = tempDirectory.resolve("original photo.heic");
        byte[] bytes = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 1, 2, 3};
        Files.write(path, bytes);
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(mediaFile(path)));
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("original", "true").queryParam("maxSize", "192")
                        .header(HttpHeaders.ACCEPT, "image/jpeg,image/*;q=0.8,*/*;q=0.5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/heic"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, bytes.length))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''original%20photo.heic")))
                .andExpect(header().doesNotExist("X-MTL-Image-Processor"));
        verify(mediaProcessLimiter, never()).start(any(ProcessBuilder.class));
    }

    @Test
    void originalAndConvertedHeicHaveSeparateCacheValidators() throws Exception {
        useSyntheticImageProcess("printf '\\377\\330\\377synthetic\\377\\331'");
        var preview = mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID))
                .andExpect(status().isOk()).andReturn();
        String previewEtag = preview.getResponse().getHeader(HttpHeaders.ETAG);
        var original = mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("original", "true").header(HttpHeaders.IF_NONE_MATCH, previewEtag))
                .andExpect(status().isOk())
                .andExpect(content().bytes("synthetic-container".getBytes())).andReturn();
        String originalEtag = original.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotEquals(previewEtag, originalEtag);
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("original", "true").queryParam("maxSize", "512")
                        .header(HttpHeaders.IF_NONE_MATCH, originalEtag))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .header(HttpHeaders.IF_NONE_MATCH, originalEtag))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void originalVideoDownloadsRetainByteRangeSupport() throws Exception {
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("original", "true").queryParam("maxSize", "192")
                        .header(HttpHeaders.RANGE, "bytes=2-7"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-7/23"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 6))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().bytes("ntheti".getBytes()));
        verify(videoThumbnailService, never()).createThumbnail(any(Path.class), org.mockito.ArgumentMatchers.anyInt());
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
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                    .andExpect(header().doesNotExist(HttpHeaders.LAST_MODIFIED));
        }
    }

    @Test
    void rejectsDecoderFailureWithoutCommittingOrCachingAnEmptySuccess() throws Exception {
        useSyntheticImageProcess("printf 'Too many auxiliary image references' >&2; exit 1");
        assertImageConversionFailure();
    }

    @Test
    void rejectsAnEmptySuccessfulProcess() throws Exception {
        useSyntheticImageProcess("exit 0");
        assertImageConversionFailure();
    }

    @Test
    void rejectsPartialOutputEvenWhenTheProcessExitsSuccessfully() throws Exception {
        useSyntheticImageProcess("printf '\\377\\330\\377partial'");
        assertImageConversionFailure();
    }

    @Test
    void rejectsOutputFromAFailedProcessEvenWhenItLooksLikeAJpeg() throws Exception {
        useSyntheticImageProcess("printf '\\377\\330\\377synthetic\\377\\331'; exit 1");
        assertImageConversionFailure();
    }

    @Test
    void signalsSuccessfulFallbackInResponseHeaders() throws Exception {
        useSyntheticImageProcess("exit 64");
        doAnswer(invocation -> {
            ProcessBuilder builder = invocation.getArgument(0);
            boolean nativePreview = builder.command().getFirst().equals("mtl-vips-preview");
            builder.command("sh", "-c", nativePreview ? "exit 64"
                    : "printf '\\377\\330\\377synthetic\\377\\331'");
            return invocation.callRealMethod();
        }).when(mediaProcessLimiter).start(any(ProcessBuilder.class));
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID).queryParam("maxSize", "192"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-MTL-Image-Processor", "imagemagick"))
                .andExpect(header().string("X-MTL-Image-Fallback", "unsupported-format"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 14));
    }

    @Test
    void failedFallbackIsNotCachedOrReportedAsSuccessful() throws Exception {
        useSyntheticImageProcess("exit 64");
        assertImageConversionFailure();
    }

    @Test
    void servesCompletedConversionWithLengthAndRevalidatesAgainstTheNewVariant() throws Exception {
        useSyntheticImageProcess("printf '\\377\\330\\377synthetic\\377\\331'");
        var result = mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("maxSize", "2048"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("X-MTL-Image-Processor", "libvips"))
                .andExpect(header().doesNotExist("X-MTL-Image-Fallback"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 14))
                .andReturn();
        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("maxSize", "2048")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified());
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("maxSize", "2048")
                        .header(HttpHeaders.IF_NONE_MATCH, etag.replace("converted-jpeg-v3", "converted-jpeg-v2")))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 14));
    }

    private void useSyntheticImageProcess(String script) throws Exception {
        Path imagePath = tempDirectory.resolve("synthetic.heic");
        Files.writeString(imagePath, "synthetic-container");
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(mediaFile(imagePath)));
        doAnswer(invocation -> {
            ProcessBuilder builder = invocation.getArgument(0);
            builder.command("sh", "-c", script);
            return invocation.callRealMethod();
        }).when(mediaProcessLimiter).start(any(ProcessBuilder.class));
    }

    private void assertImageConversionFailure() throws Exception {
        mockMvc.perform(get("/api/media/get/{id}/content", MEDIA_ID)
                        .queryParam("maxSize", "2048"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(header().doesNotExist(HttpHeaders.LAST_MODIFIED))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
                .andExpect(header().doesNotExist("X-MTL-Image-Processor"))
                .andExpect(header().doesNotExist("X-MTL-Image-Fallback"));
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
        properties.setAcquireTimeout(Duration.ofMillis(250));
        return new MediaProcessLimiter(properties);
    }
}
