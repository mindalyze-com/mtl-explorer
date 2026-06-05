package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MapServerStatusControllerTest {

    @Test
    void mapConfigExposesDefaultRemoteRasterStylesWithAttributions() {
        MapServerStatusController controller = controller(new MapServerProperties());

        MapConfigDto config = controller.getMapConfig();

        assertThat(config.getRemoteRasterStyles()).containsKeys("light", "light-topo", "dark");
        assertThat(config.getRemoteRasterStyles().get("light").getUrl())
                .isEqualTo("https://tile.openstreetmap.org/{z}/{x}/{y}.png");
        assertThat(config.getRemoteRasterStyles().get("light-topo").getUrl())
                .isEqualTo("https://tile.opentopomap.org/{z}/{x}/{y}.png");
        assertThat(config.getRemoteRasterStyles().get("dark").getUrl())
                .isEqualTo("https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png");
        assertThat(config.getRemoteRasterStyles().get("dark").getAttribution()).contains("CARTO");
    }

    @Test
    void mapConfigExposesConfiguredRemoteRasterStylesWithoutLegacyTileUrl() {
        MapServerProperties properties = new MapServerProperties();
        properties.setRemoteRasterStyles(new LinkedHashMap<>(Map.of(
                "dark", remoteRasterStyle("https://tiles.example.test/dark/{z}/{x}/{y}.png", "Dark attribution")
        )));
        MapServerStatusController controller = controller(properties);

        MapConfigDto config = controller.getMapConfig();

        assertThat(config.getRemoteRasterStyles()).containsOnlyKeys("dark");
        assertThat(config.getRemoteRasterStyles().get("dark").getUrl())
                .isEqualTo("https://tiles.example.test/dark/{z}/{x}/{y}.png");
        assertThat(config.getRemoteRasterStyles().get("dark").getAttribution()).isEqualTo("Dark attribution");
    }

    private static MapServerStatusController controller(MapServerProperties properties) {
        properties.setTileMode(MapProxyConstants.TILE_MODE_REMOTE);
        return new MapServerStatusController(
                mock(MapServerStatusService.class),
                properties,
                new PlannerProperties(),
                mock(MapUpstreamResolver.class),
                mock(InitialMapViewportService.class));
    }

    private static MapServerProperties.RemoteRasterStyleProperties remoteRasterStyle(String url, String attribution) {
        MapServerProperties.RemoteRasterStyleProperties style = new MapServerProperties.RemoteRasterStyleProperties();
        style.setUrl(url);
        style.setAttribution(attribution);
        return style;
    }
}
