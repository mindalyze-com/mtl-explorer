# Packet: SYN_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_01
- In scope: Freshness banner after server-side data changes.
- Out of scope: Applying the reload.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_11
- Required app/data state: Server-side upload/rescan changes have occurred and client has not reloaded cached data yet.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Observe the banner.
- Not allowed: Click `Reload` before SYN_02.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_01 | Observed the app after ADM_02 upload and ADM_04 rescans. | After server-side data changes, a data-freshness banner appears. | Banner appeared with `New data available`, explanatory text, and `Reload`/`Dismiss` actions. | PASS | `assets/SYN_01-freshness-banner.webp`, `assets/SYN_01-freshness-banner.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SYN_01-freshness-banner.webp | Screenshot of freshness banner after data changes. |
| assets/SYN_01-freshness-banner.txt | Data-change source and banner text summary. |

## Timings

| Step | Timing |
|---|---:|
| Banner observation | <1 min |

## Handoff Notes

- Completed: SYN_01 passed.
- Remaining unfinished coverage: SYN_02 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Freshness banner remains visible and ready for `Reload`.
