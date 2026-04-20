package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.planner.config.PlannerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/map")
public class MapServerStatusController {

    private final MapServerStatusService statusService;
    private final MapServerProperties properties;
    private final MtlAppProperties appProperties;
    private final PlannerProperties plannerProperties;

    public MapServerStatusController(MapServerStatusService statusService, MapServerProperties properties, MtlAppProperties appProperties, PlannerProperties plannerProperties) {
        this.statusService = statusService;
        this.properties = properties;
        this.appProperties = appProperties;
        this.plannerProperties = plannerProperties;
    }

    @GetMapping("/status")
    public MapServerStatusDto getMapServerStatus() {
        return statusService.getStatus();
    }

    @GetMapping("/config")
    public MapConfigDto getMapConfig() {
        String scope = appProperties.isDemoMode() ? "demo" : "prod";
        MapConfigDto dto = new MapConfigDto();
        dto.setTileMode(properties.getTileMode());
        dto.setTileBaseUrl(properties.getTileBaseUrl() + "/" + scope);
        dto.setTilesetName(properties.getTilesetName());
        dto.setLowzoomTilesetName(properties.getLowzoomTilesetName());
        dto.setRemoteTileUrl(properties.getRemoteTileUrl());
        dto.setInitialCenterLng(properties.getInitialCenterLng());
        dto.setInitialCenterLat(properties.getInitialCenterLat());
        dto.setInitialZoom(properties.getInitialZoom());

        // Demo area bounds — only populated when the property is set (demo mode)
        String bbox = properties.getDemoAreaBbox();
        if (bbox != null && !bbox.isBlank()) {
            List<Double> parsed = Arrays.stream(bbox.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());
            if (parsed.size() == 4) {
                dto.setDemoAreaBbox(parsed);
            }
        }
        dto.setDemoAreaMaxZoom(properties.getDemoAreaMaxZoom());

        // Planner feature flag + available profiles (client uses this to show/hide planner UI)
        dto.setPlannerEnabled(plannerProperties.isEnabled());
        dto.setPlannerProfiles(plannerProperties.isEnabled()
                ? plannerProperties.getProfiles()
                : Collections.emptyList());

        return dto;
    }
}
