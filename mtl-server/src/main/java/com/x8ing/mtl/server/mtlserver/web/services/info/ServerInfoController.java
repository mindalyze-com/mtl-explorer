package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/info")
@JsonPropertyOrder({
        "buildProperties",
        "systemLogService",
        "mtlAppProperties",
        "dockerImageInfoService",
        "serverIdentityService"
})
public class ServerInfoController {

    private final Optional<BuildProperties> buildProperties;

    private final SystemLogService systemLogService;

    private final MtlAppProperties mtlAppProperties;

    private final DockerImageInfoService dockerImageInfoService;

    private final ServerIdentityService serverIdentityService;

    public ServerInfoController(Optional<BuildProperties> buildProperties,
                                SystemLogService systemLogService,
                                MtlAppProperties mtlAppProperties,
                                DockerImageInfoService dockerImageInfoService,
                                ServerIdentityService serverIdentityService) {
        this.buildProperties = buildProperties;
        this.systemLogService = systemLogService;
        this.mtlAppProperties = mtlAppProperties;
        this.dockerImageInfoService = dockerImageInfoService;
        this.serverIdentityService = serverIdentityService;
    }

    @RequestMapping("/build")
    public BuildInfoResponse getBuild() {
        String defaultLocale = mtlAppProperties.getDefaultLocale();
        String defaultFilterName = FilterConfigEntity.DEFAULT_GPS_TRACK_FILTER_NAME;
        String serverId = serverIdentityService.getServerId();
        ImageVersionInfoDto imageInfo = dockerImageInfoService.getImageInfo();
        if (buildProperties.isPresent()) {
            var props = buildProperties.get();
            String buildTime = props.getTime() != null ? props.getTime().toString() : null;
            return new BuildInfoResponse(props.getVersion(), buildTime, defaultLocale, defaultFilterName, serverId, imageInfo);
        } else {
            return new BuildInfoResponse("n/a", null, defaultLocale, defaultFilterName, serverId, imageInfo);
        }
    }

    @PostConstruct
    public void logServerInfo() {
        String msg = "mtl-server started. Build version info: ";
        BuildInfoResponse build = getBuild();
        String buildSummary = build.version() + " / " + build.buildTime()
                              + " / serverId=" + build.serverId()
                              + " / image=" + build.image().getVersion()
                              + " / imageBuild=" + build.image().getBuildTime();

        log.info(msg + " " + buildSummary);
        systemLogService.saveLog(SystemLog.TOPIC1.SERVER, "STARTUP", null, msg, buildSummary);
    }

}
