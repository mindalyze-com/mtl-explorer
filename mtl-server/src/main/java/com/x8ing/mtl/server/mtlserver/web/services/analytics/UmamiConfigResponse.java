package com.x8ing.mtl.server.mtlserver.web.services.analytics;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"enabled", "scriptUrl", "websiteId"})
public record UmamiConfigResponse(boolean enabled, String scriptUrl, String websiteId) {
    public static final UmamiConfigResponse DISABLED = new UmamiConfigResponse(false, "", "");
}
