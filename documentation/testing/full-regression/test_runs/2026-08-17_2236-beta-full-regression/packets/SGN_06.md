# Packet: SGN_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_06
- In scope: Startup splash logo, background, message, progress, and dismissal.
- Out of scope: Startup failure retry.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_05.
- Required app/data state: Signed in with populated data.
- Required browser context: Main map tab.

## Allowed Mutations

- Allowed: Reload the current app route.
- Not allowed: Restart the server.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_06 | Reloaded the signed-in app, sampled the early splash DOM/computed presentation, then waited for settled map data. | Logo, background, and message appear during startup and disappear after map/tracks load. | Full-viewport WebP backdrop, vignette, MTL Explorer logo, message, and progress were visible early; startup elements disappeared once map and 14 tracks settled. | PASS | [assets/SGN_06-splash.txt](../assets/SGN_06-splash.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_06-splash.txt](../assets/SGN_06-splash.txt) | Visible splash presentation and settled removal evidence. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM and computed visible-presentation evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Early splash sample | Under 0.4 s after reload |
| Settled splash removal | About 2.2 s |

## Handoff Notes

- Completed: Normal startup splash lifecycle passed.
- Remaining unfinished coverage: None for SGN_06.
- Blocked or not applicable: Screenshot capture blocked under ACC_04.
- State left for the next packet: Signed-in map settled with 14 tracks.
