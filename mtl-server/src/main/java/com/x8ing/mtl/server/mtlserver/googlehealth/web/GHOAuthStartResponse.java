package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthIntent;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"authorizationUrl", "oauthMode", "intent"})
public record GHOAuthStartResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String authorizationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ConnectorProperties.OAuthMode oauthMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OAuthIntent intent) {
}
