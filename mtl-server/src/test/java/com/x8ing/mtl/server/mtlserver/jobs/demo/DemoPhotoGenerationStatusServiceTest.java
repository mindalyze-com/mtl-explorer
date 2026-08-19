package com.x8ing.mtl.server.mtlserver.jobs.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPhotoGenerationStatusServiceTest {

    @Test
    void reportsGenerationMarkerState(@TempDir Path mediaDirectory) throws Exception {
        DemoPhotoGenerationStatusService service =
                new DemoPhotoGenerationStatusService(mediaDirectory.toString());

        assertThat(service.isGenerationInProgress()).isFalse();

        Files.createFile(mediaDirectory.resolve(
                DemoPhotoGenerationStatusService.GENERATION_MARKER_NAME));

        assertThat(service.isGenerationInProgress()).isTrue();
    }
}
