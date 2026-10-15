package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseFeedClientTest {
    private static final String EMPTY_FEED = "{\"schemaVersion\":1,\"channels\":{},\"releases\":{}}";
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode release(String build) {
        var release = mapper.createObjectNode();
        release.put("buildId", build).put("image", "example/mtl:" + build)
                .put("digest", "sha256:" + "a".repeat(64)).put("summary", "Changes")
                .put("releaseNotesUrl", "https://example.com/notes/")
                .put("updateGuideUrl", "https://example.com/update/").put("publishedOn", "2026-10-03");
        return release;
    }

    private ObjectNode feed() throws Exception {
        var feed = (ObjectNode) mapper.readTree(EMPTY_FEED);
        feed.withObject("channels").put("stable", "0.9.0");
        feed.withObject("releases").set("0.9.0", release("1.508"));
        return feed;
    }

    private ReleaseFeedClient.Feed parse(ObjectNode feed) throws Exception {
        var client = new ReleaseFeedClient();
        try {
            return client.parse(mapper.writeValueAsBytes(feed));
        } finally {
            client.close();
        }
    }

    @Test
    void unknownExtensionsAndMalformedEntriesLeaveValidReleasesUsable() throws Exception {
        var feed = feed();
        var release = (ObjectNode) feed.path("releases").path("0.9.0");
        var app = mapper.createObjectNode();
        for (String key : java.util.List.of("buildId", "image", "digest")) app.set(key, release.get(key));
        release.putObject("components").set("app", app);
        release.withObject("components").putArray("future-component").add("future format");
        feed.withObject("channels").putObject("nightly").put("future", true);
        var releases = feed.withObject("releases");
        releases.set("invalid-version", release("1.509"));
        releases.set("0.9.1", release("1.509").put("publishedOn", "not-a-date"));
        releases.set("0.9.2", release("1.510").put("digest", "invalid"));
        releases.set("0.9.3", release("1.511").putNull("releaseNotesUrl"));
        releases.set("0.9.4", release("1.512").put("updateGuideUrl", "https://user:secret@example.com/"));
        releases.putArray("0.9.5").add("invalid shape");
        releases.set("0.9.6", release("1.513").putNull("buildId"));
        releases.set("0.9.7", release("1.514").put("releaseNotesUrl", "javascript:alert(1)"));
        releases.putNull("0.9.8");
        var parsed = parse(feed);
        assertEquals(java.util.Set.of("0.9.0"), parsed.releases().keySet());
        assertEquals(java.util.Map.of("stable", "0.9.0"), parsed.channels());
        assertEquals(java.util.Set.of("app"), parsed.releases().get("0.9.0").artifacts().keySet());
    }

    @Test
    void malformedKnownComponentDiscardsOnlyItsRelease() throws Exception {
        var feed = feed();
        var invalid = release("1.509");
        invalid.putObject("components").putArray("location-search").add("invalid shape");
        feed.withObject("releases").set("0.9.1", invalid);
        assertEquals(java.util.Set.of("0.9.0"), parse(feed).releases().keySet());
    }

    @Test
    void publicationDateDoesNotDependOnConsumerClockOrTimeZone() throws Exception {
        var feed = feed();
        ((ObjectNode) feed.path("releases").path("0.9.0")).put("publishedOn", java.time.LocalDate.now().plusDays(1).toString());
        assertEquals("0.9.0", parse(feed).channels().get("stable"));
    }

    @Test
    void conflictingBindingsQuarantineAllAffectedReleasesInEitherOrder() throws Exception {
        for (boolean reversed : new boolean[]{false, true}) {
            var feed = feed();
            var releases = feed.withObject("releases");
            var first = release("1.509");
            var second = release("1.509").put("digest", "sha256:" + "b".repeat(64));
            releases.set(reversed ? "0.9.2" : "0.9.1", reversed ? second : first);
            releases.set(reversed ? "0.9.1" : "0.9.2", reversed ? first : second);
            feed.withObject("channels").put("beta", "0.9.2");
            var parsed = parse(feed);
            assertEquals(java.util.Set.of("0.9.0"), parsed.releases().keySet());
            assertEquals(java.util.Map.of("stable", "0.9.0"), parsed.channels());
        }
    }

    @Test
    void invalidChannelPointersAreIgnoredWithoutLosingOtherChannels() throws Exception {
        var feed = feed();
        feed.withObject("releases").set("0.10.0-beta.1", release("1.509"));
        feed.withObject("channels").put("stable", "0.10.0-beta.1").put("beta", "0.10.0-beta.1").put("alpha", "0.11.0");
        assertEquals(java.util.Map.of("beta", "0.10.0-beta.1"), parse(feed).channels());
    }

    @Test
    void brokenEnvelopeIsStillRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parse(feed().put("schemaVersion", 2)));
        assertThrows(IllegalArgumentException.class, () -> parse(feed().putNull("channels")));
        var feed = feed();
        feed.putArray("releases");
        assertThrows(IllegalArgumentException.class, () -> parse(feed));
    }

    @Test
    void acceptsValidFeedAndRejectsBadResponses() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/valid", exchange -> {
            byte[] body = EMPTY_FEED.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.createContext("/component", exchange -> {
            byte[] body = "{\"versionInfo\":{\"image\":{\"version\":\"2.1\"}}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.createContext("/blocked", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.createContext("/malformed", exchange -> {
            exchange.sendResponseHeaders(200, 3);
            try (var output = exchange.getResponseBody()) {
                output.write("bad".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.createContext("/oversized", exchange -> {
            byte[] body = new byte[300 * 1024];
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        var client = new ReleaseFeedClient();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            assertTrue(client.fetch(url + "/valid").releases().isEmpty());
            assertEquals("2.1", client.fetchBuildId(url + "/component"));
            assertThrows(IOException.class, () -> client.fetchBuildId(url + "/valid"));
            assertThrows(IOException.class, () -> client.fetch(url + "/blocked"));
            assertThrows(IOException.class, () -> client.fetch(url + "/malformed"));
            assertThrows(IOException.class, () -> client.fetch(url + "/oversized"));
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    void refusedConnectionIsRecoverable() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var client = new ReleaseFeedClient(Duration.ofSeconds(1));
        try {
            assertThrows(IOException.class, () -> client.fetch("http://127.0.0.1:" + closedPort));
        } finally {
            client.close();
        }
    }

    @Test
    void totalDeadlineAlsoBoundsAStalledBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/stalled", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write('{');
                output.flush();
                Thread.sleep(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        var client = new ReleaseFeedClient(Duration.ofMillis(250));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertThrows(IOException.class,
                    () -> client.fetch("http://127.0.0.1:" + server.getAddress().getPort() + "/stalled")));
        } finally {
            client.close();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
