# Packet: ADM_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: ADM_05.
- In scope: Duplicate Finder and Exploration Score progress after import.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_04.
- Required app/data state: synthetic ADM_02 import completed.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: refresh status.
- Not allowed: add another import for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_05 | Observed job state during post-import calculation, then refreshed after it settled. | Duplicate Finder and Exploration Score progress is visible and settles after imports. | Exploration Score showed RUNNING at 93% with 15 done and 1 active; later both it and Duplicate Finder showed DONE at 100% with updated totals. | PASS | [settled](../assets/ADM_05-settled.webp), [sequence](../assets/ADM_05-jobs.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_05-settled.webp](../assets/ADM_05-settled.webp) | Both requested jobs settled. |
| [assets/ADM_05-jobs.txt](../assets/ADM_05-jobs.txt) | Running-to-done values. |

## Screenshot Evidence

![Background jobs settled](../assets/ADM_05-settled.webp)

## Timings

| Step | Timing |
|---|---:|
| Status refresh | < 0.4 s |

## Handoff Notes

- Completed: ADM_05 is terminal `PASS`.
- Remaining unfinished coverage: ADM_06 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Admin Processing open; all jobs settled.

