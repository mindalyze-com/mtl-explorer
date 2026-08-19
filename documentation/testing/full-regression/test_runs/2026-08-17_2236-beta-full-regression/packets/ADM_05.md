# Packet: ADM_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_05
- In scope: Duplicate Finder and Exploration Score progress visibility and
  terminal settlement after imports.
- Out of scope: Operational service readiness, covered by ADM_06.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_04.
- Required app/data state: Recent ADM_02/ADM_03 imports and watched cleanup.
- Required browser context: Desktop Admin > Processing.

## Allowed Mutations

- Allowed: Explicit status refresh and watched-source cleanup from ADM_04.
- Not allowed: Direct database writes or job-state injection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_05 | Compared live post-import jobs to intermediate and terminal refreshed states, then reconciled terminal database status. | Duplicate Finder and Exploration Score progress is visible and settles after imports. | Duplicate Finder showed 95%/1 pending then 100%; Exploration showed 94%/1 pending, then both settled at 18/18 and 100% after controlled cleanup. All 18 active tracks are CALCULATED. | PASS | [assets/ADM_05-background-jobs.txt](../assets/ADM_05-background-jobs.txt); [assets/ADM_03-live-failed.jpg](../assets/ADM_03-live-failed.jpg); [assets/ADM_05-jobs-settled.jpg](../assets/ADM_05-jobs-settled.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_05-background-jobs.txt](../assets/ADM_05-background-jobs.txt) | Active/intermediate/terminal counts and database reconciliation. |
| [assets/ADM_03-live-failed.jpg](../assets/ADM_03-live-failed.jpg) | Shared same-run active/pending Processing state. |
| [assets/ADM_05-jobs-settled.jpg](../assets/ADM_05-jobs-settled.jpg) | Same Processing page with all background jobs terminal. |

## Screenshot Evidence

- Paired live and settled images show the percentage/pending transition without
  changing browser context.

## Timings

| Step | Timing |
|---|---:|
| Live status refresh | Under 700 ms |
| Cleanup watcher transition | About 8 s |
| Final job settlement after restart/cleanup | Under 1 min |

## Handoff Notes

- Completed: Duplicate Finder and Exploration Score progress/settlement passed.
- Remaining unfinished coverage: None for ADM_05.
- Blocked or not applicable: None.
- State left for the next packet: All four background jobs are done; Admin
  Processing remains open with operational task cards visible.
