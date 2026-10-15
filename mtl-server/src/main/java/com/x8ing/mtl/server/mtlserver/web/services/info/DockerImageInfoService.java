package com.x8ing.mtl.server.mtlserver.web.services.info;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class DockerImageInfoService {

    private static final String IMAGE_VERSION_ENV = "MTL_IMAGE_VERSION";
    private static final String IMAGE_REVISION_ENV = "MTL_IMAGE_REVISION";
    private static final String IMAGE_BUILD_TIME_ENV = "MTL_IMAGE_BUILD_TIME";
    private static final Path IMAGE_VERSION_FILE = Path.of("/opt/mtl/image-version");
    private static final Path IMAGE_REVISION_FILE = Path.of("/opt/mtl/image-revision");
    private static final Path IMAGE_BUILD_TIME_FILE = Path.of("/opt/mtl/image-build-time");

    public ImageVersionInfoDto getImageInfo() {
        return ImageVersionInfoDto.of(
                metadataValue(System.getenv(IMAGE_VERSION_ENV), IMAGE_VERSION_FILE),
                metadataValue(System.getenv(IMAGE_REVISION_ENV), IMAGE_REVISION_FILE),
                metadataValue(System.getenv(IMAGE_BUILD_TIME_ENV), IMAGE_BUILD_TIME_FILE)
        );
    }

    static String metadataValue(String environmentValue, Path filePath) {
        String envValue = blankToNull(environmentValue);
        if (envValue != null) {
            return envValue;
        }
        try {
            if (!Files.exists(filePath)) {
                return null;
            }
            return blankToNull(Files.readString(filePath));
        } catch (IOException | SecurityException exception) {
            log.warn("Cannot read image metadata file {}: {}: {}", filePath,
                    exception.getClass().getSimpleName(), exception.getMessage());
            return null;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
