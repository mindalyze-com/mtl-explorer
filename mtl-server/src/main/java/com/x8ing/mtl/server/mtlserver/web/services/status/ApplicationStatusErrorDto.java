package com.x8ing.mtl.server.mtlserver.web.services.status;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "section",
        "code",
        "message"
})
public record ApplicationStatusErrorDto(
        ApplicationStatusSection section,
        String code,
        String message
) {
}
