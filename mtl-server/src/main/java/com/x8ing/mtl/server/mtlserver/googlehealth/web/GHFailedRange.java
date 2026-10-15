package com.x8ing.mtl.server.mtlserver.googlehealth.web;

import java.time.Instant;

public record GHFailedRange(String lane, String state, Instant start, Instant end, int attempts, Instant nextRetryAt, String error) {
}
