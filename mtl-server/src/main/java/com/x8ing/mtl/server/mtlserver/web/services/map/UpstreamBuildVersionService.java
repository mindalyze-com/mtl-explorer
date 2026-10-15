package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import com.x8ing.mtl.server.mtlserver.web.services.info.ImageVersionInfoDto;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Provides the running build identity for upstream map and location-search requests.
 */
@Service
public class UpstreamBuildVersionService {

    private static final String UNKNOWN_BUILD_VERSION = "unknown";
    private static final String UNSAFE_TOKEN_CHARS_REGEX = "[^A-Za-z0-9._-]";
    private static final String TOKEN_REPLACEMENT = "_";

    private final String version;
    private final String build;

    public UpstreamBuildVersionService(DockerImageInfoService imageInfoService,
                                       Optional<BuildProperties> buildProperties) {
        // Image metadata is fixed for this process; avoid file reads on every tile range request.
        ImageVersionInfoDto image = imageInfoService.getImageInfo();
        String buildVersion = firstNonBlank(image.getVersion(),
                buildProperties.map(BuildProperties::getVersion).orElse(UNKNOWN_BUILD_VERSION));
        String buildTime = firstNonBlank(image.getBuildTime(),
                buildProperties.map(BuildProperties::getTime)
                        .map(time -> time.truncatedTo(ChronoUnit.SECONDS).toString())
                        .orElse(null));
        version = sanitizeTokenPart(firstNonBlank(buildVersion, UNKNOWN_BUILD_VERSION));
        build = buildTime == null ? null : sanitizeTokenPart(buildTime);
    }

    public String getVersion() {
        return version;
    }

    public String getBuild() {
        return build;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static String sanitizeTokenPart(String value) {
        return value.trim().replaceAll(UNSAFE_TOKEN_CHARS_REGEX, TOKEN_REPLACEMENT);
    }
}
