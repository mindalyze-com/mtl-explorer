package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

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
                .isEqualTo(config.getRemoteRasterStyles().get("light").getUrl());
        assertThat(config.getRemoteRasterStyles().get("dark").getAttribution())
                .isEqualTo(config.getRemoteRasterStyles().get("light").getAttribution());
    }

    @Test
    void packagedConfigurationKeepsTheKeyFreeDarkRasterDefault() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addFirst(source);
        }
        MapServerProperties properties = Binder.get(environment)
                .bind("mtl.map-server", Bindable.of(MapServerProperties.class)).get();

        assertThat(controller(properties).getMapConfig().getRemoteRasterStyles().get("dark").getUrl())
                .isEqualTo("https://tile.openstreetmap.org/{z}/{x}/{y}.png");
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
