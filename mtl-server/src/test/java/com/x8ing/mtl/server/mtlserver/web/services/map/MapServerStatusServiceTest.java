package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MapServerStatusServiceTest {

    private static final int TEST_TIMEOUT_MS = 1000;
    private static final int STALE_RESPONSE_TIMEOUT_MS = 200;
    private static final String FIRST_ARCHIVE_ID = "first-archive";
    private static final String SECOND_ARCHIVE_ID = "second-archive";

    private HttpServer server;
    private ExecutorService serverExecutor;
    private ExecutorService clientExecutor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
        }
    }

    @Test
    void expiredCacheReturnsStaleStatusWhileRefreshIsInProgress() throws Exception {
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        CountDownLatch releaseSecondRequest = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();
        startStatusServer(requestCount, secondRequestStarted, releaseSecondRequest);

        MapServerStatusService service = new MapServerStatusService(
                mapServerProperties(),
                localUpstreamResolver());

        assertThat(service.getStatus().getArchiveId()).isEqualTo(FIRST_ARCHIVE_ID);
        ReflectionTestUtils.setField(service, "cacheExpiry", Instant.EPOCH);

        clientExecutor = Executors.newFixedThreadPool(2);
        Future<MapServerStatusDto> refreshingCall = clientExecutor.submit(service::getStatus);
        assertThat(secondRequestStarted.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        Future<MapServerStatusDto> staleCall = clientExecutor.submit(service::getStatus);
        try {
            MapServerStatusDto staleStatus = staleCall.get(STALE_RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            assertThat(staleStatus.getArchiveId()).isEqualTo(FIRST_ARCHIVE_ID);
        } finally {
            releaseSecondRequest.countDown();
        }

        assertThat(refreshingCall.get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS).getArchiveId())
                .isEqualTo(SECOND_ARCHIVE_ID);
    }

    private void startStatusServer(AtomicInteger requestCount,
                                   CountDownLatch secondRequestStarted,
                                   CountDownLatch releaseSecondRequest) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> {
            int currentRequest = requestCount.incrementAndGet();
            if (currentRequest == 2) {
                secondRequestStarted.countDown();
                awaitLatch(releaseSecondRequest);
            }
            writeStatus(exchange, currentRequest == 1 ? FIRST_ARCHIVE_ID : SECOND_ARCHIVE_ID);
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private MapServerProperties mapServerProperties() {
        MapServerProperties properties = new MapServerProperties();
        properties.setStatusUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/status");
        properties.setLocalProbeTimeoutMs(TEST_TIMEOUT_MS);
        return properties;
    }

    private static MapUpstreamResolver localUpstreamResolver() {
        return new MapUpstreamResolver(new MapServerProperties()) {
            @Override
            public MapUpstream resolveUpstream() {
                return new MapUpstream(MapUpstreamSource.LOCAL, "http://local");
            }
        };
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeStatus(HttpExchange exchange, String archiveId) throws IOException {
        String body = """
                {
                  "phase": "ready",
                  "ready": true,
                  "archive_id": "%s"
                }
                """.formatted(archiveId);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
