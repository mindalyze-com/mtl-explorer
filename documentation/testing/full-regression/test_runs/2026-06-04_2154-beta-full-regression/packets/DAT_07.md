# Packet: DAT_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_07
- In scope: Include a repeatable two-point segment with two or more tracks crossing the same two zones for measure/comparison/virtual-race checks.
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
| DAT_07 | Generated two fully synthetic anonymized GPX tracks with timestamped points crossing the same two coordinate zones near Bern. | At least two public or fully synthetic tracks cross the same two zones and can be used later for repeatable measure/comparison checks; no private GPX data is used. | synthetic-crossing-a.gpx and synthetic-crossing-b.gpx each contain 3 timestamped trkpt values and cross zone A near 46.948000,7.447000 and zone B near 46.958000,7.457000. | PASS | [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) |

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
