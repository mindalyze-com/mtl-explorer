# Packet: TRD_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_09
- In scope: Verify Download as GPX returns valid GPX even for a FIT-backed source.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows terminal or explicitly not required.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Read-only verification and packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_09 | Downloaded GPX export from FIT track 100005 detail page and validated it with xmllint and GPX element counts. | A valid GPX file downloads even when the source was FIT or another format. | FIT-backed Activity.fit exported as Activity.gpx; XML was valid with one track, one segment, and 3,601 trackpoints. | PASS | [assets/TRD_09-fit-gpx-export-control.webp](../assets/TRD_09-fit-gpx-export-control.webp); [assets/TRD_08_09-download-summary.txt](../assets/TRD_08_09-download-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_09-fit-gpx-export-control.webp](../assets/TRD_09-fit-gpx-export-control.webp) | Screenshot evidence |
| [assets/TRD_08_09-download-summary.txt](../assets/TRD_08_09-download-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/TRD_09-fit-gpx-export-control.webp](../assets/TRD_09-fit-gpx-export-control.webp)

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
