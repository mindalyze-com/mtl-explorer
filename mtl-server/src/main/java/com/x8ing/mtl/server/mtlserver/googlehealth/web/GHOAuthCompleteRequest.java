package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"redirectUrl"})
public record GHOAuthCompleteRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String redirectUrl) {
}
