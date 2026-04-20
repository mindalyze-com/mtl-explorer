package com.x8ing.mtl.server.mtlserver.web.services.info;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.db.entity.config.FilterConfigEntity;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import com.x8ing.mtl.server.mtlserver.db.repository.logs.SystemLogService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/info")
public class ServerInfoController {

    private final Optional<BuildProperties> buildProperties;

    private final SystemLogService systemLogService;

    private final MtlAppProperties mtlAppProperties;

    public ServerInfoController(Optional<BuildProperties> buildProperties, SystemLogService systemLogService, MtlAppProperties mtlAppProperties) {
        this.buildProperties = buildProperties;
        this.systemLogService = systemLogService;
        this.mtlAppProperties = mtlAppProperties;
    }

    @RequestMapping("/build")
    public BuildInfoResponse getBuild() {
        String defaultLocale = mtlAppProperties.getDefaultLocale();
        String defaultFilterName = FilterConfigEntity.DEFAULT_GPS_TRACK_FILTER_NAME;
        if (buildProperties.isPresent()) {
            var props = buildProperties.get();
            String buildTime = props.getTime() != null ? props.getTime().toString() : null;
            return new BuildInfoResponse(props.getVersion(), buildTime, defaultLocale, defaultFilterName);
        } else {
            return new BuildInfoResponse("n/a", null, defaultLocale, defaultFilterName);
        }
    }

    @PostConstruct
    public void logServerInfo() {
        String msg = "mtl-server started. Build version info: ";
        BuildInfoResponse build = getBuild();
        String buildSummary = build.version() + " / " + build.buildTime();

        log.info(msg + " " + buildSummary);
        systemLogService.saveLog(SystemLog.TOPIC1.SERVER, "STARTUP", null, msg, buildSummary);
    }

}
