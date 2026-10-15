package com.x8ing.mtl.server.mtlserver.planner.service;

import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.dto.BRouterStatusDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerStatusServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void appliesConfiguredBrouterTimeoutToStatusRequests() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/status", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        PlannerProperties properties = new PlannerProperties();
        properties.setBrouterTimeoutMs(50);
        properties.setStatusUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/status");
        PlannerStatusService service = new PlannerStatusService(properties);

        long startedAt = System.nanoTime();
        BRouterStatusDto status = service.fetchStatus();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(status.isAvailable()).isFalse();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
    }
}
