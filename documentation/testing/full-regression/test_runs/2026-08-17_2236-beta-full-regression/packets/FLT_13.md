# Packet: FLT_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_13
- In scope: A selected category remains visible when parameters eliminate its matches and can be removed.
- Out of scope: View-switch selection behavior covered by FLT_14.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_12 and FLT_11.
- Required app/data state: Exact type view; controlled Hiking track dated 2026-08-18.
- Required browser context: Included categories and date Criteria.

## Allowed Mutations

- Allowed: Select only Hiking, temporarily set To=2025-12-31, remove Hiking, then restore the FLT_14 baseline.
- Not allowed: Leave the restrictive To date active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_13 | Selected only HIKING, set To before the Hiking activity, reopened categories, removed unavailable HIKING, then cleaned up. | Selected zero-match category remains visible as unavailable and can be removed. | HIKING stayed checked with `No matches with current parameters` / 0 while BICYCLE and WALKING were available unchecked. Unchecking HIKING removed the saved unavailable selection cleanly. | PASS | [assets/FLT_13-unavailable-category.txt](../assets/FLT_13-unavailable-category.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_13-unavailable-category.txt](../assets/FLT_13-unavailable-category.txt) | Setup, unavailable representation, removal, and cleanup states. |

## Screenshot Evidence

Unavailable under ACC_04. Exact checkbox state, unavailable text/count, criteria date, and result summaries provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Make Hiking unavailable | About 2 s |
| Inspect and remove | About 2 s |
| Cleanup baseline | About 3 s |

## Handoff Notes

- Completed: Unavailable selected-category persistence and removal.
- Remaining unfinished coverage: None for FLT_13.
- Blocked or not applicable: None.
- State left for the next packet: Filter open; exact activity view with HIKING+WALKING selected, no criteria, track 100017 still temporarily Hiking.
