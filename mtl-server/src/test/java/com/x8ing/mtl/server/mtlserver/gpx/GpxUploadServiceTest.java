package com.x8ing.mtl.server.mtlserver.gpx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpxUploadServiceTest {

    private static final String WAYPOINT_ONLY_GPX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="MTL Explorer test" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="46.9480" lon="7.4470"><name>Not a track</name></wpt>
            </gpx>
            """;
    private static final String TRACK_GPX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="MTL Explorer test" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><trkseg>
                <trkpt lat="46.9480" lon="7.4470" />
                <trkpt lat="46.9490" lon="7.4480" />
              </trkseg></trk>
            </gpx>
            """;

    @TempDir
    Path watchDirectory;

    private GpxUploadService service;

    @BeforeEach
    void setUp() {
        service = new GpxUploadService();
        ReflectionTestUtils.setField(service, "gpxWatchDirectory", watchDirectory.toString());
        service.init();
    }

    @Test
    void rejectsWaypointOnlyGpxBeforeCreatingAWatchedFile() throws Exception {
        MockMultipartFile upload = gpxUpload("waypoint-only.gpx", WAYPOINT_ONLY_GPX);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.saveFile(upload));

        assertEquals(GpxUploadService.GPX_WITHOUT_TRACK_POINTS_MESSAGE, error.getMessage());
        assertFalse(Files.exists(watchDirectory.resolve(GpxUploadService.UPLOAD_SUBDIR).resolve("waypoint-only.gpx")));
        try (var uploadedFiles = Files.list(watchDirectory.resolve(GpxUploadService.UPLOAD_SUBDIR))) {
            assertEquals(0, uploadedFiles.count());
        }
    }

    @Test
    void savesGpxContainingTrackPointsForIndexerProcessing() throws Exception {
        MockMultipartFile upload = gpxUpload("activity.gpx", TRACK_GPX);

        String savedName = service.saveFile(upload);

        assertEquals("activity.gpx", savedName);
        Path savedFile = watchDirectory.resolve(GpxUploadService.UPLOAD_SUBDIR).resolve(savedName);
        assertTrue(Files.exists(savedFile));
        assertEquals(TRACK_GPX, Files.readString(savedFile));
    }

    @Test
    void rejectsUnreadableGpxBeforeCreatingAWatchedFile() {
        MockMultipartFile upload = gpxUpload("broken.gpx", "<gpx><trk>");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.saveFile(upload));

        assertEquals(GpxUploadService.INVALID_GPX_MESSAGE, error.getMessage());
        assertFalse(Files.exists(watchDirectory.resolve(GpxUploadService.UPLOAD_SUBDIR).resolve("broken.gpx")));
    }

    private static MockMultipartFile gpxUpload(String fileName, String content) {
        return new MockMultipartFile(
                "file",
                fileName,
                "application/gpx+xml",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
