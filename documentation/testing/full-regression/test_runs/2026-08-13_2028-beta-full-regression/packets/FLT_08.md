# Packet: FLT_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_08.
- In scope: full filter reset from a restrictive view.
- Out of scope: resetting only one parameter.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_07.
- Required app/data state: 12-track baseline.
- Required browser context: Filter overview.

## Allowed Mutations

- Allowed: apply Jura keyword, then select Reset filter.
- Not allowed: leave a restrictive filter active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_08 | Applied Jura under Activities by keyword, then selected Reset filter. | Clearing filter restores all tracks. | The 1/12 result returned to 12, the view returned to Smart Base Filter, criteria cleared, and keyword coloring/legend disappeared. | PASS | [reset filter](../assets/FLT_08-reset-filter.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_08-reset-filter.txt](../assets/FLT_08-reset-filter.txt) | Restrictive and restored result/view/criteria states. |

## Screenshot Evidence

Exact count and configuration transitions are recorded as text.

## Timings

| Step | Timing |
|---|---:|
| Restrictive apply | < 1 s |
| Reset filter | < 1 s |

## Handoff Notes

- Completed: FLT_08.
- Remaining unfinished coverage: FLT_09 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Smart Base Filter, no criteria, all 12 tracks, no map coloring.

