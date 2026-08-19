package com.x8ing.mtl.server.mtlserver.jobs.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DemoPhotoGenerationStatusService {

    static final String GENERATION_MARKER_NAME = ".demo-photo-generation-in-progress";

    private final Path generationMarker;

    public DemoPhotoGenerationStatusService(
            @Value("${mtl.media-watch-directory}") String mediaWatchDirectory) {
        this.generationMarker = Path.of(mediaWatchDirectory).resolve(GENERATION_MARKER_NAME);
    }

    public boolean isGenerationInProgress() {
        return Files.exists(generationMarker);
    }
}
