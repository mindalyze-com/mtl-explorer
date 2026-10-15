package com.x8ing.mtl.server.mtlserver.db.entity.logs;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "system_log")
@Data
@JsonPropertyOrder({"id", "createDate", "occurredAt", "lastOccurredAt", "occurrenceCount", "clientEventId", "originalClientLoginRef", "source", "severity", "topic1", "topic2", "topic3",
        "message", "detail", "stackTrace", "stackFingerprint", "context", "buildId", "clientLoginRef"})
public class SystemLog {
    public enum Source {CLIENT, SERVER}

    public enum Severity {DEBUG, INFO, WARN, ERROR}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "create_date")
    private Date createDate = new Date();

    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Column(name = "last_occurred_at")
    private Instant lastOccurredAt;
    @Column(name = "occurrence_count")
    private Integer occurrenceCount;
    @Column(name = "client_event_id")
    private UUID clientEventId;
    @Column(name = "original_client_login_ref", length = 32)
    private String originalClientLoginRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 16)
    private Severity severity;

    @Column(name = "topic1", length = 255)
    private String topic1;
    @Column(name = "topic2", length = 255)
    private String topic2;
    @Column(name = "topic3", length = 255)
    private String topic3;

    @Column(name = "message", columnDefinition = "text")
    private String message;
    @Column(name = "detail", columnDefinition = "text")
    private String detail;
    @Column(name = "stack_trace", columnDefinition = "text")
    private String stackTrace;
    @Column(name = "stack_fingerprint", length = 64)
    private String stackFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "jsonb")
    private Map<String, Object> context;

    @Column(name = "build_id", length = 128)
    private String buildId;
    @Column(name = "client_login_ref", length = 32)
    private String clientLoginRef;
}
