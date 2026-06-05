# Packet: DAT_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_04
- In scope: Use the suggested verified GPX source from gps-touring/sample-gpx.
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
| DAT_04 | Downloaded all five suggested raw GPX URLs listed under DAT_04. | The suggested gps-touring sample GPX source is used for public GPX import evidence. | All five DAT_04 suggested gps-touring GPX raw URLs were downloaded and validated as real track files. | PASS | [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
