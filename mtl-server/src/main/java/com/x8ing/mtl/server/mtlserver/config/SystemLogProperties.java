package com.x8ing.mtl.server.mtlserver.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "mtl.system-logs")
public class SystemLogProperties {
    public static final int DEFAULT_RETENTION_DAYS = 30;
    public static final int DEFAULT_CLEANUP_BATCH_SIZE = 1000;
    private boolean retentionEnabled = true;
    @NotNull
    private Duration retention = Duration.ofDays(DEFAULT_RETENTION_DAYS);
    @Min(1)
    private int cleanupBatchSize = DEFAULT_CLEANUP_BATCH_SIZE;

    public void setRetention(Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("System log retention must be positive");
        }
        this.retention = retention;
    }
}
