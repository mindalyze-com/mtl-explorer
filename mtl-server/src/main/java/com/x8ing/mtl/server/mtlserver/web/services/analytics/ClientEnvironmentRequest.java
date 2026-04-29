package com.x8ing.mtl.server.mtlserver.web.services.analytics;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ClientEnvironmentRequest {

    private String userAgent;
    private String timezone;
    private String browserLanguage;
    private List<String> browserLanguages;
    private Integer screenWidth;
    private Integer screenHeight;
    private Integer availableScreenWidth;
    private Integer availableScreenHeight;
    private Integer viewportWidth;
    private Integer viewportHeight;
    private Double devicePixelRatio;
    private Integer colorDepth;
    private String platform;
    private Integer hardwareConcurrency;
    private Double deviceMemoryGb;
    private Integer touchPoints;
    private String appDisplayMode;
    private Boolean online;

    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
