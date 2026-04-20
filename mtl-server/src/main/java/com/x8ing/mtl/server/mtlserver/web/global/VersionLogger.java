package com.x8ing.mtl.server.mtlserver.web.global;

import com.x8ing.mtl.server.mtlserver.config.MtlAppProperties;
import com.x8ing.mtl.server.mtlserver.web.services.info.ServerInfoController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.SpringVersion;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VersionLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final String SEP = "==================================================================================";
    private final String DEMO_SEP = "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!";

    private final ServerInfoController serverInfoController;
    private final MtlAppProperties mtlAppProperties;

    public VersionLogger(ServerInfoController serverInfoController, MtlAppProperties mtlAppProperties) {
        this.serverInfoController = serverInfoController;
        this.mtlAppProperties = mtlAppProperties;
    }


    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        String springVersion = SpringVersion.getVersion();
        log.info("\n");
        log.info(SEP);
        log.info("SpringVersion Release. Spring Framework version: " + springVersion + ". SpringBoot version: " + SpringBootVersion.getVersion());
        serverInfoController.logServerInfo();
        if (mtlAppProperties.isDemoMode()) {
            log.warn("\n");
            log.warn(DEMO_SEP);
            log.warn("  *** DEMO MODE IS ACTIVE — using demo credentials, Porto taxi dataset ***");
            log.warn(DEMO_SEP + "\n");
        }
        log.info(SEP + "\n");
    }
}
