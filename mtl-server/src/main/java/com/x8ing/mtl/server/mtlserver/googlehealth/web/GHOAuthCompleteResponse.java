package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.googlehealth.oauth.OAuthCompletionResult;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"result", "message"})
public record GHOAuthCompleteResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OAuthCompletionResult result,
        String message) {
}
