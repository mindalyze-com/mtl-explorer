# Packet: TBS_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_08
- In scope: Statistics updates after the required five-GPX import and after deleting exactly two imported sources, with no stale deleted totals.
- Out of scope: Full cross-surface deletion verification retained for DEL_03/DEL_04 finalization.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02-IMP_08 and TBS_07.
- Required app/data state: Five source files identifiable with preserved copies; pre-delete resolved set has 15 tracks.
- Required browser context: Statistics Overview, Admin freshness notice, and Statistics Tracks.

## Allowed Mutations

- Allowed: Remove exactly two recorded GPX sources from the watched folder into a recoverable quarantine, wait for processing, reload data, and inspect results.
- Not allowed: Remove unrelated sources or restore deleted sources during this run.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_08 | Compared imported/pre-delete totals, moved exactly Vitry and Voie Verte out of the watched folder, accepted automatic processing, reloaded, compared Overview, and searched deleted/retained names. | Stats update after import and deletion; no stale deleted-track totals remain. | Map/Overview changed 15→13, 1,048→823 km, 1d 00h→16h 51m, 4,896→3,989 Wh, and 14,963→13,073 m. Deleted names returned 0/13; three retained GPX names each returned 1/13. | PASS | [assets/TBS_08-import-delete-stats.txt](../assets/TBS_08-import-delete-stats.txt); [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt); [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_08-import-delete-stats.txt](../assets/TBS_08-import-delete-stats.txt) | Exact targets/checksums, recoverable paths, before/after totals, processing, and absence/retention checks. |
| [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) | Original exact five-file watched import. |
| [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) | Original five source-to-track mappings. |

## Screenshot Evidence

Unavailable under ACC_04. Exact filesystem counts/checksums and accessible map, status, Overview, query, and row values provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Resolve and move exactly two sources | About 2 s |
| Automatic processing observation and client reload | About 8 s |
| Post-delete Overview and browser verification | About 8 s |

## Handoff Notes

- Completed: Five-GPX statistics reference, exact delete-two mutation, automatic processing, post-delete totals, and stale-name checks.
- Remaining unfinished coverage: None for TBS_08; DEL_01-DEL_04 packets still need their dedicated terminal updates/cross-surface checks.
- Blocked or not applicable: None.
- State left for the next packet: 13 resolved tracks; three original GPX sources remain watched; two are recoverably quarantined; Statistics Tracks All is open with clear search.
