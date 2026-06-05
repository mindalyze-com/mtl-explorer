# Packet: MAP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_02
- In scope: All tracks appear on the map and total/visible count is correct.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Current dataset has 11 accepted tracks after deletion, FIT import, and seven format imports.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only evidence review and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_02 | Compared current map UI, API total, and format-verification summary. | All current tracks appear on the map, with correct visible/total count. | Current map UI shows 11 Tracks. API/format summary also shows TOTAL_TRACKS=11 and map cache logs renderedTracks=11. | PASS | [assets/FMT_02-format-verification-summary.txt](../assets/FMT_02-format-verification-summary.txt); [assets/FMT_02-map-formats.webp](../assets/FMT_02-map-formats.webp); [assets/FMT_02-map-formats.txt](../assets/FMT_02-map-formats.txt); [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_02-format-verification-summary.txt](../assets/FMT_02-format-verification-summary.txt) | Text/log evidence |
| [assets/FMT_02-map-formats.webp](../assets/FMT_02-map-formats.webp) | Screenshot evidence |
| [assets/FMT_02-map-formats.txt](../assets/FMT_02-map-formats.txt) | Text/log evidence |
| [assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp) | Screenshot evidence |

## Screenshot Evidence

![assets/FMT_02-map-formats.webp](../assets/FMT_02-map-formats.webp)
![assets/SGN_02-valid-login-map.webp](../assets/SGN_02-valid-login-map.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
