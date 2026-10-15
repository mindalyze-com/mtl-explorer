package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import java.time.Instant;
import java.util.List;

public record GHSynchronizationStatus(Instant snapshotAt, Instant cooldownUntil, List<GHStreamSyncStatus> streams) {
}
