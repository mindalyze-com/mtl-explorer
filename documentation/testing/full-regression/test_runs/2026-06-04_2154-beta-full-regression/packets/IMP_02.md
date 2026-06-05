# Packet: IMP_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_02
- In scope: Import the five public GPX files through the documented watched import folder.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01 empty baseline captured.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Copy the five staged public GPX files into the README-documented `./data/gpx/` watched folder.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copied JuraRoute72011.gpx, MoselradwegAusWiki.gpx, Vitry-le-Francois_Langres.gpx, VoieVerteHauteVosges.gpx, and Lannion_Plestin_parcours24.4RE.gpx into the target watched import folder. | The five public GPX source files are present in the documented watched import folder with expected names/checksums for indexing. | All five files appeared under `/root/mtl-regression-2026-06-04_2154-beta-full-regression/mtl-explorer/data/gpx/`; remote SHA-256 values match staged metadata. | PASS | [assets/IMP_02-import-copy.txt](../assets/IMP_02-import-copy.txt); [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-import-copy.txt](../assets/IMP_02-import-copy.txt) | Text/log evidence |
| [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| File copy to watched folder | 3 seconds |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
