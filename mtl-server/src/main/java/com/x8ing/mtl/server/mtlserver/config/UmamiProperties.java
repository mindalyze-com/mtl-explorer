package com.x8ing.mtl.server.mtlserver.config;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional analytics for public demo instances. Keep disabled for private installations;
 * they do not need a tracker URL, website ID or access to an Umami server.
 * Enabling demo mode does not enable analytics: each demo must opt in explicitly.
 */
@Data
@Component
@ConfigurationProperties(prefix = "mtl.analytics.umami")
@JsonPropertyOrder({"enabled", "scriptUrl", "websiteId"})
public class UmamiProperties {
    private boolean enabled = false;
    private String scriptUrl = "";
    private String websiteId = "";
}
