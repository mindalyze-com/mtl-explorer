package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import com.x8ing.mtl.server.mtlserver.web.services.info.ImageVersionInfoDto;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpstreamBuildVersionServiceTest {

    @Test
    void prefersContainerReleaseAndImageBuildTimeOverMavenMetadata() {
        assertThat(metadata("1.519", "2026-10-06T10:20:30Z",
                mavenBuild("1.0.0-SNAPSHOT", "2026-10-05T01:02:03Z")))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.519", "2026-10-06T10_20_30Z");
    }

    @Test
    void sameReleaseRebuiltAtDifferentTimesHasDistinctIdentity() {
        UpstreamBuildVersionService first = metadata("local", "2026-10-06T10:20:30Z", Optional.empty());
        UpstreamBuildVersionService second = metadata("local", "2026-10-06T11:20:30Z", Optional.empty());

        assertThat(first.getVersion()).isEqualTo(second.getVersion());
        assertThat(first.getBuild()).isNotEqualTo(second.getBuild());
    }

    @Test
    void missingImageBuildTimeUsesMavenBuildTimeWithContainerRelease() {
        assertThat(metadata("1.519", null,
                mavenBuild("1.0.0-SNAPSHOT", "2026-10-06T10:20:30.528Z")))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.519", "2026-10-06T10_20_30Z");
    }

    @Test
    void missingContainerVersionStillUsesImageBuildTime() {
        assertThat(metadata(null, "2026-10-06T10:20:30Z",
                mavenBuild("1.0.0-SNAPSHOT", "2026-10-05T01:02:03Z")))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.0.0-SNAPSHOT", "2026-10-06T10_20_30Z");
    }

    @Test
    void absentImageMetadataPreservesDevelopmentBuildIdentity() {
        assertThat(metadata(null, null,
                mavenBuild("1.0.0-SNAPSHOT", "2026-05-02T19:25:13.528Z")))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.0.0-SNAPSHOT", "2026-05-02T19_25_13Z");
        assertThat(metadata(" ", " ", mavenBuild("1.0.0-SNAPSHOT", null)))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.0.0-SNAPSHOT", null);
    }

    @Test
    void metadataWithoutTimeUsesVersionAlone() {
        assertThat(metadata("1.519", null, Optional.empty()))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.519", null);
    }

    @Test
    void missingAllMetadataUsesUnknown() {
        assertThat(metadata(null, null, Optional.empty()))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("unknown", null);
        assertThat(metadata(" ", " ", mavenBuild(" ", null)))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("unknown", null);
    }

    @Test
    void sanitizesMetadataForUpstreamAuthorizationAndLogs() {
        assertThat(metadata(" 1.519 local:test/build ", "2026-10-06T10:20:30+02:00", Optional.empty()))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("1.519_local_test_build", "2026-10-06T10_20_30_02_00");
        assertThat(metadata(null, null, mavenBuild("0.0.1 SNAPSHOT:local/build", null)))
                .extracting(UpstreamBuildVersionService::getVersion, UpstreamBuildVersionService::getBuild)
                .containsExactly("0.0.1_SNAPSHOT_local_build", null);
    }

    private static UpstreamBuildVersionService metadata(String imageVersion, String imageBuildTime,
                                                        Optional<BuildProperties> buildProperties) {
        DockerImageInfoService imageInfoService = mock(DockerImageInfoService.class);
        when(imageInfoService.getImageInfo()).thenReturn(ImageVersionInfoDto.of(imageVersion, null, imageBuildTime));
        return new UpstreamBuildVersionService(imageInfoService, buildProperties);
    }

    private static Optional<BuildProperties> mavenBuild(String version, String buildTime) {
        Properties entries = new Properties();
        entries.setProperty("version", version);
        if (buildTime != null) {
            entries.setProperty("time", buildTime);
        }
        return Optional.of(new BuildProperties(entries));
    }
}
