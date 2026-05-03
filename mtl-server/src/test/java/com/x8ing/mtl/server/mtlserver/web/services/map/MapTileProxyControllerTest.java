package com.x8ing.mtl.server.mtlserver.web.services.map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.util.Properties;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_PARTIAL_CONTENT;
import static jakarta.servlet.http.HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE;
import static org.assertj.core.api.Assertions.assertThat;

class MapTileProxyControllerTest {

    @Test
    void okHttpClientUsesConfiguredCallTimeout() {
        MapServerProperties properties = new MapServerProperties();
        properties.setProxyCallTimeoutMs(1234);

        assertThat(MapTileProxyController.buildOkHttpClient(properties).callTimeoutMillis())
                .isEqualTo(1234);
    }

    @Test
    void buildVersionParamIncludesBuildTime() {
        Properties entries = new Properties();
        entries.setProperty("version", "0.0.1-SNAPSHOT");
        entries.setProperty("time", "2026-05-02T19:25:13.528Z");

        String versionParam = MapTileProxyController.buildVersionParam(new BuildProperties(entries));

        assertThat(versionParam).isEqualTo("0.0.1-SNAPSHOT@2026-05-02T19:25:13.528Z");
    }

    @Test
    void buildVersionParamFallsBackToVersionOnlyWhenBuildTimeIsMissing() {
        Properties entries = new Properties();
        entries.setProperty("version", "0.0.1-SNAPSHOT");

        String versionParam = MapTileProxyController.buildVersionParam(new BuildProperties(entries));

        assertThat(versionParam).isEqualTo("0.0.1-SNAPSHOT");
    }

    @Test
    void fullDownloadIsAllowedOnlyForDistinctLowZoomArchive() {
        assertThat(MapTileProxyController.isFullDownloadAllowed(
                "world-lowzoom.pmtiles", "planet", "world-lowzoom")).isTrue();
        assertThat(MapTileProxyController.isFullDownloadAllowed(
                "planet.pmtiles", "planet", "world-lowzoom")).isFalse();
        assertThat(MapTileProxyController.isFullDownloadAllowed(
                "planet.pmtiles", "planet", "planet")).isFalse();
    }

    @Test
    void unsafeFullBodyResponseRejectsOversizedOkForRangeRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(10);

        assertThat(MapTileProxyController.isUnsafeFullBodyResponse(
                "bytes=0-4", SC_OK, headers)).isTrue();
    }

    @Test
    void unsafeFullBodyResponseRejectsOkWithoutContentLengthForRangeRequest() {
        assertThat(MapTileProxyController.isUnsafeFullBodyResponse(
                "bytes=0-4", SC_OK, new HttpHeaders())).isTrue();
    }

    @Test
    void unsafeFullBodyResponseAllowsOkWhenBodyFitsRequestedRange() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentLength(5);

        assertThat(MapTileProxyController.isUnsafeFullBodyResponse(
                "bytes=0-4", SC_OK, headers)).isFalse();
    }

    @Test
    void unsafeFullBodyResponseDoesNotRejectPartialContent() {
        assertThat(MapTileProxyController.isUnsafeFullBodyResponse(
                "bytes=0-4", SC_PARTIAL_CONTENT, new HttpHeaders())).isFalse();
    }

    @Test
    void cacheControlIsImmutableOnlyForSuccessfulTileStatuses() {
        assertThat(MapTileProxyController.cacheControlForStatus(SC_OK))
                .isEqualTo("public, max-age=2678400, immutable");
        assertThat(MapTileProxyController.cacheControlForStatus(SC_PARTIAL_CONTENT))
                .isEqualTo("public, max-age=2678400, immutable");
        assertThat(MapTileProxyController.cacheControlForStatus(SC_NOT_FOUND))
                .isEqualTo("no-store");
        assertThat(MapTileProxyController.cacheControlForStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE))
                .isEqualTo("no-store");
    }

    @Test
    void clientAbortDetectionMatchesNestedTomcatAbortExceptionName() {
        IOException wrapped = new IOException("stream failed", new ClientAbortException("Broken pipe"));

        assertThat(MapTileProxyController.isClientAbort(wrapped)).isTrue();
        assertThat(MapTileProxyController.isClientAbort(new IOException("upstream failed"))).isFalse();
    }

    private static class ClientAbortException extends IOException {

        ClientAbortException(String message) {
            super(message);
        }
    }
}
