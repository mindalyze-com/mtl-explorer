# Packet: DEL_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_01
- In scope: Deletion of two imported source files from the documented watched import folder.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 through IMP_09 terminal; five GPX imports present before deletion.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Delete exactly two selected imported public GPX files from the target watched folder; packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Removed Lannion_Plestin_parcours24.4RE.gpx and VoieVerteHauteVosges.gpx from /root/mtl-regression-2026-06-04_2154-beta-full-regression/mtl-explorer/data/gpx/ on the target server. | The watched/import folder no longer contains the two selected source files while the remaining imported source files remain present. | Before deletion the folder contained all five imported public GPX files; after deletion it contained JuraRoute72011.gpx, MoselradwegAusWiki.gpx, and Vitry-le-Francois_Langres.gpx only. Checksums for the two deleted files were recorded before removal. | PASS | [assets/DEL_01-delete-source-files.txt](../assets/DEL_01-delete-source-files.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-delete-source-files.txt](../assets/DEL_01-delete-source-files.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Server-side delete command | <1 second |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
