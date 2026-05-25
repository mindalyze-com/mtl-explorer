package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mtl.location-search")
@JsonPropertyOrder({
        "enabled",
        "statusUrl",
        "queryUrl",
        "defaultLimit",
        "maxLimit",
        "timeoutMs"
})
public class LocationSearchProperties {

    private boolean enabled = true;

    private String statusUrl = "http://location-search:8083/status";

    private String queryUrl = "http://location-search:8083/search";

    private int defaultLimit = 20;

    private int maxLimit = 50;

    private int timeoutMs = 1500;
}
