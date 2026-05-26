# Packet: SGN_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_09
- In scope: browser back/forward behavior between primary app views.
- Out of scope: deep-link detail navigation.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: app running and signed in.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: open Stats/Admin views and use browser back/forward.
- Not allowed: change data or settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_09 | Opened Stats, opened Admin, then used browser Back and Forward. | Back/forward navigation between views works without errors. | Stats/Admin did not update browser history; Back and Forward left the user on Admin. Console captured two 401 resource errors during the pass. | FAIL | `assets/SGN_09-back-forward.webp`, `assets/SGN_09-back-forward.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| SGN-01 | P3 | Browser Back/Forward does not navigate between primary views. | Sign in, click Stats, click Admin, then use browser Back and Forward. | Back returns to the previous app view and Forward returns to the next view. | URL remains `/mtl/` and both Back and Forward leave the UI on Admin. | `assets/SGN_09-back-forward.txt` | Users cannot rely on browser history for primary view navigation. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_09-back-forward.webp | Final state after back/forward check. |
| assets/SGN_09-back-forward.txt | View states, console errors, and failed request summary. |

## Timings

| Step | Timing |
|---|---:|
| Back/forward check | <1m |

## Handoff Notes

- Completed: SGN_09 terminal FAIL.
- Remaining unfinished coverage: continue with MAP_01.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
