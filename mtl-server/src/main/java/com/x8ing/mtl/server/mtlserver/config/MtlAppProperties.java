package com.x8ing.mtl.server.mtlserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mtl")
public class MtlAppProperties {

    /**
     * True when the application is running in demo mode (application-demo.yml).
     */
    private boolean demoMode = false;

    /**
     * Target number of visible tracks in demo mode.  After suspicious tracks are excluded
     * the service trims excess good tracks so the final count is exactly this value.
     * 0 = no trimming (keep all good tracks).  Only used when demoMode is true.
     */
    private int demoTargetTrackCount = 0;

    /**
     * When false the /api/admin/server-log endpoint returns an empty string. Default true; set false in demo mode.
     */
    private boolean logViewerEnabled = true;

    /**
     * Optional BCP 47 locale tag (e.g. "de-CH") to suggest as the default formatting locale
     * for the client when the user has not yet chosen one manually.
     * When not set the client falls back to its own browser-based detection.
     */
    private String defaultLocale;
}
