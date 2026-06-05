# Packet: DAT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DAT_03
- In scope: Record source metadata for every source file, including imported track mappings after indexing completed.
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
| DAT_03 | Recorded source URL/license note, destination filename, checksum, byte size, trackpoint/timestamp counts, source names, imported IDs, and imported display names for GPX, FIT, and synthetic non-GPX files. | Every source file record includes source URL, license/source note, destination filename, checksum, byte size, trkpt and timestamp counts, imported id(s), and imported track name(s). | All required source metadata is recorded; imported IDs and display names are mapped for the five public GPX files, Activity.fit, and seven synthetic non-GPX conversion fixtures. | PASS | [assets/DAT-public-data.txt](../assets/DAT-public-data.txt); [assets/DAT_03-imported-source-mapping.txt](../assets/DAT_03-imported-source-mapping.txt); [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt); [assets/FIT_02-ui-api-summary.txt](../assets/FIT_02-ui-api-summary.txt); [assets/FMT_02-format-verification-summary.txt](../assets/FMT_02-format-verification-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT-public-data.txt](../assets/DAT-public-data.txt) | Text/log evidence |
| [assets/DAT_03-imported-source-mapping.txt](../assets/DAT_03-imported-source-mapping.txt) | Text/log evidence |
| [assets/IMP_03-track-summary.txt](../assets/IMP_03-track-summary.txt) | Text/log evidence |
| [assets/FIT_02-ui-api-summary.txt](../assets/FIT_02-ui-api-summary.txt) | Text/log evidence |
| [assets/FMT_02-format-verification-summary.txt](../assets/FMT_02-format-verification-summary.txt) | Text/log evidence |

## Screenshot Evidence

No screenshot evidence for this packet.

## Timings

| Step | Timing |
|---|---:|
| Packet execution | <1 minute |

## Handoff Notes

- Completed: This coverage ID is now terminal after deferred import mappings were filled.\n- Remaining unfinished coverage: Continue with TRD_01.\n- Blocked or not applicable: none.\n- State left for the next packet: current imported dataset has 11 tracks.
