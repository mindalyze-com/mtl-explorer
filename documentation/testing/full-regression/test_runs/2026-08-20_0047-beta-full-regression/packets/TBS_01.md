# Packet: TBS_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_01
- In scope: Track-browser listing and field coverage for the active filtered result.
- Out of scope: Search semantics, covered by TBS_02.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_21.
- Required app/data state: Eight-track active year filter.
- Required browser context: Filter Review desktop table.

## Allowed Mutations

- Allowed: Clear prior browser search.
- Not allowed: Change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_01 | Inspect the filtered track-browser summary, rows, columns, and values. | All filtered tracks list with core metadata. | Eight rows matched the eight-track result and populated all required metadata columns. | PASS | [assets/TBS_01-browser-fields.txt](../assets/TBS_01-browser-fields.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_01-browser-fields.txt](../assets/TBS_01-browser-fields.txt) | Exact row count, columns, and example values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible table rows are linked above.

## Timings

| Step | Timing |
|---|---:|
| Clear search and inspect table | 2 min |

## Handoff Notes

- Completed: Filtered track listing and metadata fields.
- Remaining unfinished coverage: None for TBS_01.
- Blocked or not applicable: None.
- State left for the next packet: Review tracks with eight rows and empty search.
