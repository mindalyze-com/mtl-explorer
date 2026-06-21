# Packet: ADM_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_05
- In scope: Background job visibility and settled state.
- Out of scope: Operational map/routing tasks.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_04
- Required app/data state: Jobs panel open.
- Required browser context: Desktop Chrome.

## Allowed Mutations

- Allowed: Scroll Jobs panel.
- Not allowed: Start new data mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_05 | Scrolled Jobs to Track Processing Jobs and checked API job summaries. | Duplicate Finder and Exploration Score progress is visible and settles after imports. | Duplicate Finder, Activity Classifier, and Exploration Score were visible and settled at 100% with 15 done/15 total. | PASS | [assets/ADM_05-background-jobs.webp](../assets/ADM_05-background-jobs.webp); [assets/ADM-admin-results.txt](../assets/ADM-admin-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_05-background-jobs.webp](../assets/ADM_05-background-jobs.webp) | Background job rows and settled progress. |
| [assets/ADM-admin-results.txt](../assets/ADM-admin-results.txt) | Job summary counters. |

## Screenshot Evidence

![Background jobs](../assets/ADM_05-background-jobs.webp)

## Timings

| Step | Timing |
|---|---:|
| Inspect background jobs | 2026-06-20T01:16 CEST |

## Handoff Notes

- Completed: ADM_05 passed.
- Remaining unfinished coverage: ADM_06.
- Blocked or not applicable: None.
- State left for the next packet: Operational tasks evidence captured.
