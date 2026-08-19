# Packet: SYN_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_03
- In scope: Required five-GPX import and exact delete-two flow across all named surfaces.
- Out of scope: Repeating mutations that already have durable packets/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02-IMP_08, TBS_08, HMO_03, SYN_02.
- Required app/data state: Two original sources quarantined; three retained.
- Required browser context: Authenticated desktop Statistics, Filter, details, and Map.

## Allowed Mutations

- Allowed: Read-only reconciliation and direct current absence/retention audit.
- Not allowed: Re-import or restore the two deleted sources.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_03 | Reconciled the durable import/delete packets, queried current source rows, searched deleted/retained names, opened retained details, and inspected Filter/heatmap state. | Indexer, freshness, map, browser, stats, filters, heatmap, and details reflect the source-of-truth files. | Exact +5 import and -2 deletion propagated through every named surface. Both removed files remain REMOVED with no track; retained sources, details, Filter, and heatmap remain coherent after later additive fixtures. | PASS | [assets/SYN_03-five-import-delete-audit.txt](../assets/SYN_03-five-import-delete-audit.txt); [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt); [assets/TBS_08-import-delete-stats.txt](../assets/TBS_08-import-delete-stats.txt); [assets/HMO_03-filter-update.txt](../assets/HMO_03-filter-update.txt); [assets/SYN_03-current-heatmap.jpg](../assets/SYN_03-current-heatmap.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_03-five-import-delete-audit.txt](../assets/SYN_03-five-import-delete-audit.txt) | Full cross-packet reconciliation plus direct current DB/UI audit. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Original +5 freshness reload across map/browser/stats/filter. |
| [assets/TBS_08-import-delete-stats.txt](../assets/TBS_08-import-delete-stats.txt) | Exact two sources, quarantine, processing, reload, totals, and searches. |
| [assets/HMO_03-filter-update.txt](../assets/HMO_03-filter-update.txt) | Post-delete heatmap/filter synchronization. |
| [assets/SYN_03-current-heatmap.jpg](../assets/SYN_03-current-heatmap.jpg) | Current all-track map data layers with heatmap enabled at 52%. |

## Screenshot Evidence

- The current Map data panel shows the live 15-track post-addition result with
  all four data layers and Heatmap enabled; HMO_03 preserves paired 13-track
  post-delete density captures.

## Timings

| Step | Timing |
|---|---:|
| Durable-flow reconciliation | About 3 min |
| Current DB/UI audit | About 2 min |

## Handoff Notes

- Completed: Required +5/-2 source-of-truth flow passes every named surface.
- Remaining unfinished coverage: None for SYN_03.
- Blocked or not applicable: None.
- State left for the next packet: Map data detail is open; heatmap is enabled
  at 52%; current in-sync total is 15 tracks.
