package com.x8ing.mtl.server.mtlserver.energy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;

/**
 * Current durable calculation state; retry messages do not expose exception details.
 */
@JsonPropertyOrder({"state", "message", "nextAttemptAt"})
public record EnergyCalculationStatus(State state, String message, Instant nextAttemptAt) {
    public enum State {PENDING, RETRYING, CALCULATED, UNAVAILABLE}
}
