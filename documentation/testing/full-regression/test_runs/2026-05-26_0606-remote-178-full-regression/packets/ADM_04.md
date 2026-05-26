# Packet: ADM_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_04
- In scope: Manual GPS/media rescan controls and map usability after triggering them.
- Out of scope: Forcing a not-ready indexer service state.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_03
- Required app/data state: Admin Jobs panel open.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Trigger manual GPS and media rescans.
- Not allowed: Stop services or make indexers unavailable.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_04 | Clicked `Rescan GPS` and `Rescan Media`, observed messages/status, and used map zoom after rescans. | Rescan actions show queued/already-running/not-ready states without breaking map interaction. | GPS and MEDIA rescans showed clear queued messages; MEDIA briefly showed `SCANNING`. The ready indexers completed too quickly to produce `ALREADY_RUNNING`, and not-ready was not applicable. Map zoom still changed scale from 5 km to 10 km. | PASS | `assets/ADM_04-rescan-controls.webp`, `assets/ADM_04-rescan-results.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_04-rescan-controls.webp | Screenshot after manual rescan controls were used. |
| assets/ADM_04-rescan-results.txt | UI/API rescan messages and map-interaction check. |

## Timings

| Step | Timing |
|---|---:|
| Rescan controls and map zoom check | <2 min |

## Handoff Notes

- Completed: ADM_04 passed.
- Remaining unfinished coverage: ADM_05 through RUN_CLEANUP.
- Blocked or not applicable: Not-ready state did not apply because indexers were ready.
- State left for the next packet: Jobs panel remains open; freshness banner remains visible; map scale is 10 km.
