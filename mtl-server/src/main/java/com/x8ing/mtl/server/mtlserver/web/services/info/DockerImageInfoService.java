package com.x8ing.mtl.server.mtlserver.web.services.info;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DockerImageInfoService {

    private static final String IMAGE_VERSION_ENV = "MTL_IMAGE_VERSION";
    private static final String IMAGE_BUILD_TIME_ENV = "MTL_IMAGE_BUILD_TIME";
    private static final Path IMAGE_VERSION_FILE = Path.of("/opt/mtl/image-version");
    private static final Path IMAGE_BUILD_TIME_FILE = Path.of("/opt/mtl/image-build-time");

    public ImageVersionInfoDto getImageInfo() {
        return ImageVersionInfoDto.of(
                metadataValue(IMAGE_VERSION_ENV, IMAGE_VERSION_FILE),
                metadataValue(IMAGE_BUILD_TIME_ENV, IMAGE_BUILD_TIME_FILE)
        );
    }

    private static String metadataValue(String envName, Path filePath) {
        String envValue = blankToNull(System.getenv(envName));
        if (envValue != null) {
            return envValue;
        }
        try {
            return blankToNull(Files.readString(filePath));
        } catch (Exception ignored) {
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
