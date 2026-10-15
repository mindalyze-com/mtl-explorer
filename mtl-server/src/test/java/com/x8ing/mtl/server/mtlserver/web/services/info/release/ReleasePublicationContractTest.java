package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import com.x8ing.mtl.server.mtlserver.web.services.info.DockerImageInfoService;
import com.x8ing.mtl.server.mtlserver.web.services.info.ImageVersionInfoDto;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.UpstreamBuildVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Run with the private tools/verify_release_mechanism.py producer/consumer harness. */
@EnabledIfSystemProperty(named = "mtl.releaseContractDirectory", matches = ".+")
class ReleasePublicationContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private JsonNode snapshot(MockMvc api) throws Exception {
        return mapper.readTree(api.perform(get("/api/info/updates").param("check", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode settled(MockMvc api) {
        return assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            JsonNode response;
            do {
                response = snapshot(api);
                if (response.path("checking").asBoolean()) Thread.sleep(10);
            } while (response.path("checking").asBoolean());
            return response;
        });
    }

    @Test
    void generatedCatalogTravelsThroughHttpConfigurationInventoryAndController() throws Exception {
        Path directory = Path.of(System.getProperty("mtl.releaseContractDirectory"));
        byte[] published = Files.readAllBytes(directory.resolve("releases.json"));
        var feedBody = new AtomicReference<>(published);
        var feedCode = new AtomicInteger(200);
        var feedRequests = new AtomicInteger();
        var searchRequests = new AtomicInteger();
        var unexpectedRequests = new AtomicInteger();
        var privacyFailure = new AtomicReference<String>();
        var searchBuild = new AtomicReference<>("2.0");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases.json", exchange -> {
            feedRequests.incrementAndGet();
            if (exchange.getRequestURI().getRawQuery() != null
                || exchange.getRequestHeaders().containsKey("Authorization")
                || exchange.getRequestHeaders().containsKey("Cookie")) privacyFailure.set("Private feed request data");
            byte[] body = feedBody.get();
            exchange.sendResponseHeaders(feedCode.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/search/status", exchange -> {
            searchRequests.incrementAndGet();
            var query = UriComponentsBuilder.fromUri(exchange.getRequestURI()).build().getQueryParams();
            boolean authorized = "contract-installation".equals(query.getFirst("mtl-server-id"))
                                 && "contract-version".equals(query.getFirst("mtl-version"));
            byte[] body = ("{\"versionInfo\":{\"image\":{\"version\":\"" + searchBuild.get() + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(authorized ? 200 : 403, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/", exchange -> {
            unexpectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        var client = new ReleaseFeedClient();
        ReleaseUpdateService service = null;
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
                    "MTL_UPDATES_ENABLED", "true", "MTL_UPDATES_CHANNEL", "stable",
                    "MTL_UPDATES_FEEDURL", base + "/releases.json",
                    "MTL_UPDATES_COMPONENTS_LOCATIONSEARCH_MANAGEMENT", "SELF_MANAGED",
                    "MTL_UPDATES_COMPONENTS_BROUTER_MANAGEMENT", "PROVIDER_MANAGED",
                    "MTL_UPDATES_COMPONENTS_MAPSERVER_MANAGEMENT", "DISABLED")));
            ConfigurationPropertySources.attach(environment);
            var properties = Binder.get(environment).bind("mtl.updates", Bindable.of(ReleaseUpdateProperties.class)).get();
            var planner = new PlannerProperties();
            planner.setEnabled(true);
            planner.setStatusUrl(base + "/provider/status");
            var search = new LocationSearchProperties();
            search.setEnabled(true);
            search.setStatusUrl(base + "/search/status");
            var maps = new MapServerProperties();
            maps.setStatusUrl(base + "/disabled/status");
            var identity = mock(ServerIdentityService.class);
            when(identity.getServerId()).thenReturn("contract-installation");
            var buildVersion = mock(UpstreamBuildVersionService.class);
            when(buildVersion.getVersion()).thenReturn("contract-version");
            var appBuild = new AtomicReference<>("1.0");
            var image = mock(DockerImageInfoService.class);
            when(image.getImageInfo()).thenAnswer(ignored -> ImageVersionInfoDto.of(appBuild.get(), "private-revision", null));
            var now = new AtomicReference<>(Instant.parse("2026-01-01T12:00:00Z"));
            var clock = mock(Clock.class);
            when(clock.instant()).thenAnswer(ignored -> now.get());
            var inventory = new ReleaseComponentInventory(properties, planner, search, maps, client, identity, buildVersion);
            service = new ReleaseUpdateService(properties, image, identity, client, clock, executor, inventory);
            var api = MockMvcBuilders.standaloneSetup(new ReleaseUpdateController(service)).build();
            var snapshots = new LinkedHashMap<String, JsonNode>();

            var available = settled(api);
            snapshots.put("componentAvailable", available);
            assertEquals("UPDATE_AVAILABLE", available.path("status").asText());
            assertEquals("0.9.0", available.path("installedReleaseVersion").asText());
            assertEquals("0.9.1", available.path("latestReleaseVersion").asText());
            assertFalse(available.path("checkFailed").asBoolean());
            settled(api);
            assertEquals(1, feedRequests.get(), "Manual checks must respect cooldown");

            searchBuild.set("2.1");
            now.updateAndGet(value -> value.plusSeconds(61));
            var componentInstalled = settled(api);
            snapshots.put("componentInstalled", componentInstalled);
            assertEquals("UP_TO_DATE", componentInstalled.path("status").asText());
            assertEquals("0.9.1", componentInstalled.path("installedReleaseVersion").asText());
            assertEquals(available.path("installedBuildId"), componentInstalled.path("installedBuildId"));
            assertNotEquals(available.path("installedComponentKey"), componentInstalled.path("installedComponentKey"));

            properties.setChannel("beta");
            now.updateAndGet(value -> value.plusSeconds(61));
            var appAvailable = settled(api);
            snapshots.put("appAvailable", appAvailable);
            assertEquals("UPDATE_AVAILABLE", appAvailable.path("status").asText());
            assertEquals("0.10.0", appAvailable.path("latestReleaseVersion").asText());
            appBuild.set("1.1");
            now.updateAndGet(value -> value.plusSeconds(61));
            var appInstalled = settled(api);
            snapshots.put("appInstalled", appInstalled);
            assertEquals("UP_TO_DATE", appInstalled.path("status").asText());
            assertEquals("0.10.0", appInstalled.path("installedReleaseVersion").asText());

            properties.setChannel("stable");
            now.updateAndGet(value -> value.plusSeconds(61));
            assertEquals("UP_TO_DATE", settled(api).path("status").asText(), "Never offer a downgrade");
            for (int code : new int[]{404, 200}) {
                feedCode.set(code);
                feedBody.set("{\"schemaVersion\":2}".getBytes(StandardCharsets.UTF_8));
                now.updateAndGet(value -> value.plusSeconds(61));
                var cached = settled(api);
                assertTrue(cached.path("checkFailed").asBoolean());
                assertEquals("0.9.1", cached.path("latestReleaseVersion").asText());
            }
            feedBody.set(published);
            now.updateAndGet(value -> value.plusSeconds(61));
            assertFalse(settled(api).path("checkFailed").asBoolean());
            appBuild.set("custom");
            assertEquals("UNKNOWN", settled(api).path("status").asText());

            properties.setEnabled(false);
            int requestsBeforeDisable = feedRequests.get() + searchRequests.get();
            now.updateAndGet(value -> value.plusSeconds(86400));
            assertEquals("DISABLED", settled(api).path("status").asText());
            assertEquals(requestsBeforeDisable, feedRequests.get() + searchRequests.get());
            assertEquals(0, unexpectedRequests.get(), "Do not probe provider-managed or disabled services");
            assertNull(privacyFailure.get());
            String serialized = mapper.writeValueAsString(snapshots);
            assertFalse(serialized.contains("private-revision"));
            Files.writeString(directory.resolve("api-snapshots.json"), serialized);
        } finally {
            if (service != null) service.close();
            executor.shutdownNow();
            client.close();
            server.stop(0);
        }
    }
}
