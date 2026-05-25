package com.x8ing.mtl.server.mtlserver.planner.client;

import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.dto.WaypointDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BRouterClientTest {

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
    void segmentMissingResponseStillTriggersSpecificException() throws IOException {
        startBRouterServer(400, "datafile E5_N45.rd5 not found");

        BRouterClient client = new BRouterClient(properties());

        assertThatThrownBy(() -> client.computeLeg(
                new WaypointDto(47.57649313009003, 8.492245131153055),
                new WaypointDto(47.57556669249894, 8.501343184132395),
                "trekking"))
                .isInstanceOf(BRouterSegmentMissingException.class)
                .hasMessage("Routing data segment not available");
    }

    @Test
    void errorBodySummaryKeepsBRouterText() {
        String body = """
                Routing failed near 8.492245131153055,47.57649313009003
                with\tunexpected   BRouter detail
                """;

        String summary = BRouterClient.summarizeErrorBody(body);

        assertThat(summary).isEqualTo("Routing failed near 8.492245131153055,47.57649313009003\nwith\tunexpected   BRouter detail");
    }

    @Test
    void blankErrorBodySummaryIsExplicit() {
        assertThat(BRouterClient.summarizeErrorBody(" \n\t ")).isEqualTo("<empty>");
    }

    private void startBRouterServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/brouter", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        serverExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(serverExecutor);
        server.start();
    }

    private PlannerProperties properties() {
        PlannerProperties properties = new PlannerProperties();
        properties.setBrouterBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
    }
}
