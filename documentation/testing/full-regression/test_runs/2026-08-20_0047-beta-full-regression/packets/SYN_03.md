# Packet: SYN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_03
- In scope: Aggregate five-GPX import and delete-two source-of-truth flow across all required surfaces.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02-09 and DEL_01-05.
- Required app/data state: Same-run public fixtures and their recorded mutations.
- Required browser context: Same-run desktop browser evidence.

## Allowed Mutations

- Allowed: No new mutation; consolidate durable same-run packets only.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_03 | Consolidated the five-file create/index/reload and exact two-file delete flow across indexer, freshness, map, browser, stats, filters, heatmap, and details. | Every surface reflects source-of-truth files. | Every accessible/non-canvas surface passed. Rendered heatmap/polyline absence remains unprovable because screenshot capture is blocked under ACC_04/DEL_03/DEL_05. | BLOCKED | [assets/SYN_03-import-delete-matrix.txt](../assets/SYN_03-import-delete-matrix.txt); [packets/IMP_09.md](IMP_09.md); [packets/DEL_05.md](DEL_05.md) |

## Issues

No new product issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_03-import-delete-matrix.txt](../assets/SYN_03-import-delete-matrix.txt) | Cross-surface import/delete result matrix. |
| [assets/IMP_04-settled.txt](../assets/IMP_04-settled.txt) | Five-file index settlement. |
| [assets/DEL_02-watcher-processing.txt](../assets/DEL_02-watcher-processing.txt) | Two-file delete settlement. |
| [assets/DEL_03-cross-view-removal.txt](../assets/DEL_03-cross-view-removal.txt) | Cross-view removal and canvas limitation. |

## Screenshot Evidence

Required rendered heatmap/polyline proof is blocked by ACC_04; compact textual/control evidence exists for all other surfaces.

## Timings

| Step | Timing |
|---|---:|
| Consolidation | <1 min; underlying timings are in source packets |

## Handoff Notes

- Completed: All non-canvas components of the five-import/delete-two flow.
- Remaining unfinished coverage: None; rendered heatmap/polyline proof is terminally BLOCKED.
- Blocked or not applicable: Canvas absence evidence under ACC_04.
- State left for the next packet: Current synchronized eight-track state unchanged.
