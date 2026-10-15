package com.x8ing.mtl.server.mtlserver.googlehealth.persistence;

public record GHDataSourceSummary(
        long id,
        String applicationName,
        String deviceDisplayName,
        String platform,
        long recordCount
) {
}
