# Packet: FLT_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_11
- In scope: Exact category selection remains active through parameter changes; newly available categories remain unchecked.
- Out of scope: Removing unavailable selected categories covered by FLT_13.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_10.
- Required app/data state: HIKING and WALKING exact selection; controlled track 100017 remains Hiking.
- Required browser context: Filter criteria and Included categories.

## Allowed Mutations

- Allowed: Temporarily set and clear From date 2026-08-18.
- Not allowed: Change the exact category selection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_11 | Set From=2026-08-18, inspected selected/unavailable types, cleared From, and inspected all types. | Exact selection remains; newly discovered/available categories stay unchecked. | HIKING stayed checked, temporarily unavailable WALKING stayed checked at 0, and after clear WALKING returned checked while newly available BICYCLE (12) remained unchecked. Result returned 1→3. | PASS | [assets/FLT_11-parameter-selection.txt](../assets/FLT_11-parameter-selection.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_11-parameter-selection.txt](../assets/FLT_11-parameter-selection.txt) | Before/restricted/cleared exact category states and result counts. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered checkbox states, availability labels, counts, and criteria values provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Restrict and inspect | About 2 s |
| Clear and inspect | About 2 s |

## Handoff Notes

- Completed: Exact selection persistence and newly available category behavior.
- Remaining unfinished coverage: None for FLT_11.
- Blocked or not applicable: None.
- State left for the next packet: Included categories panel open; HIKING+WALKING selected, BICYCLE unchecked, no criteria.
