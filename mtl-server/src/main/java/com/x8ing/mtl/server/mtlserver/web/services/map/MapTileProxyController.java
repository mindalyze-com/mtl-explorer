package com.x8ing.mtl.server.mtlserver.web.services.map;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;

/**
 * Proxies HTTP Range requests for map tile files from the browser to the internal
 * map-server container. The browser never needs to know the Docker-internal address.
 * <p>
 * Endpoint: GET /api/map-proxy/{scope}/{filename}
 * - scope must be "demo" or "prod" — rejects anything else with 400.
 * - Rejects filenames containing path traversal characters (e.g. "../", "%2e%2e").
 * - Rejects filenames containing any path separator (/ or \).
 * - Forwards the Range header and streams back the partial / full response.
 * - Requires authentication (same JWT as all other /api/** endpoints).
 */
@Slf4j
@RestController
@RequestMapping("/api/map-proxy")
public class MapTileProxyController {

    private final RestClient restClient;
    private final MapServerProperties properties;

    public MapTileProxyController(MapServerProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @GetMapping("/{scope}/{filename:.+}")
    public void proxyTile(
            @PathVariable String scope,
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletResponse servletResponse) throws IOException {

        if (!("demo".equals(scope) || "prod".equals(scope))) {
            log.warn("Rejected tile proxy request with invalid scope: {}", scope);
            servletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid scope: must be 'demo' or 'prod'");
            return;
        }

        if (isSuspiciousFilename(filename)) {
            log.warn("Rejected suspicious tile proxy request for filename: {}", filename);
            servletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String upstreamUrl = properties.getTileUpstreamUrl() + "/" + scope + "/" + filename;
        log.debug("Proxying tile request: {} (Range: {})", upstreamUrl, rangeHeader);

        try {
            restClient.get()
                    .uri(upstreamUrl)
                    .headers(h -> {
                        if (rangeHeader != null) {
                            h.set(HttpHeaders.RANGE, rangeHeader);
                        }
                    })
                    .<Void>exchange((req, clientResponse) -> {
                        servletResponse.setStatus(clientResponse.getStatusCode().value());

                        forwardHeader(clientResponse.getHeaders(), HttpHeaders.CONTENT_TYPE, servletResponse);
                        forwardHeader(clientResponse.getHeaders(), HttpHeaders.CONTENT_RANGE, servletResponse);
                        forwardHeader(clientResponse.getHeaders(), HttpHeaders.CONTENT_LENGTH, servletResponse);
                        forwardHeader(clientResponse.getHeaders(), HttpHeaders.ACCEPT_RANGES, servletResponse);
                        forwardHeader(clientResponse.getHeaders(), HttpHeaders.ETAG, servletResponse);
                        forwardHeader(clientResponse.getHeaders(), HttpHeaders.LAST_MODIFIED, servletResponse);

                        // Override Spring Security's default no-cache headers — tile files are
                        // immutable binary data that never change for a given filename.
                        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=2678400, immutable"); // 31 days
                        servletResponse.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

                        try (InputStream body = clientResponse.getBody()) {
                            body.transferTo(servletResponse.getOutputStream());
                        }
                        servletResponse.flushBuffer();
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Tile proxy failed for {}: {}", filename, e.getMessage());
            if (!servletResponse.isCommitted()) {
                servletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Map tile server unavailable");
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
}
