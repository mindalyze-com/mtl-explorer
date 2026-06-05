# Packet: IMP_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_08
- In scope: Verify statistics/visible count increased by five after five-GPX import and record whether any source split into multiple tracks.
- Out of scope: Detailed totals direction; covered by IMP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07.
- Required app/data state: Five GPX imports indexed and visible.
- Required browser context: Clean desktop browser and authenticated status/API evidence.

## Allowed Mutations

- Allowed: Read UI/API evidence.
- Not allowed: Add/delete files or change metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_08 | Compared baseline count evidence with post-import track mapping and Stats → Tracks summary. | Count increases by five unless a source legitimately splits, in which case mapping is recorded. | Baseline map/stat count was `0`; post-import map and Stats → Tracks showed `5 Tracks` / `5 tracks`; imported mapping has five source files and five track IDs (`100000`-`100004`), so no source split occurred. | PASS | [assets/IMP_01-baseline-freshness-dom.txt](../assets/IMP_01-baseline-freshness-dom.txt), [assets/IMP_04-imported-track-mapping.txt](../assets/IMP_04-imported-track-mapping.txt), [assets/IMP_05-surfaces-after-reload.txt](../assets/IMP_05-surfaces-after-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_01-baseline-freshness-dom.txt](../assets/IMP_01-baseline-freshness-dom.txt) | Baseline freshness/token count with `tracks:0`. |
| [assets/IMP_04-imported-track-mapping.txt](../assets/IMP_04-imported-track-mapping.txt) | Five source files mapped to five imported track IDs. |
| [assets/IMP_05-surfaces-after-reload.txt](../assets/IMP_05-surfaces-after-reload.txt) | UI count evidence: map `5 Tracks`, stats `5 tracks`. |

## Timings

| Step | Timing |
|---|---:|
| Count comparison | <1 minute |

## Handoff Notes

- Completed: IMP_08 terminal as `PASS`.
- Remaining unfinished coverage: Continue with `IMP_09` totals/statistics direction checks.
- Blocked or not applicable: None.
- State left for the next packet: Five GPX tracks remain imported; no data mutation.
