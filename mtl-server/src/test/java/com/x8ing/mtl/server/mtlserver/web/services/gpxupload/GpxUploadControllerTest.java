package com.x8ing.mtl.server.mtlserver.web.services.gpxupload;

import com.x8ing.mtl.server.mtlserver.gpx.GpxUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GpxUploadControllerTest {

    @Test
    void waypointOnlyGpxReturnsClearBadRequest(@TempDir Path watchDirectory) {
        GpxUploadService service = new GpxUploadService();
        ReflectionTestUtils.setField(service, "gpxWatchDirectory", watchDirectory.toString());
        ReflectionTestUtils.invokeMethod(service, "init");
        GpxUploadController controller = new GpxUploadController(service);
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "waypoint-only.gpx",
                "application/gpx+xml",
                """
                        <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
                          <wpt lat="46.9480" lon="7.4470"><name>Not a track</name></wpt>
                        </gpx>
                        """.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<GpxUploadService.GpxUploadResult> response = controller.upload(upload);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        GpxUploadService.GpxUploadResult result = response.getBody();
        assertNotNull(result);
        assertFalse(result.success());
        assertEquals("Uploaded GPX does not contain any track points. No track was imported.", result.message());
        assertNull(result.fileName());
    }
}
