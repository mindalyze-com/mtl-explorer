package com.x8ing.mtl.server.mtlserver.web.services.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonPropertyOrder({"clientEventId", "occurredAt", "lastOccurredAt", "occurrenceCount", "originalClientLoginRef", "severity", "topic1", "topic2", "topic3", "message", "detail", "stackTrace", "context", "buildId"})
public record ClientLogRequest(
        @Schema(nullable = true) UUID clientEventId,
        @Schema(nullable = true) Instant occurredAt,
        @Schema(nullable = true) Instant lastOccurredAt,
        @Schema(nullable = true) @Positive Integer occurrenceCount,
        @Schema(nullable = true) @Size(max = ClientDiagnosticLimits.LOGIN_REF_CHARS) String originalClientLoginRef,
        @Schema(nullable = true) Severity severity,
        @Schema(nullable = true) @Size(max = ClientDiagnosticLimits.TOPIC_CHARS) String topic1,
        @Schema(nullable = true) @Size(max = ClientDiagnosticLimits.TOPIC_CHARS) String topic2,
        @Schema(nullable = true) @Size(max = ClientDiagnosticLimits.TOPIC_CHARS) String topic3,
        @NotBlank @Size(max = ClientDiagnosticLimits.MESSAGE_CHARS) String message,
        @Schema(nullable = true) @Size(max = ClientDiagnosticLimits.DETAIL_CHARS) String detail,
        @Schema(type = "string", nullable = true) @Size(max = ClientDiagnosticLimits.STACK_CHARS) String stackTrace,
        @Schema(nullable = true) Map<String, Object> context,
        @NotBlank @Size(max = ClientDiagnosticLimits.BUILD_CHARS) String buildId) {
    public enum Severity {WARN, ERROR}
}
