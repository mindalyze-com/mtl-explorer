package com.x8ing.mtl.server.mtlserver.googlehealth.demo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@ConfigurationProperties("mtl.demo-health")
@Validated
@Data
public class DemoHealthProperties {
    private boolean enabled;
    @Min(0)
    @Max(100)
    private int trackPercentage = 100;
    private long seed = 20261003L;
    @Min(5)
    @Max(60)
    private int sampleIntervalSeconds = 15;
    @Min(1)
    @Max(100)
    private int batchSize = 25;
    @Min(7)
    @Max(365)
    private int recentDays = 90;
    @NotNull
    private Duration runSchedule = Duration.ofSeconds(1);
}
