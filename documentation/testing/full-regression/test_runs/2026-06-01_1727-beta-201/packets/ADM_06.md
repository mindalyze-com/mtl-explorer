# Packet: ADM_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_06
- In scope: Operational task rows for vector map tiles, location search, and routing segments.
- Out of scope: Forcing unavailable/downloading states by changing deployment configuration.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_05.
- Required app/data state: Jobs panel available.
- Required browser context: Desktop Chromium context.

## Allowed Mutations

- Allowed: Read operational status endpoints and UI.
- Not allowed: Change map/search/routing service configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_06 | Reviewed the Jobs panel `Map & Routing` section and supporting status APIs. | Vector map tiles, location search, and routing segment status show ready/downloading/unavailable/disabled states with useful detail. | Vector Map Tiles showed hosted map service / Protomaps archive `public-default`; Location Search showed GeoNames ready with image/component/data details and 1,332,531 rows; Routing Segments showed ready with BRouter 1.7.9 and 3 segments on disk. | PASS | [assets/ADM_03-jobs-status.webp](../assets/ADM_03-jobs-status.webp); [assets/ADM_03_05_06-jobs-operational-status.txt](../assets/ADM_03_05_06-jobs-operational-status.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_03-jobs-status.webp](../assets/ADM_03-jobs-status.webp) | Jobs panel operational task rows. |
| [assets/ADM_03_05_06-jobs-operational-status.txt](../assets/ADM_03_05_06-jobs-operational-status.txt) | Map, location search, and planner status API details. |

## Screenshot Evidence

**Jobs panel operational task rows.**

![Jobs panel operational task rows.](../assets/ADM_03-jobs-status.webp)

## Timings

| Step | Timing |
|---|---:|
| Operational task status review | ~20 s |

## Handoff Notes

- Completed: ADM_06 terminal as `PASS`.
- Remaining unfinished coverage: Continue with ADM_07.
- Blocked or not applicable: Unavailable/downloading states did not occur in this healthy fixed-target run.
- State left for the next packet: Server data unchanged.
