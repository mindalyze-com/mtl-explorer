# Packet: DEL_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_01
- In scope: Delete two imported source files from the watched import folder.
- Out of scope: Waiting for delete processing and UI verification.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_09.
- Required app/data state: Five public GPX files imported from watched folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: Remove exactly two imported GPX source files from the watched import folder.
- Not allowed: Remove unrelated files or database state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_01 | Deleted `Vitry-le-Francois_Langres.gpx` and `VoieVerteHauteVosges.gpx` from `/root/mtl-full-regression-2026-06-19_1952-beta-188-full-regression/data/gpx`. | Two imported source files are removed from the documented watched import folder. | The watched folder now contains only `JuraRoute72011.gpx`, `Lannion_Plestin_parcours24.4RE.gpx`, and `MoselradwegAusWiki.gpx`. | PASS | [assets/DEL_01-delete-files.txt](../assets/DEL_01-delete-files.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_01-delete-files.txt](../assets/DEL_01-delete-files.txt) | Before/after watched-folder listing. |

## Screenshot Evidence

No screenshot required for filesystem deletion.

## Timings

| Step | Timing |
|---|---:|
| Delete two source files | <1 min |

## Handoff Notes

- Completed: DEL_01.
- Remaining unfinished coverage: DEL_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: two GPX files deleted from watched folder; three GPX files remain.
