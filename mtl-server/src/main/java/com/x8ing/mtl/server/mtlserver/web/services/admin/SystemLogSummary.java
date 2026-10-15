package com.x8ing.mtl.server.mtlserver.web.services.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.entity.logs.SystemLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Date;

@JsonPropertyOrder({"id", "createDate", "occurredAt", "lastOccurredAt", "occurrenceCount", "source", "severity", "topic1", "topic2", "topic3", "message", "stackFingerprint", "buildId"})
public record SystemLogSummary(Long id, Date createDate, @Schema(nullable = true) Instant occurredAt,
                               @Schema(nullable = true) Instant lastOccurredAt, @Schema(nullable = true) Integer occurrenceCount,
                               @Schema(nullable = true) SystemLog.Source source,
                               @Schema(nullable = true) SystemLog.Severity severity,
                               @Schema(nullable = true) String topic1, @Schema(nullable = true) String topic2,
                               @Schema(nullable = true) String topic3, String message,
                               @Schema(nullable = true) String stackFingerprint, @Schema(nullable = true) String buildId) {
    public static SystemLogSummary from(SystemLog event) {
        return new SystemLogSummary(event.getId(), event.getCreateDate(), event.getOccurredAt(), event.getLastOccurredAt(),
                event.getOccurrenceCount(), event.getSource(), event.getSeverity(),
                event.getTopic1(), event.getTopic2(), event.getTopic3(), event.getMessage(), event.getStackFingerprint(), event.getBuildId());
    }
}
