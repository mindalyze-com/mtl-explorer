package com.x8ing.mtl.server.mtlserver.web.services.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;
import java.util.UUID;

@JsonPropertyOrder({"event", "detail", "stackTrace", "context", "clientLoginRef", "originalClientLoginRef", "clientEventId"})
public record SystemLogDetail(SystemLogSummary event, @Schema(nullable = true) String detail,
                              @Schema(type = "string", nullable = true) String stackTrace,
                              @Schema(nullable = true) Map<String, Object> context,
                              @Schema(nullable = true) String clientLoginRef,
                              @Schema(nullable = true) String originalClientLoginRef, @Schema(nullable = true) UUID clientEventId) {
    public static SystemLogDetail from(SystemLog event) {
        return new SystemLogDetail(SystemLogSummary.from(event), event.getDetail(), event.getStackTrace(),
                event.getContext(), event.getClientLoginRef(), event.getOriginalClientLoginRef(), event.getClientEventId());
    }
}
