# Packet: GPS_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_04
- In scope: Clear permission-denied or disabled GPS state.
- Out of scope: Accepted live-location state.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01-03.
- Required app/data state: GPS disabled; normal map baseline.
- Required browser context: Authenticated remote plain-HTTP origin.

## Allowed Mutations

- Allowed: Activate GPS repeatedly and dismiss its alert.
- Not allowed: Change origin or permission configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_04 | Activated GPS twice from the normal map on the configured disabled origin and inspected the alert/map state. | Permission-denied or disabled GPS has a clear user-facing message and does not break the map. | Both attempts immediately showed `GPS unavailable` plus `GPS needs HTTPS or localhost. Open MTL Explorer from a secure address to use live location.` The alert is closable, no marker appears, and the 8-track map stays usable. | PASS | [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) | Exact repeated disabled-state alert and unchanged map result. |

## Screenshot Evidence

Live desktop inspection confirmed both alerts. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Each disabled response | Under 0.35 s |

## Handoff Notes

- Completed: Repeated clear disabled-state copy, closability, no marker, and map continuity.
- Remaining unfinished coverage: None for GPS_04.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: GPS disabled; alert currently visible; no marker/follow state.
