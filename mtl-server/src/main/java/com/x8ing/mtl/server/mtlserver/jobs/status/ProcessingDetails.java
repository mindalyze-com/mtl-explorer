package com.x8ing.mtl.server.mtlserver.jobs.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.List;

public final class ProcessingDetails {
    private ProcessingDetails() { }
    public static final String RESOURCE_LOCK = "track-processing";
    public enum State { RUNNING, QUEUED, RETRYING, WAITING, COMPLETE, DISABLED }
    public enum Kind { INDEX, JOB, LOCK, USER }
    public enum Resolution { AUTOMATIC, USER_ACTION }
    public enum Relation { GATE, INPUT, RETRIGGER }
    public enum Scope { WHOLE_JOB, ITEMS }

    /** Ready includes eligible work currently being processed; execution is tracked separately. */
    @JsonPropertyOrder({"ready", "waiting", "retrying"})
    public record Work(long ready, long waiting, long retrying) { }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"kind", "id", "reason", "scope", "resolution", "heldBy"})
    public record Blocker(Kind kind, String id, String reason, Scope scope, Resolution resolution, String heldBy) {
        public static Blocker automatic(Kind kind, String id, String reason, Scope scope) {
            return new Blocker(kind, id, reason, scope, Resolution.AUTOMATIC, null);
        }
        public static Blocker user(String reason) {
            return new Blocker(Kind.USER, "track-review", reason, Scope.ITEMS, Resolution.USER_ACTION, null);
        }
    }

    @JsonPropertyOrder({"fromKind", "fromId", "toJob", "kind", "scope"})
    public record Dependency(Kind fromKind, String fromId, String toJob, Relation kind, Scope scope) { }

    public record BatchOutcome(boolean hasMore, List<Blocker> blockers, Instant retryAt) {
        public static BatchOutcome finished(boolean hasMore) { return new BatchOutcome(hasMore, List.of(), null); }
        public static BatchOutcome waiting(List<Blocker> blockers) { return new BatchOutcome(false, List.copyOf(blockers), null); }
    }

    public static State state(boolean enabled, boolean executing, Work work) {
        if (!enabled) return State.DISABLED;
        if (executing) return State.RUNNING;
        if (work.ready() > 0) return State.QUEUED;
        if (work.retrying() > 0) return State.RETRYING;
        if (work.waiting() > 0) return State.WAITING;
        return State.COMPLETE;
    }
}
