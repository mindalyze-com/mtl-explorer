package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import com.x8ing.mtl.server.mtlserver.googlehealth.provider.GoogleHealthProvider;

public record GHCycleStatistics(long fetched, long changed, long stamped, long deleted, int completeFastStreams,
                                int totalFastStreams, boolean budgetLimited, GoogleHealthProvider.Counters requests) {
}
