package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationSearchServiceTest {

    private static final int TEST_TIMEOUT_MS = 1000;
    private static final String TEST_SERVER_ID = "test-server";

    private HttpServer server;
    private ExecutorService serverExecutor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void statusDeserializesGeoNamesSidecarFields() throws Exception {
        startServer();

        LocationSearchStatusDto status = newService(properties()).getStatus();

        assertThat(status.isReady()).isTrue();
        assertThat(status.getPhase()).isEqualTo("ready");
        assertThat(status.getDbPath()).isEqualTo("/data/geonames-search.sqlite");
        assertThat(status.getSourceAttribution()).isEqualTo("GeoNames");
        assertThat(status.getSourceLicense()).isEqualTo("CC-BY 4.0");
        assertThat(status.getRowCount()).isEqualTo(8L);
        assertThat(status.getTerrainCount()).isEqualTo(5L);
        assertThat(status.getVersionInfo().getImage().getVersion()).isEqualTo("test-image");
        assertThat(status.getVersionInfo().getComponents()).containsEntry("sqlite", "3.53.1");
    }

    @Test
    void searchProxiesQueryAndClampsLimit() throws Exception {
        startServer();

        LocationSearchResponseDto response = newService(properties()).search("Zürich HB", 500);

        assertThat(response.isReady()).isTrue();
        assertThat(response.getQuery()).isEqualTo("Zürich HB");
        assertThat(response.getLimit()).isEqualTo(50);
        assertThat(response.getSort()).isEqualTo("importance");
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getDisplayName()).isEqualTo("Zürich");
        assertThat(response.getResults().get(0).getSourceLayer()).isEqualTo("places");
        assertThat(response.getResults().get(0).getAdmin1Name()).isEqualTo("Zürich");
        assertThat(response.getResults().get(0).getCountryName()).isEqualTo("Switzerland");
    }

    @Test
    void searchPassesDistanceSortOnlyWithValidCenter() throws Exception {
        startServer();

        LocationSearchResponseDto distance = newService(properties()).search("Zurich", 10, "distance", 47.38, 8.54);
        LocationSearchResponseDto fallback = newService(properties()).search("Zurich", 10, "distance", null, null);

        assertThat(distance.getSort()).isEqualTo("distance");
        assertThat(distance.getResults()).extracting(LocationSearchResultDto::getDisplayName)
                .containsExactly("Zürich", "Zurich");
        assertThat(distance.getResults().get(0).getDistanceMeters()).isLessThan(1_000L);
        assertThat(fallback.getSort()).isEqualTo("importance");
    }

    @Test
    void missingSidecarReturnsUnavailableResponse() {
        LocationSearchProperties properties = new LocationSearchProperties();
        properties.setStatusUrl("http://127.0.0.1:1/status");
        properties.setQueryUrl("http://127.0.0.1:1/search");
        properties.setTimeoutMs(TEST_TIMEOUT_MS);

        LocationSearchStatusDto status = newService(properties).getStatus();
        LocationSearchResponseDto response = newService(properties).search("Zurich", 10);

        assertThat(status.isReady()).isFalse();
        assertThat(status.getPhase()).isEqualTo("unavailable");
        assertThat(response.isReady()).isFalse();
        assertThat(response.getPhase()).isEqualTo("unavailable");
        assertThat(response.getResults()).isEmpty();
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            assertAuthParams(params);
            writeJson(exchange, 200, """
                {
                  "phase": "ready",
                  "ready": true,
                  "message": "GeoNames location search ready.",
                  "db_path": "/data/geonames-search.sqlite",
                  "source_build_date": "2026-05-24T00:00:00Z",
                  "source_attribution": "GeoNames",
                  "source_url": "https://www.geonames.org/",
                  "source_license": "CC-BY 4.0",
                  "source_license_url": "https://creativecommons.org/licenses/by/4.0/",
                  "sqlite_version": "3.53.1",
                  "row_count": 8,
                  "populated_place_count": 3,
                  "terrain_count": 5,
                  "versionInfo": {
                    "image": {
                      "version": "test-image",
                      "buildTime": "2026-05-24T00:00:00Z"
                    },
                    "components": {
                      "sqlite": "3.53.1"
                    },
                    "data": {
                      "sourceAttribution": "GeoNames"
                    }
                  }
                }
                """);
        });
        server.createContext("/search", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            assertAuthParams(params);
            String query = params.get("q");
            String sort = params.getOrDefault("sort", "importance");
            if ("Zürich HB".equals(query)) {
                assertThat(params.get("limit")).isEqualTo("50");
                writeJson(exchange, 200, searchResponse("Zürich HB", "importance", zurichJson()));
            } else if ("Zurich".equals(query) && "distance".equals(sort)) {
                assertThat(params.get("lat")).isEqualTo("47.38");
                assertThat(params.get("lon")).isEqualTo("8.54");
                writeJson(exchange, 200, searchResponse("Zurich", "distance", zurichJsonWithDistance() + "," + zurichUsJson()));
            } else if ("Zurich".equals(query)) {
                assertThat(params).doesNotContainKeys("lat", "lon");
                writeJson(exchange, 200, searchResponse("Zurich", "importance", zurichJson()));
            } else {
                writeJson(exchange, 200, """
                        {"query":"","normalized_query":"","limit":20,"sort":"importance","ready":false,"phase":"invalid-request","message":"unexpected query","results":[]}
                        """);
            }
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private LocationSearchProperties properties() {
        LocationSearchProperties properties = new LocationSearchProperties();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.setStatusUrl(baseUrl + "/status");
        properties.setQueryUrl(baseUrl + "/search");
        properties.setTimeoutMs(TEST_TIMEOUT_MS);
        return properties;
    }

    private static LocationSearchService newService(LocationSearchProperties properties) {
        ServerIdentityService serverIdentityService = mock(ServerIdentityService.class);
        when(serverIdentityService.getServerId()).thenReturn(TEST_SERVER_ID);
        return new LocationSearchService(properties, serverIdentityService, Optional.empty());
    }

    private static String searchResponse(String query, String sort, String resultsJson) {
        return """
                {
                  "query": "%s",
                  "normalized_query": "zurich",
                  "limit": 20,
                  "sort": "%s",
                  "ready": true,
                  "phase": "ready",
                  "message": "",
                  "results": [%s]
                }
                """.formatted(query, sort, resultsJson);
    }

    private static String zurichJson() {
        return """
                {
                  "display_name": "Zürich",
                  "source_layer": "places",
                  "kind": "city",
                  "kind_detail": "city",
                  "min_zoom": 7,
                  "max_zoom": 15,
                  "lat": 47.3769,
                  "lon": 8.5417,
                  "country_code": "CH",
                  "country_name": "Switzerland",
                  "admin1_code": "ZH",
                  "admin1_name": "Zürich",
                  "admin1_level": 4,
                  "lang": "local",
                  "name": "Zürich"
                }
                """;
    }

    private static String zurichJsonWithDistance() {
        return zurichJson().replace("\"name\": \"Zürich\"", "\"distance_meters\": 362,\n                  \"name\": \"Zürich\"");
    }

    private static String zurichUsJson() {
        return """
                {
                  "display_name": "Zurich",
                  "source_layer": "places",
                  "kind": "village",
                  "kind_detail": "village",
                  "min_zoom": 11,
                  "max_zoom": 15,
                  "lat": 41.2223,
                  "lon": -82.4885,
                  "distance_meters": 6860000,
                  "country_code": "US",
                  "country_name": "United States",
                  "admin1_code": "OH",
                  "admin1_name": "Ohio",
                  "admin1_level": 4,
                  "lang": "local",
                  "name": "Zurich"
                }
                """;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void assertAuthParams(Map<String, String> params) {
        assertThat(params.get(MapProxyConstants.UPSTREAM_AUTH_VERSION_PARAM)).isEqualTo("unknown");
        assertThat(params.get(MapProxyConstants.UPSTREAM_AUTH_SERVER_ID_PARAM)).isEqualTo(TEST_SERVER_ID);
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
