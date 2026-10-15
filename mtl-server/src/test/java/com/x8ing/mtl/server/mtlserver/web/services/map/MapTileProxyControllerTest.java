package com.x8ing.mtl.server.mtlserver.web.services.map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.io.IOException;

import static jakarta.servlet.http.HttpServletResponse.*;
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
    void buildUpstreamUrlEncodesPrivateQueryParameters() {
        String upstreamUrl = MapTileProxyController.buildUpstreamUrl(
                "https://maps.example.test",
                "prod",
                "world-lowzoom.pmtiles",
                "0.0.1 SNAPSHOT:local/build",
                "2026-10-06T10:20:30+02:00",
                "server:id/1",
                true,
                "login:ref/1");

        assertThat(upstreamUrl)
                .isEqualTo("https://maps.example.test/prod/world-lowzoom.pmtiles"
                           + "?mtl-version=0.0.1%20SNAPSHOT%3Alocal%2Fbuild"
                           + "&mtl-build=2026-10-06T10%3A20%3A30%2B02%3A00"
                           + "&mtl-server-id=server%3Aid%2F1"
                           + "&mtl-instance-type=demo"
                           + "&mtl-client-login-ref=login%3Aref%2F1");
    }

    @Test
    void buildUpstreamUrlOmitsMissingClientLoginRef() {
        String upstreamUrl = MapTileProxyController.buildUpstreamUrl(
                "https://maps.example.test",
                "prod",
                "planet.pmtiles",
                "1.0.0",
                null,
                "server-1",
                false,
                null);

        assertThat(upstreamUrl)
                .isEqualTo("https://maps.example.test/prod/planet.pmtiles"
                           + "?mtl-version=1.0.0&mtl-server-id=server-1&mtl-instance-type=standard");
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
