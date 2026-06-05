# Packet: FIT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_06
- In scope: Fallback behavior when GPSBabel or FIT conversion is unavailable.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01 through FIT_05 terminal.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Applicability review and packet/run-state updates only; do not break the working converter to manufacture this condition.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_06 | Reviewed the current FIT conversion outcome and evidence from FIT_02 through FIT_05. | If GPSBabel or FIT conversion is unavailable, the UI shows a clear conversion/indexing error and that failure is recorded as blocking for FIT support. | Not applicable in this configured run because FIT conversion is available and successful: Activity.fit indexed as track 100005, displayed in UI, opened in details, downloaded as original FIT, and exported as valid GPX with 3,601 trkpt elements. | NOT APPLICABLE | [assets/FIT_06-conversion-available-note.txt](../assets/FIT_06-conversion-available-note.txt); [assets/FIT_02-index-wait.txt](../assets/FIT_02-index-wait.txt); [assets/FIT_03-detail-summary.txt](../assets/FIT_03-detail-summary.txt); [assets/FIT_05-gpx-download-validation.txt](../assets/FIT_05-gpx-download-validation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_06-conversion-available-note.txt](../assets/FIT_06-conversion-available-note.txt) | Text/log evidence |
| [assets/FIT_02-index-wait.txt](../assets/FIT_02-index-wait.txt) | Text/log evidence |
| [assets/FIT_03-detail-summary.txt](../assets/FIT_03-detail-summary.txt) | Text/log evidence |
| [assets/FIT_05-gpx-download-validation.txt](../assets/FIT_05-gpx-download-validation.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Applicability review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
