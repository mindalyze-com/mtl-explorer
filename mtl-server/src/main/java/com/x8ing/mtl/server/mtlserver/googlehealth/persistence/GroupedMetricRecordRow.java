package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

import java.time.LocalDate;

/**
 * Date-based records retain their stored civil date; timestamped records use the requested timezone.
 */
public record GroupedMetricRecordRow(LocalDate date, MetricRecordRow record) {
}
