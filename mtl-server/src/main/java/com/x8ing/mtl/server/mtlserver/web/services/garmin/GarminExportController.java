package com.x8ing.mtl.server.mtlserver.web.services.garmin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminToolInstallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/garmin-export")
@JsonPropertyOrder({
        "garminExporter",
        "garminToolInstallService"
})
public class GarminExportController {

    private final GarminExporter garminExporter;
    private final GarminToolInstallService garminToolInstallService;

    public GarminExportController(GarminExporter garminExporter, GarminToolInstallService garminToolInstallService) {
        this.garminExporter = garminExporter;
        this.garminToolInstallService = garminToolInstallService;
    }

    @RequestMapping("/trigger-export")
    public String triggerExport() throws Exception {
        return garminExporter.run();
    }

    @GetMapping("/tool-status")
    public GarminToolInstallService.ToolStatusDto getToolStatus() {
        return garminToolInstallService.getToolStatus();
    }

    @PostMapping("/install-gcexport")
    public ResponseEntity<String> installGcexport(@RequestParam String version) {
        return installTool(() -> garminToolInstallService.installGcexport(version));
    }

    @PostMapping("/install-fit-export")
    public ResponseEntity<String> installFitExport(@RequestParam String profile, @RequestParam String packages) {
        return installTool(() -> garminToolInstallService.installFitExport(profile, packages));
    }

    private ResponseEntity<String> installTool(InstallOperation operation) {
        try {
            return ResponseEntity.ok(operation.run());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Install failed: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface InstallOperation {
        String run() throws Exception;
    }
}
