package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.config.ConnectorProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonPropertyOrder({
        "configurationState",
        "configurationIssues",
        "oauthMode",
        "configuredRedirectUri",
        "manualRedirectUri",
        "callbackPath",
        "requestedScopes"
})
public record GHSetupResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ConnectorProperties.ConfigurationState configurationState,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ConnectorProperties.ConfigurationIssue> configurationIssues,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ConnectorProperties.OAuthMode oauthMode,
        String configuredRedirectUri,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String manualRedirectUri,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String callbackPath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> requestedScopes
) {
}
