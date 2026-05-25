package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.planner.dto.BRouterStatusDto;
import com.x8ing.mtl.server.mtlserver.planner.service.PlannerStatusService;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchService;
import com.x8ing.mtl.server.mtlserver.web.services.map.LocationSearchStatusDto;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerStatusDto;
import com.x8ing.mtl.server.mtlserver.web.services.map.MapServerStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@JsonPropertyOrder({
        "mapServerStatusService",
        "locationSearchService",
        "plannerStatusService",
        "systemLogService",
        "objectMapper"
})
public class SidecarStatusLogService {

    private static final String SYSLOG_TOPIC2 = "SIDECAR_STATUS";
    private static final String LOG_MESSAGE = "MTL sidecar status and version snapshot";
    private static final String VERSION_LOG_MESSAGE = "Sidecar version info log";
    private static final String NOT_AVAILABLE = "N/A";
    private static final String MAP_STATUS_KEY = "map";
    private static final String VECTOR_MAPS_DISPLAY_NAME = "vectorMaps";

    private final MapServerStatusService mapServerStatusService;
    private final LocationSearchService locationSearchService;
    private final Optional<PlannerStatusService> plannerStatusService;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    public SidecarStatusLogService(MapServerStatusService mapServerStatusService,
                                   LocationSearchService locationSearchService,
                                   Optional<PlannerStatusService> plannerStatusService,
                                   SystemLogService systemLogService,
                                   ObjectMapper objectMapper) {
        this.mapServerStatusService = mapServerStatusService;
        this.locationSearchService = locationSearchService;
        this.plannerStatusService = plannerStatusService;
        this.systemLogService = systemLogService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(
            fixedDelayString = "${mtl.sidecar-status-log.interval:PT4H}",
            initialDelayString = "${mtl.sidecar-status-log.initial-delay:PT3S}"
    )
    public void logSidecarStatus() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("map", safeStatus(mapServerStatusService::getStatus));
        snapshot.put("locationSearch", safeStatus(locationSearchService::getStatus));
        plannerStatusService.ifPresent(statusService ->
                snapshot.put("brouter", safeStatus(statusService::fetchStatus)));

        try {
            String compactJson = objectMapper.writeValueAsString(snapshot);
            String detail = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
            log.info("{}: {}", LOG_MESSAGE, compactJson);
            logReadableVersionInfo(snapshot);
            systemLogService.saveLog(SystemLog.TOPIC1.SERVER, SYSLOG_TOPIC2, null, LOG_MESSAGE, detail);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize sidecar status snapshot: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Could not persist sidecar status snapshot: {}", e.getMessage());
        }
    }

    private static Object safeStatus(Supplier<?> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return Map.of(
                    "available", false,
                    "reason", e.getMessage() == null ? "unavailable" : e.getMessage()
            );
        }
    }

    private static void logReadableVersionInfo(Map<String, Object> snapshot) {
        snapshot.forEach((sidecarName, status) ->
                log.info("{}: {}", VERSION_LOG_MESSAGE, readableVersionInfo(sidecarName, status)));
    }

    private static String readableVersionInfo(String sidecarName, Object status) {
        ImageVersionInfoDto image = imageInfo(status);
        return displaySidecarName(sidecarName)
               + " imageVersion=" + display(image == null ? null : image.getVersion())
               + " imageBuildTime=" + display(image == null ? null : image.getBuildTime());
    }

    private static String displaySidecarName(String sidecarName) {
        return MAP_STATUS_KEY.equals(sidecarName) ? VECTOR_MAPS_DISPLAY_NAME : sidecarName;
    }

    private static ImageVersionInfoDto imageInfo(Object status) {
        VersionInfoDto versionInfo = versionInfo(status);
        return versionInfo == null ? null : versionInfo.getImage();
    }

    private static VersionInfoDto versionInfo(Object status) {
        if (status instanceof MapServerStatusDto mapStatus) {
            return mapStatus.getVersionInfo();
        }
        if (status instanceof LocationSearchStatusDto locationSearchStatus) {
            return locationSearchStatus.getVersionInfo();
        }
        if (status instanceof BRouterStatusDto brouterStatus) {
            return brouterStatus.getVersionInfo();
        }
        return null;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? NOT_AVAILABLE : value;
    }
}
