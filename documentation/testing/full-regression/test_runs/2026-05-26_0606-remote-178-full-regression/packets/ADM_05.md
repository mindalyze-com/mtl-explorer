# Packet: ADM_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_05
- In scope: Background job progress for Duplicate Finder and Exploration Score.
- Out of scope: Manually creating long-running job load.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_04
- Required app/data state: Jobs panel available after upload/rescan activity.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Refresh the Jobs panel.
- Not allowed: Start additional data imports just for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_05 | Refreshed Jobs and checked Duplicate Finder and Exploration Score rows. | Background job progress is visible and settles after imports. | Duplicate Finder and Exploration Score were visible as `DONE 100%`; API reported pending=0 and done=total for all background jobs. | PASS | `assets/ADM_05-background-jobs.webp`, `assets/ADM_05-background-jobs.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_05-background-jobs.webp | Screenshot of settled background job rows. |
| assets/ADM_05-background-jobs.txt | Compact UI/API job status evidence. |

## Timings

| Step | Timing |
|---|---:|
| Refresh and inspect background jobs | <1 min |

## Handoff Notes

- Completed: ADM_05 passed.
- Remaining unfinished coverage: ADM_06 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Jobs panel remains open; freshness banner remains visible.
