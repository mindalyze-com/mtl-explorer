package com.x8ing.mtl.server.mtlserver.web.services.track;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.entity.media.MediaFile;
import com.x8ing.mtl.server.mtlserver.db.repository.media.MediaRepository;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeQuality;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionDto;
import com.x8ing.mtl.server.mtlserver.web.services.track.entity.VideoTranscodeSessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoTranscodeControllerTest {

    private static final long MEDIA_ID = 41L;
    private static final UUID SESSION_ID = UUID.fromString("c78f1770-a2bd-49ab-bd24-04b14fe109f7");

    @TempDir
    Path tempDirectory;

    private MediaRepository mediaRepository;
    private VideoTranscodeSessionService sessionService;
    private MockMvc mockMvc;
    private Path source;

    @BeforeEach
    void setUp() throws Exception {
        source = tempDirectory.resolve("source.mov");
        Files.writeString(source, "synthetic");
        mediaRepository = mock(MediaRepository.class);
        sessionService = mock(VideoTranscodeSessionService.class);
        when(mediaRepository.findById(MEDIA_ID)).thenReturn(Optional.of(mediaFile(source)));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new VideoTranscodeController(mediaRepository, sessionService)).build();
    }

    @Test
    void createsASessionAndReturnsTheStableFrontendContract() throws Exception {
        VideoTranscodeSessionDto dto = session(false, VideoTranscodeSessionState.RUNNING, true);
        when(sessionService.create(MEDIA_ID, source, VideoTranscodeQuality.P720))
                .thenReturn(new VideoTranscodeSessionService.CreateResult(dto, false));

        mockMvc.perform(post("/api/media/{mediaId}/transcode-sessions", MEDIA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quality\":\"P720\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.mediaId").value(MEDIA_ID))
                .andExpect(jsonPath("$.quality").value("P720"))
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.playlistUrl").value(
                        "api/media/transcode-sessions/" + SESSION_ID + "/playlist.m3u8"))
                .andExpect(jsonPath("$.playlistReady").value(true))
                .andExpect(jsonPath("$.reused").value(false));
    }

    @Test
    void returnsOkWhenCreateReconnectsToAnExistingSession() throws Exception {
        VideoTranscodeSessionDto dto = session(true, VideoTranscodeSessionState.COMPLETED, true);
        when(sessionService.create(MEDIA_ID, source, VideoTranscodeQuality.AUTO))
                .thenReturn(new VideoTranscodeSessionService.CreateResult(dto, true));

        mockMvc.perform(post("/api/media/{mediaId}/transcode-sessions", MEDIA_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reused").value(true));
    }

    @Test
    void servesPlaylistWithExactHlsTypeAndNoCaching() throws Exception {
        Path playlist = tempDirectory.resolve("playlist.m3u8");
        Files.writeString(playlist, "#EXTM3U\n#EXT-X-ENDLIST\n");
        when(sessionService.getOutput(SESSION_ID, VideoTranscodeSessionService.PLAYLIST_FILE))
                .thenReturn(new VideoTranscodeSessionService.OutputResource(
                        playlist, "application/vnd.apple.mpegurl", Files.size(playlist)));

        mockMvc.perform(get("/api/media/transcode-sessions/{sessionId}/playlist.m3u8", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("application/vnd.apple.mpegurl")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("#EXTM3U\n#EXT-X-ENDLIST\n"));
    }

    @Test
    void servesMediaSegmentsWithExactTypeAndNoCaching() throws Exception {
        Path segment = tempDirectory.resolve("segment-000000.m4s");
        Files.write(segment, new byte[]{1, 2, 3, 4});
        when(sessionService.getOutput(SESSION_ID, "segment-000000.m4s"))
                .thenReturn(new VideoTranscodeSessionService.OutputResource(
                        segment, "video/iso.segment", Files.size(segment)));

        mockMvc.perform(get("/api/media/transcode-sessions/{sessionId}/segment-000000.m4s", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.valueOf("video/iso.segment")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void reportsConflictUntilTheFirstPlaylistSegmentIsReady() throws Exception {
        when(sessionService.getOutput(SESSION_ID, VideoTranscodeSessionService.PLAYLIST_FILE))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "still preparing"));

        mockMvc.perform(get("/api/media/transcode-sessions/{sessionId}/playlist.m3u8", SESSION_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsFilesOutsideTheHlsOutputAllowlist() throws Exception {
        when(sessionService.getOutput(SESSION_ID, "ffmpeg.log"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

        mockMvc.perform(get("/api/media/transcode-sessions/{sessionId}/ffmpeg.log", SESSION_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void explicitDeleteCancelsTheSession() throws Exception {
        mockMvc.perform(delete("/api/media/transcode-sessions/{sessionId}", SESSION_ID))
                .andExpect(status().isNoContent());

        verify(sessionService).cancel(SESSION_ID);
    }

    private static VideoTranscodeSessionDto session(boolean reused,
                                                    VideoTranscodeSessionState state,
                                                    boolean playlistReady) {
        return new VideoTranscodeSessionDto(
                SESSION_ID.toString(),
                MEDIA_ID,
                VideoTranscodeQuality.P720,
                state,
                "api/media/transcode-sessions/" + SESSION_ID + "/playlist.m3u8",
                playlistReady,
                4d,
                20d,
                2d,
                1024L,
                "Preparing compatible stream",
                reused);
    }

    private static MediaFile mediaFile(Path path) {
        IndexedFile indexedFile = new IndexedFile();
        indexedFile.setFullPath(path.toString());
        indexedFile.setName(path.getFileName().toString());
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(MEDIA_ID);
        mediaFile.setIndexedFile(indexedFile);
        return mediaFile;
    }
}
