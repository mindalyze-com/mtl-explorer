package com.x8ing.mtl.server.mtlserver.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.management.OperatingSystemMXBean;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class StartupResourceLoggerTest {
    @Test
    void bootLogsRuntimeLimitsAndConfiguredPoolsOnceAtInfo(CapturedOutput output) {
        SpringApplication application = new SpringApplication(StartupConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setDefaultProperties(Map.of(
                "spring.config.name", "startup-resource-test",
                "mtl.indexer.worker-threads", "3",
                "mtl.exploration.worker-threads", "2",
                "spring.task.scheduling.pool.size", "4",
                "server.tomcat.threads.min-spare", "2",
                "server.tomcat.threads.max", "30",
                "spring.datasource.hikari.maximum-pool-size", "5",
                "spring.threads.virtual.enabled", "true",
                "mtl.test.secret", "synthetic-sensitive-value"));

        try (var context = application.run()) {
            assertThat(context.getBean(StartupResourceLogger.class)).isNotNull();
            assertThat(output).contains("INFO", "jvmAvailableProcessors=" + Runtime.getRuntime().availableProcessors(),
                    "jvmPlatformThreadsLive=", "heapInitialMiB=", "heapCommittedMiB=", "heapMaxMiB=",
                    "nonHeapUsedMiB=", "jvmOsTotalMemoryMiB=", "javaVersion=", "vmVersion=",
                    "indexerWorkersPerIndex=3", "explorationWorkers=2", "schedulerPoolSize=4",
                    "tomcatThreadsMax=30", "databaseConnectionsMax=5", "virtualThreadsEnabled=true");
            assertThat(output.getAll().lines().filter(line -> line.contains("Startup runtime resources:")).count())
                    .isEqualTo(1);
            assertThat(output).doesNotContain("synthetic-sensitive-value", "mtl.test.secret");
        }
    }

    @Test
    void reportsUndefinedMemoryAsUnavailableAndUsesMibUnits() {
        assertThat(StartupResourceLogger.memoryMiB(-1)).isEqualTo("unavailable");
        assertThat(StartupResourceLogger.memoryMiB(0)).isEqualTo("0.0");
        assertThat(StartupResourceLogger.memoryMiB(320L * 1024L * 1024L)).isEqualTo("320.0");
    }

    @Test
    void supportsJvmsWithoutExtendedOsMemoryMetrics() {
        var memory = StartupResourceLogger.readOsMemory(mock(OperatingSystemMXBean.class));
        assertThat(memory.totalBytes()).isEqualTo(-1);
        assertThat(memory.freeBytes()).isEqualTo(-1);
    }

    @Test
    void deniedOsMemoryMetricsDoNotPreventStartupAndRetainDiagnosticContext(CapturedOutput output) {
        var os = mock(com.sun.management.OperatingSystemMXBean.class);
        when(os.getTotalMemorySize()).thenThrow(new SecurityException("metric access denied"));
        var memory = StartupResourceLogger.readOsMemory(os);
        assertThat(memory.totalBytes()).isEqualTo(-1);
        assertThat(memory.freeBytes()).isEqualTo(-1);
        assertThat(output).contains("WARN", "Startup OS memory read failed", "SecurityException", "metric access denied");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(StartupResourceLogger.class)
    static class StartupConfiguration {
    }
}
