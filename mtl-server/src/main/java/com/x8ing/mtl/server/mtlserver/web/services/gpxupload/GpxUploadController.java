package com.x8ing.mtl.server.mtlserver.web.services.gpxupload;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.gpx.GpxUploadService;
import com.x8ing.mtl.server.mtlserver.web.security.DemoWriteAccess;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/gpx-upload")
@RequiredArgsConstructor
@Slf4j
@JsonPropertyOrder({
        "gpxUploadService",
        "demoWriteAccess"
})
public class GpxUploadController {

    private final GpxUploadService gpxUploadService;
    private final DemoWriteAccess demoWriteAccess;

    @Operation(operationId = "getUploadStatus")
    @GetMapping("/status")
    public GpxUploadService.GpxUploadStatus getStatus() {
        if (demoWriteAccess.blocksImportAndReview()) {
            return new GpxUploadService.GpxUploadStatus(false, "Track uploads are unavailable in this shared demo.");
        }
        return gpxUploadService.getStatus();
    }

    @Operation(operationId = "uploadGpxFile")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<GpxUploadService.GpxUploadResult> upload(@RequestPart("file") MultipartFile file) {
        demoWriteAccess.requireImportAndReviewAccess();
        try {
            String savedName = gpxUploadService.saveFile(file);
            return ResponseEntity.ok(new GpxUploadService.GpxUploadResult(
                    true,
                    "'" + savedName + "' uploaded successfully. Indexing will begin shortly.",
                    savedName
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new GpxUploadService.GpxUploadResult(false, e.getMessage(), null));
        } catch (IllegalStateException e) {
            log.warn("GPX upload service unavailable: {}", e.getMessage(), e);
            return ResponseEntity.status(503)
                    .body(new GpxUploadService.GpxUploadResult(false, "Upload service is not available.", null));
        } catch (Exception e) {
            log.warn("GPX upload failed unexpectedly", e);
            return ResponseEntity.internalServerError()
                    .body(new GpxUploadService.GpxUploadResult(false, "Upload failed.", null));
        }
    }
}
