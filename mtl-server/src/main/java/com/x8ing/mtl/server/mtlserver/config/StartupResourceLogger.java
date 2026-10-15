package com.x8ing.mtl.server.mtlserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.Locale;

/**
 * Records the resources visible to this JVM without inspecting private application data.
 */
@Slf4j
@Component
public class StartupResourceLogger implements ApplicationListener<ApplicationStartedEvent> {
    private static final long BYTES_PER_MIB = 1024L * 1024L;
    private static final long UNAVAILABLE_MEMORY = -1L;
    private static final String UNAVAILABLE = "unavailable";
    private static final String UNSPECIFIED = "unspecified";

    private final Environment environment;

    public StartupResourceLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var threads = ManagementFactory.getThreadMXBean();
        var os = ManagementFactory.getOperatingSystemMXBean();
        var memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        OsMemory osMemory = readOsMemory(os);

        log.info("Startup runtime resources: jvmAvailableProcessors={} jvmPlatformThreadsLive={} "
                 + "jvmPlatformThreadsDaemon={} jvmPlatformThreadsPeak={} os={} osArch={} osVersion={} "
                 + "javaVersion={} vmName={} vmVersion={} jvmUptimeMs={}",
                Runtime.getRuntime().availableProcessors(), threads.getThreadCount(), threads.getDaemonThreadCount(),
                threads.getPeakThreadCount(), os.getName(), os.getArch(), os.getVersion(), Runtime.version(),
                runtime.getVmName(), runtime.getVmVersion(), runtime.getUptime());
        log.info("Startup memory resources: heapInitialMiB={} heapUsedMiB={} heapCommittedMiB={} heapMaxMiB={} "
                 + "nonHeapUsedMiB={} nonHeapCommittedMiB={} jvmOsTotalMemoryMiB={} jvmOsFreeMemoryMiB={}",
                memoryMiB(heap.getInit()), memoryMiB(heap.getUsed()), memoryMiB(heap.getCommitted()),
                memoryMiB(heap.getMax()), memoryMiB(nonHeap.getUsed()), memoryMiB(nonHeap.getCommitted()),
                memoryMiB(osMemory.totalBytes()), memoryMiB(osMemory.freeBytes()));
        log.info("Startup configured pools: indexerWorkersPerIndex={} explorationWorkers={} "
                 + "schedulerPoolSize={} tomcatThreadsMinSpare={} tomcatThreadsMax={} "
                 + "databaseConnectionsMax={} virtualThreadsEnabled={}",
                configuredCount("mtl.indexer.worker-threads"), configuredCount("mtl.exploration.worker-threads"),
                configuredCount("spring.task.scheduling.pool.size"), configuredCount("server.tomcat.threads.min-spare"),
                configuredCount("server.tomcat.threads.max"), configuredCount("spring.datasource.hikari.maximum-pool-size"),
                environment.getProperty("spring.threads.virtual.enabled", Boolean.class, false));
    }

    private String configuredCount(String property) {
        Integer value = environment.getProperty(property, Integer.class);
        return value == null ? UNSPECIFIED : value.toString();
    }

    static String memoryMiB(long bytes) {
        return bytes < 0 ? UNAVAILABLE : String.format(Locale.ROOT, "%.1f", (double) bytes / BYTES_PER_MIB);
    }

    static OsMemory readOsMemory(OperatingSystemMXBean os) {
        if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
            try {
                return new OsMemory(extended.getTotalMemorySize(), extended.getFreeMemorySize());
            } catch (UnsupportedOperationException | SecurityException e) {
                log.warn("Startup OS memory read failed: type={} message={}", e.getClass().getSimpleName(), e.getMessage());
                log.debug("Startup OS memory read failure", e);
            }
        }
        return new OsMemory(UNAVAILABLE_MEMORY, UNAVAILABLE_MEMORY);
    }

    record OsMemory(long totalBytes, long freeBytes) {
    }
}
