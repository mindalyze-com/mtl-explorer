# Packet: ADM_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_04
- In scope: GPS/media manual rescan queued, already-running, and not-ready
  messages plus map continuity.
- Out of scope: Background-job settling, covered by ADM_05.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_03.
- Required app/data state: Healthy indexers and disposable ADM_03 sources.
- Required browser context: Desktop Admin > Maintenance with map visible.

## Allowed Mutations

- Allowed: Manual rescans, controlled concurrency, controlled app restart, and
  recoverable quarantine of only ADM_03 sources.
- Not allowed: Injecting server state or direct database writes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_04 | Ran browser GPS/media rescans, repeated/concurrent GPS requests, exercised map zoom, probed the startup boundary, and quarantined disposable sources. | Queued/already-running/not-ready states are visible without breaking map interaction. | Both browser queued messages and map continuity passed. Concurrent endpoint calls proved exact ALREADY_RUNNING responses, but the browser frame always landed on STARTED. Authenticated startup probes found indexers ready, so NOT_RUNNING was not safely reachable. | BLOCKED | [assets/ADM_04-rescan.txt](../assets/ADM_04-rescan.txt); [assets/ADM_04-manual-rescan.jpg](../assets/ADM_04-manual-rescan.jpg) |

## Issues

- No product defect filed. The uncovered states are timing/setup constraints,
  not contradictory product behavior.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_04-rescan.txt](../assets/ADM_04-rescan.txt) | Exact messages, concurrency/startup boundaries, map checks, and cleanup. |
| [assets/ADM_04-manual-rescan.jpg](../assets/ADM_04-manual-rescan.jpg) | Maintenance controls and visible media queued confirmation. |

## Screenshot Evidence

- The clipped Maintenance panel preserves the end-user manual rescan surface
  and queued confirmation while the map remains rendered beside it.

## Timings

| Step | Timing |
|---|---:|
| Browser rescan response | Under 500 ms |
| Concurrent guard probe | Under 2 s |
| Controlled restart to first login | About 29 s |
| Post-restart map interaction | Under 1 s |

## Handoff Notes

- Completed: GPS/media queued states, server already-running guard, map
  continuity, restart recovery, and ADM_03 fixture cleanup.
- Remaining unfinished coverage: None actionable for ADM_04 in this setup.
- Blocked or not applicable: Browser-visible already-running timing and the
  startup-only authenticated not-ready state.
- State left for the next packet: ADM_03 disposable sources are recoverably
  quarantined and their rows are REMOVED; ADM_02 track 100023 remains.
