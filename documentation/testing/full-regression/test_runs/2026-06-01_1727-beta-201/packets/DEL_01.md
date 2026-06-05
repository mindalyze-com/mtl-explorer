# Packet: DEL_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_01
- In scope: Delete two imported source files from the watched import folder.
- Out of scope: Waiting for delete processing and UI verification; covered by DEL_02+.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_09.
- Required app/data state: Five GPX files imported; watched folder contains the five source files.
- Required browser context: None.

## Allowed Mutations

- Allowed: Remove exactly two imported GPX source files from `/root/mtl-regression-2026-06-01_1727-beta-201/mtl-explorer/data/gpx/`.
- Not allowed: Delete remaining GPX files or FIT staged file.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Removed `Lannion_Plestin_parcours24.4RE.gpx` and `Vitry-le-Francois_Langres.gpx` from the watched import folder. | Two imported source files are deleted from the watched/import folder; remaining three files stay in place. | The two target files were removed; watched folder now contains `JuraRoute72011.gpx`, `MoselradwegAusWiki.gpx`, and `VoieVerteHauteVosges.gpx`. | PASS | [assets/DEL_01-delete-two-files.txt](../assets/DEL_01-delete-two-files.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-delete-two-files.txt](../assets/DEL_01-delete-two-files.txt) | Before/after watched-folder listing and delete command output. |

## Timings

| Step | Timing |
|---|---:|
| Delete two GPX files | <1 second |

## Handoff Notes

- Completed: DEL_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with `DEL_02` wait/trigger delete processing.
- Blocked or not applicable: None.
- State left for the next packet: Two source files removed from disk; three GPX files remain.
