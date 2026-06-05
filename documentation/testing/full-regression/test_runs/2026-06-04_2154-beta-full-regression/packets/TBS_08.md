# Packet: TBS_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_08
- In scope: Post-import and post-delete statistics freshness.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_07 terminal; prior IMP and DEL packets imported five GPX files and deleted two imported tracks.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only browser/API verification, screenshot/text evidence, packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_08 | Reset filters, captured overview totals after import/delete state, then searched the track browser for the two deleted GPX source filenames. | Stats reflect the five-GPX import and later two-track deletion; deleted tracks are not included in totals or browser results. | Overview still showed the current 11-track dataset with three remaining GPX sources plus FIT/format tracks; searches for VoieVerteHauteVosges.gpx and Lannion_Plestin_parcours24.4RE.gpx returned 0 of 11, and current source files excluded both deleted filenames. | PASS | [assets/TBS_08-deleted-tracks-absent.webp](../assets/TBS_08-deleted-tracks-absent.webp); [assets/TBS_08-post-delete-stats.txt](../assets/TBS_08-post-delete-stats.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_08-deleted-tracks-absent.webp](../assets/TBS_08-deleted-tracks-absent.webp) | Screenshot evidence |
| [assets/TBS_08-post-delete-stats.txt](../assets/TBS_08-post-delete-stats.txt) | Text/log evidence |

## Screenshot Evidence

![assets/TBS_08-deleted-tracks-absent.webp](../assets/TBS_08-deleted-tracks-absent.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser automation and evidence capture | ~1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
