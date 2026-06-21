# Packet: DEL_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_01
- In scope: Delete two imported source files from the watched import folder.
- Out of scope: Processing/removal verification; covered by DEL_02 onward.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 through IMP_09.
- Required app/data state: five GPX files imported from the watched folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: remove two selected imported GPX files from `data/gpx`.
- Not allowed: remove unrelated files or directories.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Deleted `VoieVerteHauteVosges.gpx` and `Lannion_Plestin_parcours24.4RE.gpx` from the documented watched import folder. | Two imported source files are removed from the watched folder for deletion-sync testing. | PASS: both selected files existed before deletion and were absent afterward; the other three GPX files remain in the watched folder. | PASS | [assets/DEL_01-delete-files.txt](../assets/DEL_01-delete-files.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-delete-files.txt](../assets/DEL_01-delete-files.txt) | Before/after file listing and deletion proof. |

## Screenshot Evidence

Not applicable; this is a watched-folder file mutation.

## Timings

| Step | Timing |
|---|---:|
| Delete two GPX source files | <1 minute |

## Handoff Notes

- Completed: DEL_01 is terminal.
- Remaining unfinished coverage: DEL_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: two GPX files are missing from the watched folder; server should process removal automatically or via rescan if needed.
