package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.config.MtlCacheConfig;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchTestSupport.parseQuery;
import static com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchTestSupport.writeJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        LocationSearchService.class,
        MtlCacheConfig.class,
        LocationSearchServiceCacheTest.TestConfig.class
})
class LocationSearchServiceCacheTest {

    private static final int TEST_TIMEOUT_MS = 1000;
    private static final String TEST_SERVER_ID = "test-server";

    @Autowired
    private LocationSearchService service;

    @Autowired
    private LocationSearchProperties properties;

    @Autowired
    private CacheManager cacheManager;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private AtomicInteger statusRequests;

    @BeforeEach
    void setUp() throws IOException {
        statusRequests = new AtomicInteger();
        startServer();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.setStatusUrl(baseUrl + "/status");
        properties.setQueryUrl(baseUrl + "/search");
        properties.setTimeoutMs(TEST_TIMEOUT_MS);
        statusCache().clear();
    }

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
    void statusUsesSpringCaffeineCache() {
        LocationSearchStatusDto first = service.getStatus();
        LocationSearchStatusDto second = service.getStatus();

        assertThat(first.isReady()).isTrue();
        assertThat(second).isSameAs(first);
        assertThat(statusRequests).hasValue(1);
    }

    @Test
    void statusRefetchesAfterCacheEviction() {
        service.getStatus();
        statusCache().clear();
        service.getStatus();

        assertThat(statusRequests).hasValue(2);
    }

    @Test
    void locationSearchStatusCacheExpiresAfterTenSecondsAndHoldsOneEntry() {
        Cache cache = statusCache();

        assertThat(cache).isInstanceOf(CaffeineCache.class);
        CaffeineCache caffeineCache = (CaffeineCache) cache;
        assertThat(caffeineCache.getNativeCache().policy().expireAfterWrite())
                .hasValueSatisfying(expiration -> assertThat(expiration.getExpiresAfter(TimeUnit.MILLISECONDS))
                        .isEqualTo(MtlCacheConfig.LOCATION_SEARCH_STATUS_TTL.toMillis()));
        assertThat(caffeineCache.getNativeCache().policy().eviction())
                .hasValueSatisfying(eviction -> assertThat(eviction.getMaximum())
                        .isEqualTo(MtlCacheConfig.LOCATION_SEARCH_STATUS_MAXIMUM_SIZE));
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> {
            statusRequests.incrementAndGet();
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            assertThat(params.get(MapProxyConstants.UPSTREAM_AUTH_VERSION_PARAM)).isNotBlank();
            assertThat(params.get(MapProxyConstants.UPSTREAM_AUTH_SERVER_ID_PARAM)).isEqualTo(TEST_SERVER_ID);
            writeJson(exchange, """
                    {
                      "phase": "ready",
                      "ready": true,
                      "message": "GeoNames location search ready.",
                      "row_count": 8,
                      "populated_place_count": 3,
                      "terrain_count": 5
                    }
                    """);
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private Cache statusCache() {
        Cache cache = cacheManager.getCache(MtlCacheConfig.LOCATION_SEARCH_STATUS_CACHE);
        assertThat(cache).isNotNull();
        return cache;
    }

    @Configuration
    static class TestConfig {

        @Bean
        LocationSearchProperties locationSearchProperties() {
            return new LocationSearchProperties();
        }

        @Bean
        ServerIdentityService serverIdentityService() {
            ServerIdentityService serverIdentityService = mock(ServerIdentityService.class);
            when(serverIdentityService.getServerId()).thenReturn(TEST_SERVER_ID);
            return serverIdentityService;
        }
    }
}
