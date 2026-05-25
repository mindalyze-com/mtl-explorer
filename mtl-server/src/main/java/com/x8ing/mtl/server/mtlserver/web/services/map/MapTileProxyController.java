package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Proxies HTTP Range requests for map tile files from the browser to the internal
 * map-server container. The browser never needs to know the Docker-internal address.
 * <p>
 * Endpoint: GET /api/map-proxy/{scope}/{filename}
 * - scope must be "prod" — rejects anything else with 400.
 * - Rejects filenames containing path traversal characters (e.g. "../", "%2e%2e").
 * - Rejects filenames containing any path separator (/ or \).
 * - Forwards the Range header and streams back the partial / full response.
 * - Requires authentication (same JWT as all other /api/** endpoints).
 * <p>
 * The proxy supports dual-mode PMTiles serving. Browser-visible map-source and
 * archive query parameters are cache identity only; the Java backend still owns
 * the actual upstream selection and privately appends the maintainer fallback
 * metadata expected by docker-maps. The hosted public upstream is provided for
 * MTL Explorer only and is not a general tile service.
 */
@Slf4j
@RestController
@RequestMapping("/api/map-proxy")
@JsonPropertyOrder({
        "restClient",
        "okHttpClient",
        "properties",
        "upstreamResolver",
        "statusService",
        "serverIdentityService",
        "buildProperties"
})
public class MapTileProxyController {

    private static final String UNKNOWN_BUILD_VERSION = "unknown";
    private static final String BUILD_VERSION_TIME_SEPARATOR = "_";
    private static final String UPSTREAM_AUTH_TOKEN_UNSAFE_CHARS_REGEX = "[^A-Za-z0-9._-]";
    private static final String UPSTREAM_AUTH_TOKEN_REPLACEMENT = "_";
    private static final String PMTILES_EXTENSION = ".pmtiles";
    private static final String BYTE_RANGE_PREFIX = "bytes=";
    private static final String CACHE_CONTROL_IMMUTABLE = "public, max-age=2678400, immutable";
    private static final String CACHE_CONTROL_NO_STORE = "no-store";
    private static final String CLIENT_ABORT_EXCEPTION_NAME = "ClientAbortException";

    private final RestClient restClient;
    private final OkHttpClient okHttpClient;
    private final MapServerProperties properties;
    private final MapUpstreamResolver upstreamResolver;
    private final MapServerStatusService statusService;
    private final ServerIdentityService serverIdentityService;
    private final Optional<BuildProperties> buildProperties;

    public MapTileProxyController(MapServerProperties properties,
                                  MapUpstreamResolver upstreamResolver,
                                  MapServerStatusService statusService,
                                  ServerIdentityService serverIdentityService,
                                  Optional<BuildProperties> buildProperties) {
        this.properties = properties;
        this.upstreamResolver = upstreamResolver;
        this.statusService = statusService;
        this.serverIdentityService = serverIdentityService;
        this.buildProperties = buildProperties;
        this.okHttpClient = buildOkHttpClient(properties);
        this.restClient = buildRestClient(okHttpClient);
    }

    static OkHttpClient buildOkHttpClient(MapServerProperties properties) {
        ConnectionPool pool = new ConnectionPool(
                properties.getProxyMaxIdleConnections(),
                properties.getProxyKeepAliveDurationSeconds(),
                TimeUnit.SECONDS);
        return new OkHttpClient.Builder()
                .connectionPool(pool)
                .callTimeout(Duration.ofMillis(properties.getProxyCallTimeoutMs()))
                .connectTimeout(Duration.ofMillis(properties.getProxyConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getProxyReadTimeoutMs()))
                .build();
    }

    private static RestClient buildRestClient(OkHttpClient okHttpClient) {
        return RestClient.builder()
                .requestFactory(new OkHttp3ClientHttpRequestFactory(okHttpClient))
                .build();
    }

    @PreDestroy
    public void shutdownHttpClient() {
        // Sync proxy calls run on servlet request threads; this only stops async dispatcher work and clears idle sockets.
        okHttpClient.dispatcher().executorService().shutdown();
        okHttpClient.connectionPool().evictAll();
    }

    @GetMapping("/{scope}/{filename:.+}")
    public void proxyTile(
            @PathVariable String scope,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestParam(value = MapProxyConstants.CACHE_SOURCE_PARAM, required = false) String requestedSource,
            @RequestParam(value = MapProxyConstants.CACHE_ARCHIVE_PARAM, required = false) String requestedArchive,
            HttpServletResponse servletResponse) throws IOException {

        if (!MapProxyConstants.SCOPE_PROD.equals(scope)) {
            log.warn("Rejected tile proxy request with invalid scope: {}", scope);
            sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_REQUEST, "Invalid scope: must be 'prod'");
            return;
        }

        if (isSuspiciousFilename(filename)) {
            log.warn("Rejected suspicious tile proxy request for filename: {}", filename);
            sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (hasPartialCacheIdentity(requestedSource, requestedArchive)) {
            sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_REQUEST,
                    "Map source and archive parameters must be provided together");
            return;
        }

        if (isBlank(rangeHeader) && !isFullDownloadAllowed(
                filename, properties.getTilesetName(), properties.getLowzoomTilesetName())) {
            log.warn("Rejected full PMTiles proxy request for filename without Range: {}", filename);
            sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_REQUEST,
                    "Range header required for this map archive");
            return;
        }

        upstreamResolver.markTileRequestActivity();
        MapUpstreamSource requestedUpstreamSource = null;
        try {
            if (requestedSource != null && !requestedSource.isBlank()) {
                requestedUpstreamSource = MapUpstreamSource.fromCacheValue(requestedSource);
            }
        } catch (IllegalArgumentException e) {
            sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        MapUpstream upstream = upstreamResolver.resolveUpstream();
        if (requestedUpstreamSource != null && requestedUpstreamSource != upstream.source()) {
            log.debug("Rejected stale map source request: requested={}, current={}",
                    requestedUpstreamSource.cacheValue(), upstream.source().cacheValue());
            sendNoStoreError(servletResponse, HttpServletResponse.SC_CONFLICT,
                    "Map source changed; refresh map config");
            return;
        }
        if (requestedArchive != null && !requestedArchive.isBlank()) {
            String currentArchive = statusService.archiveIdFor(upstream.source());
            if (!requestedArchive.equals(currentArchive)) {
                log.debug("Rejected stale map archive request: requested={}, current={}, source={}",
                        requestedArchive, currentArchive, upstream.source().cacheValue());
                sendNoStoreError(servletResponse, HttpServletResponse.SC_CONFLICT,
                        "Map archive changed; refresh map config");
                return;
            }
        }

        String upstreamUrl = buildUpstreamUrl(upstream, scope, filename);
        log.debug("Proxying tile request: source={}, archive={}, file={}, Range={}",
                upstream.source().cacheValue(), requestedArchive, filename, rangeHeader);

        try {
            restClient.get()
                    .uri(upstreamUrl)
                    .headers(h -> {
                        if (!isBlank(rangeHeader)) {
                            h.set(HttpHeaders.RANGE, rangeHeader);
                        }
                    })
                    .<Void>exchange((req, clientResponse) -> {
                        int upstreamStatus = clientResponse.getStatusCode().value();
                        try (InputStream body = clientResponse.getBody()) {
                            if (isUnsafeFullBodyResponse(rangeHeader, upstreamStatus, clientResponse.getHeaders())) {
                                log.warn("Rejected oversized upstream 200 response for ranged tile request: file={}, Range={}, Content-Length={}",
                                        filename, rangeHeader, clientResponse.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH));
                                sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_GATEWAY,
                                        "Map tile upstream ignored byte range");
                                return null;
                            }

                            servletResponse.setStatus(upstreamStatus);

                            forwardHeader(clientResponse.getHeaders(), HttpHeaders.CONTENT_TYPE, servletResponse);
                            forwardHeader(clientResponse.getHeaders(), HttpHeaders.CONTENT_RANGE, servletResponse);
                            forwardHeader(clientResponse.getHeaders(), HttpHeaders.CONTENT_LENGTH, servletResponse);
                            forwardHeader(clientResponse.getHeaders(), HttpHeaders.ETAG, servletResponse);
                            forwardHeader(clientResponse.getHeaders(), HttpHeaders.LAST_MODIFIED, servletResponse);

                            applyProxyCacheHeaders(upstreamStatus, servletResponse);

                            body.transferTo(servletResponse.getOutputStream());
                        }
                        servletResponse.flushBuffer();
                        return null;
                    });
        } catch (Exception e) {
            if (isClientAbort(e)) {
                log.debug("Tile proxy client disconnected while streaming {}: {}", filename, e.getMessage());
                return;
            }
            log.warn("Tile proxy failed for {}: {}", filename, e.getMessage());
            if (!servletResponse.isCommitted()) {
                sendNoStoreError(servletResponse, HttpServletResponse.SC_BAD_GATEWAY,
                        "Map tile server unavailable");
            }
        }
    }

    /**
     * Returns true if the filename looks like a path traversal attempt.
     * Checks both raw and URL-decoded forms to catch percent-encoded variants.
     */
    private boolean isSuspiciousFilename(String filename) {
        if (filename == null || filename.isBlank()) return true;
        // Reject path separators — the filename must be a plain name, not a path
        if (filename.contains("/") || filename.contains("\\")) return true;
        // Reject dot-dot sequences
        if (filename.contains("..")) return true;
        // Reject percent-encoded traversal variants (case-insensitive)
        String lower = filename.toLowerCase();
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) return true;
        return false;
    }

    private void forwardHeader(HttpHeaders headers, String name, HttpServletResponse response) {
        String value = headers.getFirst(name);
        if (value != null) {
            response.setHeader(name, value);
        }
    }

    private void applyProxyCacheHeaders(int status, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControlForStatus(status));
        if (isCacheableTileStatus(status)) {
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        }
    }

    private void sendNoStoreError(HttpServletResponse response, int status) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
        response.sendError(status);
    }

    private void sendNoStoreError(HttpServletResponse response, int status, String message) throws IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
        response.sendError(status, message);
    }

    private boolean hasPartialCacheIdentity(String requestedSource, String requestedArchive) {
        boolean hasSource = !isBlank(requestedSource);
        boolean hasArchive = !isBlank(requestedArchive);
        return hasSource != hasArchive;
    }

    static boolean isFullDownloadAllowed(String filename, String tilesetName, String lowzoomTilesetName) {
        String lowzoomArchive = archiveFilename(lowzoomTilesetName);
        if (lowzoomArchive == null || !lowzoomArchive.equals(filename)) {
            return false;
        }
        String mainArchive = archiveFilename(tilesetName);
        return !lowzoomArchive.equals(mainArchive);
    }

    static boolean isUnsafeFullBodyResponse(String rangeHeader, int upstreamStatus, HttpHeaders upstreamHeaders) {
        if (isBlank(rangeHeader) || upstreamStatus != HttpServletResponse.SC_OK) {
            return false;
        }
        OptionalLong requestedLength = parseSingleByteRangeLength(rangeHeader);
        long contentLength = upstreamHeaders.getContentLength();
        return requestedLength.isEmpty() || contentLength < 0 || contentLength > requestedLength.getAsLong();
    }

    static String cacheControlForStatus(int status) {
        return isCacheableTileStatus(status)
                ? CACHE_CONTROL_IMMUTABLE
                : CACHE_CONTROL_NO_STORE;
    }

    private static boolean isCacheableTileStatus(int status) {
        return status == HttpServletResponse.SC_OK || status == HttpServletResponse.SC_PARTIAL_CONTENT;
    }

    private static OptionalLong parseSingleByteRangeLength(String rangeHeader) {
        if (isBlank(rangeHeader)) {
            return OptionalLong.empty();
        }
        String trimmedRangeHeader = rangeHeader.trim();
        if (!trimmedRangeHeader.regionMatches(true, 0, BYTE_RANGE_PREFIX, 0, BYTE_RANGE_PREFIX.length())) {
            return OptionalLong.empty();
        }
        String rangeSpec = trimmedRangeHeader.substring(BYTE_RANGE_PREFIX.length()).trim();
        if (rangeSpec.contains(",")) {
            return OptionalLong.empty();
        }
        int dashIndex = rangeSpec.indexOf('-');
        if (dashIndex < 0) {
            return OptionalLong.empty();
        }

        String startPart = rangeSpec.substring(0, dashIndex).trim();
        String endPart = rangeSpec.substring(dashIndex + 1).trim();
        try {
            if (startPart.isEmpty()) {
                long suffixLength = Long.parseLong(endPart);
                return suffixLength > 0 ? OptionalLong.of(suffixLength) : OptionalLong.empty();
            }
            if (endPart.isEmpty()) {
                return OptionalLong.empty();
            }

            long start = Long.parseLong(startPart);
            long end = Long.parseLong(endPart);
            if (start < 0 || end < start) {
                return OptionalLong.empty();
            }
            long span = end - start;
            if (span == Long.MAX_VALUE) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(span + 1);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static String archiveFilename(String tilesetName) {
        return isBlank(tilesetName) ? null : tilesetName + PMTILES_EXTENSION;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (CLIENT_ABORT_EXCEPTION_NAME.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String buildUpstreamUrl(MapUpstream upstream, String scope, String filename) {
        return buildUpstreamUrl(
                upstream.baseUrl(),
                scope,
                filename,
                buildVersion(),
                serverIdentityService.getServerId());
    }

    static String buildUpstreamUrl(String upstreamBaseUrl,
                                   String scope,
                                   String filename,
                                   String version,
                                   String serverId) {
        return UriComponentsBuilder
                .fromUriString(upstreamBaseUrl)
                .pathSegment(scope, filename)
                .queryParam(MapProxyConstants.UPSTREAM_AUTH_VERSION_PARAM, encodeUpstreamQueryParam(version))
                .queryParam(MapProxyConstants.UPSTREAM_AUTH_SERVER_ID_PARAM, encodeUpstreamQueryParam(serverId))
                .build(true)
                .toUriString();
    }

    private String buildVersion() {
        return buildProperties
                .map(MapTileProxyController::buildVersionParam)
                .orElse(UNKNOWN_BUILD_VERSION);
    }

    static String buildVersionParam(BuildProperties properties) {
        String version = properties.getVersion();
        String normalizedVersion = version == null || version.isBlank()
                ? UNKNOWN_BUILD_VERSION
                : sanitizeUpstreamAuthTokenPart(version);
        if (properties.getTime() == null) {
            return normalizedVersion;
        }
        return normalizedVersion
               + BUILD_VERSION_TIME_SEPARATOR
               + sanitizeUpstreamAuthTokenPart(properties.getTime().truncatedTo(ChronoUnit.SECONDS).toString());
    }

    private static String sanitizeUpstreamAuthTokenPart(String value) {
        return value.trim().replaceAll(UPSTREAM_AUTH_TOKEN_UNSAFE_CHARS_REGEX, UPSTREAM_AUTH_TOKEN_REPLACEMENT);
    }

    private static String encodeUpstreamQueryParam(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
