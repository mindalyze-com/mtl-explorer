# Packet: ERR_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ERR_02
- In scope: Rapid switching among primary tools and stale UI/listener cleanup.
- Out of scope: Deep functional retest of each tool, covered by prior packets.

## Prerequisites

- Required previous coverage IDs or run packets: ERR_01
- Required app/data state: Main map loaded with 14 tracks.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Click tool buttons and map zoom controls.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ERR_02 | Rapidly clicked Map, Stats, Filter, Planner, Segments, GPS, Animate, Admin, Map, Stats, Map, then checked active sheets, stale overlays, cursor, and map zoom. | Rapid switching between tools does not leave stale markers, listeners, or cursors. | Final state had one active Map sheet, zero stale track-pick/planner/segment/location/media overlays, cursor `auto`, no bad text, and map Zoom out changed scale from 300 km to 500 km. | PASS | `assets/ERR_02-rapid-tool-switching.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ERR_02-rapid-tool-switching.txt | Rapid switching and stale-state checks. |

## Timings

| Step | Timing |
|---|---:|
| Rapid switching and map usability check | <2 min |

## Handoff Notes

- Completed: ERR_02 passed.
- Remaining unfinished coverage: RUN_CLEANUP after finalization gate.
- Blocked or not applicable: None.
- State left for the next packet: Main browser signed in, Map sheet open, desktop viewport 1280 x 720.
