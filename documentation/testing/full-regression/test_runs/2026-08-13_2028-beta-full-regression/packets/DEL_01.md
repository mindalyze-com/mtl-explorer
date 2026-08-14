# Packet: DEL_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DEL_01.
- In scope: delete two imported source files from the watched folder.
- Out of scope: wait for processing and verify UI disappearance.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_09.
- Required app/data state: five verified GPX sources in the disposable watched folder.
- Required browser context: not required for this file-system action.

## Allowed Mutations

- Allowed: delete exactly two identified public GPX files from the disposable watched folder.
- Not allowed: delete staged sources, other watched files, the installation directory, volumes, or unrelated server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Resolved and checksummed Vitry and VoieVerte in the watched folder, then deleted those two exact paths. | Two imported source files are removed, leaving three watched files for delete synchronization. | `Vitry-le-Francois_Langres.gpx` and `VoieVerteHauteVosges.gpx` were removed in 3 ms; folder count changed 5→3; the other three sources remain. | PASS | [assets/DEL_01-delete.txt](../assets/DEL_01-delete.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-delete.txt](../assets/DEL_01-delete.txt) | Exact paths, checksums, timestamps, and remaining watched-folder contents. |

## Screenshot Evidence

Not needed; this packet covers the watched-folder mutation. UI results follow in DEL_02-DEL_05.

## Timings

| Step | Timing |
|---|---:|
| Two-file deletion | 3 ms |

## Handoff Notes

- Completed: exactly two verified source files removed from the watched folder.
- Remaining unfinished coverage: DEL_02 onward; DAT_03 still needs the FIT imported mapping.
- Blocked or not applicable: none.
- State left for the next packet: three GPX files remain watched; deleted records should be processed automatically.
