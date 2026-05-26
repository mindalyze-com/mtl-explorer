# Packet: SYN_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_02
- In scope: Apply data freshness banner reload and verify cached tracks/stats refresh.
- Out of scope: Full revalidation of every data surface.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_01
- Required app/data state: Freshness banner visible after ADM_02 upload/rescans.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Click the freshness banner `Reload`.
- Not allowed: Create additional imports for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_02 | Closed Admin, clicked the freshness banner `Reload`, then checked map and Stats Overview. | Reloading from the banner refreshes cached tracks and stats. | Banner cleared; map changed from `10 / 10 Tracks` to `11 / 11 Tracks`; Stats Overview showed 11 tracks and included `ADM_02 Upload Validation` as latest/recent activity. | PASS | `assets/SYN_02-reload-results.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SYN_02-reload-results.txt | Before/after map and stats evidence for banner reload. |

## Timings

| Step | Timing |
|---|---:|
| Banner reload and map/stats verification | <2 min |

## Handoff Notes

- Completed: SYN_02 passed.
- Remaining unfinished coverage: SYN_03 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Client cache is refreshed to 11 visible tracks; Stats panel is open; freshness banner is cleared.
