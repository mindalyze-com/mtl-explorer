package com.x8ing.mtl.server.mtlserver.web.services.info;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DockerImageInfoServiceTest {

    private static final String REVISION = "48a4874aa741dc980574531172b98f11c91d6017";

    @TempDir
    Path directory;

    @Test
    void environmentTakesPrecedenceOverTheImageFile() throws IOException {
        Path file = Files.writeString(directory.resolve("image-revision"), "another-build\n");
        assertThat(DockerImageInfoService.metadataValue(" " + REVISION + "\n", file)).isEqualTo(REVISION);
    }

    @Test
    void blankEnvironmentFallsBackToTheImageFile() throws IOException {
        Path file = Files.writeString(directory.resolve("image-revision"), REVISION + "\n");
        assertThat(DockerImageInfoService.metadataValue(" ", file)).isEqualTo(REVISION);
        assertThat(DockerImageInfoService.metadataValue(null, file)).isEqualTo(REVISION);
    }

    @Test
    void olderImagesWithoutRevisionMetadataRemainSupported() throws IOException {
        Path file = directory.resolve("image-revision");
        assertThat(DockerImageInfoService.metadataValue(null, file)).isNull();
        Files.writeString(file, "\n");
        assertThat(DockerImageInfoService.metadataValue(null, file)).isNull();
    }

    @Test
    void unreadableMetadataDoesNotBreakTheBuildInfoEndpoint() {
        assertThat(DockerImageInfoService.metadataValue(null, directory)).isNull();
    }
}
