package com.x8ing.mtl.server.mtlserver.event;

import org.springframework.context.ApplicationEvent;

/**
 * Published by the GPS file indexer once the initial filesystem scan has fully completed
 * (including reconciliation of removed files). Consumed e.g. by the Garmin exporter to know
 * when it is safe to start its processing (all existing files have been scheduled / processed at least once).
 */
public class InitialScanFinishedEvent extends ApplicationEvent {

    private final String index;            // logical index name (e.g. "GPS")
    private final long filesScanned;       // number of files processed (created/changed) during initial scan
    private final long scanDurationMs;     // wall-clock duration of initial scan

    public InitialScanFinishedEvent(Object source, String index, long filesScanned, long scanDurationMs) {
        super(source);
        this.index = index;
        this.filesScanned = filesScanned;
        this.scanDurationMs = scanDurationMs;
    }

    public String getIndex() {
        return index;
    }

    public long getFilesScanned() {
        return filesScanned;
    }

    public long getScanDurationMs() {
        return scanDurationMs;
    }

    @Override
    public String toString() {
        return "InitialScanFinishedEvent{" +
               "index='" + index + '\'' +
               ", filesScanned=" + filesScanned +
               ", scanDurationMs=" + scanDurationMs +
               '}';
    }
}
