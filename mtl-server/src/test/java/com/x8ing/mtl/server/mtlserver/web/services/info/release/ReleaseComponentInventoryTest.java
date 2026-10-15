package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties;
import com.x8ing.mtl.server.mtlserver.config.ReleaseUpdateProperties.Management;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerProperties;
import com.x8ing.mtl.server.mtlserver.web.services.map.UpstreamBuildVersionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseComponentInventoryTest {
    private final ReleaseUpdateProperties properties = new ReleaseUpdateProperties();
    private final PlannerProperties planner = new PlannerProperties();
    private final LocationSearchProperties search = new LocationSearchProperties();
    private final MapServerProperties maps = new MapServerProperties();
    private final ReleaseFeedClient client = mock(ReleaseFeedClient.class);
    private final ServerIdentityService identity = mock(ServerIdentityService.class);
    private final UpstreamBuildVersionService buildVersion = mock(UpstreamBuildVersionService.class);
    private final Instant now = Instant.parse("2026-10-03T12:00:00Z");

    @BeforeEach
    void backendIdentity() {
        when(identity.getServerId()).thenReturn("server-a");
        when(buildVersion.getVersion()).thenReturn("test-1");
        when(buildVersion.getBuild()).thenReturn("build-1");
    }

    private String searchStatusUrl() {
        return search.getStatusUrl() + "?mtl-version=test-1&mtl-server-id=server-a&mtl-build=build-1";
    }

    private void management(String id, Management management) {
        properties.component(id).setManagement(management);
    }

    private ReleaseComponentInventory inventory() {
        return new ReleaseComponentInventory(properties, planner, search, maps, client, identity, buildVersion);
    }

    @Test
    void authenticatesLocationSearchWithoutChangingOtherProbeUrls() throws Exception {
        planner.setEnabled(true);
        for (String id : java.util.List.of("brouter", "location-search", "map-server")) management(id, Management.SELF_MANAGED);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (String path : java.util.List.of("/routing/status", "/maps/status", "/search/status", "/custom/status")) {
            server.createContext(path, exchange -> {
                var query = UriComponentsBuilder.fromUri(exchange.getRequestURI()).build().getQueryParams();
                boolean authorized = path.equals("/search/status") || path.equals("/custom/status")
                        ? "all".equals(query.getFirst("dataset"))
                          && "test-1".equals(query.getFirst("mtl-version"))
                          && "server-a".equals(query.getFirst("mtl-server-id"))
                          && "build-1".equals(query.getFirst("mtl-build"))
                        : query.isEmpty();
                byte[] body = "{\"versionInfo\":{\"image\":{\"version\":\"2.1\"}}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(authorized ? 200 : 403, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
        }
        server.start();
        var httpClient = new ReleaseFeedClient();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            planner.setStatusUrl(base + "/routing/status");
            maps.setStatusUrl(base + "/maps/status");
            search.setStatusUrl(base + "/search/status?dataset=all");
            for (boolean override : new boolean[]{false, true}) {
                if (override) properties.component("location-search").setStatusUrl(base + "/custom/status?dataset=all&mtl-version=stale&mtl-server-id=stale");
                var inventory = new ReleaseComponentInventory(properties, planner, search, maps, httpClient, identity, buildVersion);
                inventory.refresh(now, false);
                for (var installed : inventory.snapshot()) {
                    assertEquals("2.1", installed.buildId(), installed.id());
                    assertFalse(installed.checkFailed(), installed.id());
                }
            }
        } finally {
            httpClient.close();
            server.stop(0);
        }
    }

    @Test
    void probesOnlyExplicitSelfManagedEnabledServices() throws Exception {
        planner.setEnabled(true);
        management("brouter", Management.PROVIDER_MANAGED);
        management("locationsearch", Management.SELF_MANAGED);
        management("map-server", Management.DISABLED);
        when(client.fetchBuildId(searchStatusUrl())).thenReturn("2.1");
        var inventory = inventory();
        inventory.refresh(now, false);
        assertEquals("2.1", inventory.snapshot().get(1).buildId());
        verify(client).fetchBuildId(searchStatusUrl());
        verifyNoMoreInteractions(client);
    }

    @Test
    void failedProbeKeepsCachedBuildAndBacksOff() throws Exception {
        management("location-search", Management.SELF_MANAGED);
        when(client.fetchBuildId(anyString())).thenReturn("2.1").thenThrow(new IOException("network blocked"));
        var inventory = inventory();
        inventory.refresh(now, false);
        inventory.refresh(now.plusSeconds(60), false);
        var cached = inventory.snapshot().get(1);
        assertEquals("2.1", cached.buildId());
        assertTrue(cached.checkFailed());
        inventory.refresh(now.plusSeconds(120), false);
        verify(client, times(2)).fetchBuildId(anyString());
        inventory.refresh(now.plusSeconds(120), true);
        verify(client, times(3)).fetchBuildId(anyString());
    }

    @Test
    void firstFailureAndMissingManagementRemainUnknown() throws Exception {
        management("location-search", Management.SELF_MANAGED);
        when(client.fetchBuildId(anyString())).thenThrow(new IOException("offline"));
        var inventory = inventory();
        inventory.refresh(now, false);
        assertNull(inventory.snapshot().get(1).buildId());
        assertTrue(inventory.snapshot().get(1).checkFailed());
        assertEquals(Management.UNKNOWN, inventory.snapshot().get(2).management());
    }

    @Test
    void unavailableSelfManagedMapsDoNotInvalidateOtherProbes() throws Exception {
        planner.setEnabled(true);
        for (String id : java.util.List.of("brouter", "location-search", "map-server")) management(id, Management.SELF_MANAGED);
        when(client.fetchBuildId(planner.getStatusUrl())).thenReturn("1.24");
        when(client.fetchBuildId(searchStatusUrl())).thenReturn("1.14");
        when(client.fetchBuildId(maps.getStatusUrl())).thenThrow(new IOException("connection refused"));
        var inventory = inventory();
        inventory.refresh(now, false);
        var result = inventory.snapshot();
        assertEquals("1.24", result.get(0).buildId());
        assertEquals("1.14", result.get(1).buildId());
        assertFalse(result.get(0).checkFailed());
        assertFalse(result.get(1).checkFailed());
        assertTrue(result.get(2).checkFailed());
    }

    @Test
    void disabledChecksAndDisabledFeatureDoNotProbe() {
        management("brouter", Management.SELF_MANAGED);
        management("location-search", Management.SELF_MANAGED);
        search.setEnabled(false);
        var inventory = inventory();
        inventory.refresh(now, false);
        properties.setEnabled(false);
        inventory.refresh(now.plusSeconds(60), true);
        verifyNoInteractions(client);
    }

    @Test
    void composeEnvironmentAndYamlComponentNamesBindToSameConfiguration() {
        var env = new org.springframework.core.env.StandardEnvironment();
        env.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, java.util.Map.of(
                "MTL_UPDATES_COMPONENTS_BROUTER_MANAGEMENT", "SELF_MANAGED",
                "MTL_UPDATES_COMPONENTS_LOCATIONSEARCH_MANAGEMENT", "SELF_MANAGED",
                "MTL_UPDATES_COMPONENTS_MAPSERVER_MANAGEMENT", "PROVIDER_MANAGED")));
        env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("yaml", java.util.Map.of(
                "mtl.updates.components.location-search.status-url", "http://another-machine:8083/status")));
        org.springframework.boot.context.properties.source.ConfigurationPropertySources.attach(env);
        var bound = org.springframework.boot.context.properties.bind.Binder.get(env)
                .bind("mtl.updates", org.springframework.boot.context.properties.bind.Bindable.of(ReleaseUpdateProperties.class)).get();
        assertEquals(Management.SELF_MANAGED, bound.component("brouter").getManagement());
        assertEquals(Management.SELF_MANAGED, bound.component("location-search").getManagement());
        assertEquals("http://another-machine:8083/status", bound.component("location-search").getStatusUrl());
        assertEquals(Management.PROVIDER_MANAGED, bound.component("map-server").getManagement());
    }

}
