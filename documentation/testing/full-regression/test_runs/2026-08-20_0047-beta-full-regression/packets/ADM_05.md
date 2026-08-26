# Packet: ADM_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_05
- In scope: Duplicate Finder and Exploration Score progress and settlement after import.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_02 import cycle and ADM_03 settled refresh.
- Required app/data state: Controlled temporary import followed by cleanup.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: Observe/refresh status only; use ADM_02's already-cleaned import mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_05 | Observed background jobs during the controlled import, then compared settled status after cleanup. | Duplicate Finder and Exploration Score progress is visible and settles. | Both jobs moved from running 93% (14 done/1 pending/15) to done 100% (14/14); controls stayed usable. | PASS | [assets/ADM_05-background-jobs.txt](../assets/ADM_05-background-jobs.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_05-background-jobs.txt](../assets/ADM_05-background-jobs.txt) | Running/pending and final job values. |

## Screenshot Evidence

Live desktop inspection confirmed both progress stages. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Progress settlement | Within the ADM_02 create/delete cycle |

## Handoff Notes

- Completed: Duplicate Finder and Exploration Score active and settled states.
- Remaining unfinished coverage: None for ADM_05.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Background jobs done/100% after cleanup.
