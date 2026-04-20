package com.x8ing.mtl.server.mtlserver.web.services.garmin;

import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminExporter;
import com.x8ing.mtl.server.mtlserver.jobs.garminexport.GarminToolInstallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/garmin-export")
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
        try {
            String output = garminToolInstallService.installGcexport(version);
            return ResponseEntity.ok(output);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Install failed: " + e.getMessage());
        }
    }

    @PostMapping("/install-fit-export")
    public ResponseEntity<String> installFitExport(@RequestParam String profile, @RequestParam String packages) {
        try {
            String output = garminToolInstallService.installFitExport(profile, packages);
            return ResponseEntity.ok(output);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Install failed: " + e.getMessage());
        }
    }
}

