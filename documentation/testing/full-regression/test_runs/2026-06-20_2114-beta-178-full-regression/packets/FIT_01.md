# Packet: FIT_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_01
- In scope: Import the public GPS-bearing FIT activity file into the documented watched import folder.
- Out of scope: Index completion, map/browser/statistics verification, details views, and downloads; covered by FIT_02 through FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05, DEL_05.
- Required app/data state: public `Activity.fit` staged outside watched folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: copy `Activity.fit` into `data/gpx`.
- Not allowed: import unrelated FIT files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Copied staged public `Activity.fit` into the documented watched import folder. | FIT activity file with GPS positions is present in the import folder with source checksum preserved. | PASS: destination was missing before copy; after copy it is 94,096 bytes and SHA-256 `949a238e1bb75c3684479785f76fa9a16888bb394518844248f488171d591387`, matching the staged source. | PASS | [assets/FIT_01-import.txt](../assets/FIT_01-import.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-import.txt](../assets/FIT_01-import.txt) | Source and destination size/checksum evidence after FIT copy. |

## Screenshot Evidence

Not applicable; this is a watched-folder file mutation.

## Timings

| Step | Timing |
|---|---:|
| FIT copy and checksum verification | <1 minute |

## Handoff Notes

- Completed: FIT_01 is terminal.
- Remaining unfinished coverage: FIT_02 onward; DAT_03 still needs FIT imported ID/name.
- Blocked or not applicable: none.
- State left for the next packet: `Activity.fit` is in the watched import folder and should be converted/indexed.
