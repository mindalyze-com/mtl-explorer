# Packet: TRD_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_10
- In scope: user-facing activity type change from track details.
- Out of scope: direct API mutation.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01.
- Required app/data state: GPX-backed track #100001 open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: change activity type through visible UI if available, then restore.
- Not allowed: direct database/API metadata edit.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_10 | Scanned track detail controls and clicked the activity badge/header area. | Change activity type saves successfully and energy values update automatically. | No visible activity type edit control was exposed in the tested detail view; clicking the `Bicycle` badge did not open an editor. | FAIL | `assets/TRD_10-activity-control-scan.webp`, `assets/TRD_10-activity-badge-click.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TRD-02 | P2 | Track details do not expose a visible activity type change control. | Open track #100001 details and look for/change the activity type from the header or Overview. | User can change activity type and save, triggering energy recalculation. | Only a static `Bicycle` badge was visible; no editor opened. | `assets/TRD_10-activity-badge-click.webp` | Users cannot correct activity type from the tested details UI. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_10-activity-control-scan.webp | Detail view while scanning for activity controls. |
| assets/TRD_10-activity-badge-click.webp | Detail view after clicking activity badge/header. |

## Timings

| Step | Timing |
|---|---:|
| Activity control scan | <2m |

## Handoff Notes

- Completed: TRD_10 terminal FAIL.
- Remaining unfinished coverage: continue with TRD_11.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
