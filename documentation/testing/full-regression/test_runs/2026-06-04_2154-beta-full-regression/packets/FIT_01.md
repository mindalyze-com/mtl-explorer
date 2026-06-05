# Packet: FIT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_01
- In scope: Import the GPS-bearing FIT activity file through the documented watched folder.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05 terminal; deletion packets terminal; watched folder available on target server.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Copy the staged public Activity.fit file into the watched import folder; packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Copied /tmp/mtl-regression-data-20260604-2154/Activity.fit to the target watched folder as data/gpx/Activity.fit and recorded local/remote checksum and listing evidence. | The FIT activity file is present in the watched folder with matching checksum and ready for the import/indexer pipeline. | Activity.fit was copied successfully; local and remote SHA-256 both equal 949a238e1bb75c3684479785f76fa9a16888bb394518844248f488171d591387, and the remote folder listing includes Activity.fit plus the three remaining GPX files. | PASS | [assets/FIT_01-import-copy.txt](../assets/FIT_01-import-copy.txt); [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-import-copy.txt](../assets/FIT_01-import-copy.txt) | Text/log evidence |
| [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| FIT copy to watched folder | 1 second |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
