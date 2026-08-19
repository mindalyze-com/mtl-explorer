# Packet: FIT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_03
- In scope: FIT Overview, Graphs, Quality, Events, Related, mini-map, and point popup parity.
- Out of scope: Download content (FIT_04-FIT_05).

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02.
- Required app/data state: FIT-backed track 100005 open.
- Required browser context: Track Details and canvas mini-map.

## Allowed Mutations

- Allowed: Switch tabs and click rendered map points.
- Not allowed: Claim canvas point behavior without direct targeting.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_03 | Opened and read Overview, Graphs, Quality, Related, and Events on track 100005; confirmed the mini-map region; audited point-popup targeting. | FIT-backed details render all surfaces and point popups like GPX-backed tracks. | All named tabs render coherent content, six charts are exposed, Quality shows 3,600 points, Related shows five GPX tracks, Events has a clear empty state, and mini-map exists. Point-popup parity cannot be directly exercised because the point markers are canvas-only and visual targeting is unavailable. | BLOCKED | [assets/FIT_03-tabs.txt](../assets/FIT_03-tabs.txt); [assets/ACC_04-screenshot-capability.txt](../assets/ACC_04-screenshot-capability.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_03-tabs.txt](../assets/FIT_03-tabs.txt) | Direct detail-tab content and blocked point-popup child. |

## Screenshot Evidence

Blocked by ACC_04; that missing visual channel prevents point-marker targeting.

## Timings

| Step | Timing |
|---|---:|
| Five detail tabs | 3.9 s |

## Handoff Notes

- Completed: All semantic details-tab and mini-map checks.
- Remaining unfinished coverage: None; terminally blocked for canvas point-popup parity.
- Blocked or not applicable: Requires visual screenshot/targeting or semantic point markers.
- State left for the next packet: FIT track remains open on Events tab.
