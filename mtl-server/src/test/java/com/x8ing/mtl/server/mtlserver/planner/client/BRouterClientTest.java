package com.x8ing.mtl.server.mtlserver.planner.client;

import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.planner.dto.RouteRequestDto;
import com.x8ing.mtl.server.mtlserver.planner.dto.WaypointDto;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannerService;
import com.x8ing.mtl.server.mtlserver.planner.web.PlannerController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    void missingDirectoryReachesTheApiAsAnActionableServerFault() throws IOException {
        startBRouterServer(400, "segment directory /segments4 does not exist");
        PlannerProperties properties = properties();
        PlannerService service = new PlannerService(new BRouterClient(properties), properties);
        PlannerController controller = new PlannerController(service, null, null, properties, null);
        RouteRequestDto request = new RouteRequestDto();
        request.setProfile("trekking");
        request.setWaypoints(java.util.List.of(new WaypointDto(47, 8), new WaypointDto(47.01, 8.01)));

        var response = controller.route(request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(((Map<?, ?>) response.getBody()).get("error")).isEqualTo("routing-storage-unavailable");
        assertThat(((Map<?, ?>) response.getBody()).get("detail").toString())
                .contains("Restore the BRouter segment directory", "segment directory /segments4 does not exist");
    }

    @Test
    void rejectedRoutePreservesBRouterReason() throws IOException {
        startBRouterServer(400, "from-position not mapped");
        assertThatThrownBy(() -> new BRouterClient(properties()).computeLeg(
                new WaypointDto(47, 8), new WaypointDto(47.01, 8.01), "trekking"))
                .isInstanceOfSatisfying(BRouterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("routing-failed");
                    assertThat(exception.getMessage()).contains("from-position not mapped", "HTTP 400");
                });
    }

    @Test
    void upstreamHtmlIsNotPresentedAsARoutingReason() throws IOException {
        startBRouterServer(502, "<html><body>Proxy error</body></html>");
        assertThatThrownBy(() -> new BRouterClient(properties()).computeLeg(
                new WaypointDto(47, 8), new WaypointDto(47.01, 8.01), "trekking"))
                .hasMessage("Routing service failed (HTTP 502).");
    }

    @Test
    void timeoutIsDistinctFromAnUnroutableWaypoint() throws IOException {
        startBRouterServer(200, "{}", 200);
        PlannerProperties properties = properties();
        properties.setBrouterTimeoutMs(30);
        assertThatThrownBy(() -> new BRouterClient(properties).computeLeg(
                new WaypointDto(47, 8), new WaypointDto(47.01, 8.01), "trekking"))
                .isInstanceOfSatisfying(BRouterException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("routing-timeout");
                    assertThat(exception.getMessage()).contains("timed out");
                });
    }

    @Test
    void connectionFailureExplainsWhichServiceToCheck() throws IOException {
        startBRouterServer(200, "{}");
        PlannerProperties properties = properties();
        server.stop(0);
        assertThatThrownBy(() -> new BRouterClient(properties).computeLeg(
                new WaypointDto(47, 8), new WaypointDto(47.01, 8.01), "trekking"))
                .hasMessage("Cannot reach the routing service. Check that BRouter is running, then retry.");
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
    void calculatesBothTotalsFromGeometryInsteadOfTrustingUpstreamAscent() throws IOException {
        startBRouterServer(200, """
                {"features":[{"properties":{"track-length":"200","filtered ascend":"999","total-time":"60"},
                "geometry":{"coordinates":[[8,47,100],[8.001,47,99.5],[8.002,47,99],[8.003,47,98.5]]}}]}
                """);
        var leg = new BRouterClient(properties()).computeLeg(new WaypointDto(47, 8), new WaypointDto(47, 8.003), "trekking");
        assertThat(leg.getAscentM()).isZero();
        assertThat(leg.getDescentM()).isEqualTo(1.5);
        assertThat(leg.getDistanceM()).isEqualTo(200);
        assertThat(leg.getDurationSec()).isEqualTo(60);
    }

    @Test
    void normalizesReversedGeometryBeforeCalculatingElevation() throws IOException {
        startBRouterServer(200, """
                {"features":[{"properties":{},"geometry":{"coordinates":[[8.002,47,90],[8.001,47,95],[8,47,100]]}}]}
                """);
        var leg = new BRouterClient(properties()).computeLeg(new WaypointDto(47, 8), new WaypointDto(47, 8.002), "trekking");
        assertThat(leg.getCoordinates().getFirst()[0]).isEqualTo(8);
        assertThat(leg.getAscentM()).isZero();
        assertThat(leg.getDescentM()).isEqualTo(10);
    }

    @Test
    void missingElevationDoesNotCreateASeaLevelExcursion() throws IOException {
        startBRouterServer(200, """
                {"features":[{"properties":{},"geometry":{"coordinates":[[8,47,100],[8.001,47],[8.002,47,90]]}}]}
                """);
        var leg = new BRouterClient(properties()).computeLeg(new WaypointDto(47, 8), new WaypointDto(47, 8.002), "trekking");
        assertThat(leg.getAscentM()).isZero();
        assertThat(leg.getDescentM()).isZero();
    }

    @Test
    void blankErrorBodySummaryIsExplicit() {
        assertThat(BRouterClient.summarizeErrorBody(" \n\t ")).isEqualTo("<empty>");
    }

    private void startBRouterServer(int statusCode, String body) throws IOException {
        startBRouterServer(statusCode, body, 0);
    }

    private void startBRouterServer(int statusCode, String body, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/brouter", exchange -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted BRouter test response", e);
            }
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
