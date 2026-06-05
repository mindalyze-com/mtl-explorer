# Packet: DAT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_06
- In scope: Ensure waypoint-only/non-GPS data is not counted as positive import evidence.
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
| DAT_06 | Checked positive evidence files used for DAT_01/DAT_05: each public GPX has trkpt sequences and the FIT converts to GPS-bearing GPX. | No waypoint-only GPX or non-GPS FIT file is counted as positive evidence. | All positive GPX evidence contains trkpt sequences; Activity.fit converted to 3,601 trkpt points; no waypoint-only or non-GPS file is counted as positive evidence. | PASS | [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) |

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
