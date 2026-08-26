# Packet: IMP_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_08
- In scope: Verify statistics count increased by five and record any source-to-track splitting.
- Out of scope: Detailed totals and charts, covered by IMP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 and IMP_07.
- Required app/data state: Five indexed public GPX files.
- Required browser context: Signed-in Statistics > Tracks view.

## Allowed Mutations

- Allowed: Navigate to Statistics > Tracks.
- Not allowed: Change imported data or filters.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compare the empty pre-import statistics count with the settled post-import count and map each source to its imported ID. | Count increases by five unless a legitimate source split is documented. | Count increased from 0 to 5. Each of the five source files produced one imported track; no split occurred. | PASS | [assets/IMP_08-count.txt](../assets/IMP_08-count.txt); [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_08-count.txt](../assets/IMP_08-count.txt) | Baseline, post-import count, delta, mapping, and summary totals. |
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Empty pre-import baseline. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; visible statistics values are recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| Statistics count and mapping check | 1 min |

## Handoff Notes

- Completed: Verified a +5 statistics delta and one track per source file.
- Remaining unfinished coverage: None for IMP_08.
- Blocked or not applicable: None.
- State left for the next packet: Statistics > Tracks shows all five imports.
