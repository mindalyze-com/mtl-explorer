package com.x8ing.mtl.server.mtlserver.jobs;

import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingContext;
import com.x8ing.mtl.server.mtlserver.jobs.status.ProcessingDetails;

import java.util.ArrayList;
import java.util.List;

/** Shared import barrier for its domain consumers; status collection has no dependency rules. */
public final class ImportPrerequisites {
    private ImportPrerequisites() { }
    public static List<ProcessingDetails.Blocker> check(ProcessingContext context, boolean includeMedia) {
        var blockers = new ArrayList<ProcessingDetails.Blocker>();
        if (context.indexBusy("GPS")) blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.INDEX,
                "GPS", "IMPORT_ACTIVE", ProcessingDetails.Scope.WHOLE_JOB));
        if (includeMedia && context.indexBusy("MEDIA")) blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.INDEX,
                "MEDIA", "IMPORT_ACTIVE", ProcessingDetails.Scope.WHOLE_JOB));
        if (includeMedia && blockers.isEmpty() && context.demoMode() && context.demoIndexingBusy()) {
            blockers.add(ProcessingDetails.Blocker.automatic(ProcessingDetails.Kind.INDEX, "GPS",
                    "IMPORT_STARTING", ProcessingDetails.Scope.WHOLE_JOB));
        }
        return List.copyOf(blockers);
    }
}
