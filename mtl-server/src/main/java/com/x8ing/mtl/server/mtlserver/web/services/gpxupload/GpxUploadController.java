package com.x8ing.mtl.server.mtlserver.web.services.gpxupload;

import com.x8ing.mtl.server.mtlserver.gpx.GpxUploadService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/gpx-upload")
@RequiredArgsConstructor
public class GpxUploadController {

    private final GpxUploadService gpxUploadService;

    @Operation(operationId = "getUploadStatus")
    @GetMapping("/status")
    public GpxUploadService.GpxUploadStatus getStatus() {
        return gpxUploadService.getStatus();
    }

    @Operation(operationId = "uploadGpxFile")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<GpxUploadService.GpxUploadResult> upload(@RequestPart("file") MultipartFile file) {
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
            return ResponseEntity.status(503)
                    .body(new GpxUploadService.GpxUploadResult(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new GpxUploadService.GpxUploadResult(false, "Upload failed: " + e.getMessage(), null));
        }
    }
}
