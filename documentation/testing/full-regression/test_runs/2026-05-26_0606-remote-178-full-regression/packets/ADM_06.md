# Packet: ADM_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_06
- In scope: Operational task status for vector map tiles, location search, and routing segments.
- Out of scope: Forcing sidecar outages or disabled/downloading states.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_05
- Required app/data state: Jobs panel open.
- Required browser context: Desktop browser plus authenticated API checks.

## Allowed Mutations

- Allowed: Read operational task statuses.
- Not allowed: Stop sidecars or alter map/planner configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_06 | Inspected Map & Routing rows and checked map, location-search, and planner status endpoints. | Vector map tiles, location search, and routing segment status show ready/downloading/unavailable/disabled states with useful detail. | Healthy-run ready states were shown with detail: hosted map service, GeoNames ready, and BRouter routing segments ready with 3 segments on disk. Disabled/downloading/unavailable states did not occur in this run. | PASS | `assets/ADM_06-operational-tasks.webp`, `assets/ADM_06-operational-tasks.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_06-operational-tasks.webp | Screenshot of Map & Routing operational task rows. |
| assets/ADM_06-operational-tasks.txt | UI and endpoint status details. |

## Timings

| Step | Timing |
|---|---:|
| Inspect UI and endpoints | <1 min |

## Handoff Notes

- Completed: ADM_06 passed.
- Remaining unfinished coverage: ADM_07 through RUN_CLEANUP.
- Blocked or not applicable: Sidecar outage/downloading/disabled states were not applicable to this healthy quick-install run.
- State left for the next packet: Jobs panel remains open; freshness banner remains visible.
