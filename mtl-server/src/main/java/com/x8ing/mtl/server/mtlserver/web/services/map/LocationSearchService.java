package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@JsonPropertyOrder({
        "properties",
        "serverIdentityService",
        "buildProperties",
        "restClient"
})
public class LocationSearchService {

    static final String SORT_IMPORTANCE = "importance";
    static final String SORT_DISTANCE = "distance";
    private static final String UNKNOWN_BUILD_VERSION = "unknown";

    private final LocationSearchProperties properties;
    private final ServerIdentityService serverIdentityService;
    private final Optional<BuildProperties> buildProperties;
    private final RestClient restClient;

    public LocationSearchService(LocationSearchProperties properties,
                                 ServerIdentityService serverIdentityService,
                                 Optional<BuildProperties> buildProperties) {
        this.properties = properties;
        this.serverIdentityService = serverIdentityService;
        this.buildProperties = buildProperties;
        this.restClient = buildClient(properties);
    }

    private static RestClient buildClient(LocationSearchProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public LocationSearchStatusDto getStatus() {
        if (!properties.isEnabled()) {
            return LocationSearchStatusDto.unavailable("disabled", "Location search is disabled.");
        }
        String statusUrl = properties.getStatusUrl();
        if (statusUrl == null || statusUrl.isBlank()) {
            return LocationSearchStatusDto.unavailable("unavailable", "Location search sidecar is not configured.");
        }
        try {
            LocationSearchStatusDto status = restClient.get()
                    .uri(sidecarUri(statusUrl).build().toUriString())
                    .retrieve()
                    .body(LocationSearchStatusDto.class);
            return status == null
                    ? LocationSearchStatusDto.unavailable("unavailable", "Location search status is empty.")
                    : status;
        } catch (Exception e) {
            log.debug("Location search sidecar status unreachable at {}: {}", statusUrl, e.getMessage());
            return LocationSearchStatusDto.unavailable("unavailable", "Location search sidecar is not reachable.");
        }
    }

    public LocationSearchResponseDto search(String query, Integer requestedLimit) {
        return search(query, requestedLimit, SORT_IMPORTANCE, null, null);
    }

    public LocationSearchResponseDto search(String query,
                                            Integer requestedLimit,
                                            String requestedSort,
                                            Double lat,
                                            Double lon) {
        String trimmed = query == null ? "" : query.trim();
        int limit = clampLimit(requestedLimit, properties);
        String sort = normalizeSort(requestedSort, lat, lon);
        if (!properties.isEnabled()) {
            return LocationSearchResponseDto.unavailable(trimmed, limit, sort, "disabled", "Location search is disabled.");
        }
        String queryUrl = properties.getQueryUrl();
        if (queryUrl == null || queryUrl.isBlank()) {
            return LocationSearchResponseDto.unavailable(trimmed, limit, sort, "unavailable", "Location search sidecar is not configured.");
        }

        UriComponentsBuilder uri = sidecarUri(queryUrl)
                .queryParam("q", trimmed)
                .queryParam("limit", limit)
                .queryParam("sort", sort);
        if (SORT_DISTANCE.equals(sort)) {
            uri.queryParam("lat", lat)
                    .queryParam("lon", lon);
        }

        try {
            LocationSearchResponseDto response = restClient.get()
                    .uri(uri.build().toUriString())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    })
                    .body(LocationSearchResponseDto.class);
            if (response == null) {
                return LocationSearchResponseDto.unavailable(trimmed, limit, sort, "unavailable", "Location search response is empty.");
            }
            response.setQuery(trimmed);
            response.setLimit(limit);
            response.setSort(sort);
            return response;
        } catch (Exception e) {
            log.debug("Location search query failed at {}: {}", queryUrl, e.getMessage());
            return LocationSearchResponseDto.unavailable(trimmed, limit, sort, "unavailable", "Location search query failed.");
        }
    }

    static int clampLimit(Integer requestedLimit, LocationSearchProperties properties) {
        if (requestedLimit == null) {
            return properties.getDefaultLimit();
        }
        return Math.max(1, Math.min(requestedLimit, properties.getMaxLimit()));
    }

    private static String normalizeSort(String requestedSort, Double lat, Double lon) {
        if (requestedSort == null || requestedSort.isBlank()) {
            return SORT_IMPORTANCE;
        }
        String normalized = requestedSort.trim().toLowerCase(Locale.ROOT);
        if (SORT_DISTANCE.equals(normalized) && hasValidCoordinate(lat, lon)) {
            return SORT_DISTANCE;
        }
        return SORT_IMPORTANCE;
    }

    private static boolean hasValidCoordinate(Double lat, Double lon) {
        return lat != null
               && lon != null
               && Double.isFinite(lat)
               && Double.isFinite(lon)
               && lat >= -90.0
               && lat <= 90.0
               && lon >= -180.0
               && lon <= 180.0;
    }

    private UriComponentsBuilder sidecarUri(String url) {
        return UriComponentsBuilder.fromUriString(url)
                .queryParam(MapProxyConstants.UPSTREAM_AUTH_VERSION_PARAM, buildVersion())
                .queryParam(MapProxyConstants.UPSTREAM_AUTH_SERVER_ID_PARAM, serverIdentityService.getServerId());
    }

    private String buildVersion() {
        return buildProperties
                .map(MapTileProxyController::buildVersionParam)
                .orElse(UNKNOWN_BUILD_VERSION);
    }
}
