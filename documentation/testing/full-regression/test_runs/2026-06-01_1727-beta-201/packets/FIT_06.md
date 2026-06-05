# Packet: FIT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_06
- In scope: Decide applicability of the conditional unavailable-converter behavior for the installed quick-start run.
- Out of scope: Intentionally modifying the product container to remove GPSBabel or force FIT conversion failure.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02-FIT_05.
- Required app/data state: `Activity.fit` has been imported and verified.
- Required browser context: Not required for this applicability decision.

## Allowed Mutations

- Allowed: Review existing FIT conversion/index/download evidence.
- Not allowed: Break or alter the installed app/container to simulate missing GPSBabel.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_06 | Checked whether the configured quick-install run entered the conditional state where GPSBabel or FIT conversion was unavailable. | If conversion is unavailable, UI/indexing should show a clear error and the FIT failure should be recorded as blocking. If conversion is available, the conditional row does not apply to this run. | GPSBabel was available. `Activity.fit` converted and indexed successfully as track `100005`; original FIT and converted GPX downloads also passed. There is no exposed black-box quick-install control to safely disable conversion without mutating the installed app outside scope. | NOT APPLICABLE | [assets/FIT_06-conversion-availability-scope.txt](../assets/FIT_06-conversion-availability-scope.txt), [assets/FIT_02-fit-index-logs.txt](../assets/FIT_02-fit-index-logs.txt), [assets/FIT_02-post-fit-status-api.txt](../assets/FIT_02-post-fit-status-api.txt), [assets/FIT_04-download-original-checksum.txt](../assets/FIT_04-download-original-checksum.txt), [assets/FIT_05-download-gpx-validation.txt](../assets/FIT_05-download-gpx-validation.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_06-conversion-availability-scope.txt](../assets/FIT_06-conversion-availability-scope.txt) | Applicability decision and linked successful conversion evidence. |
| [assets/FIT_02-fit-index-logs.txt](../assets/FIT_02-fit-index-logs.txt) | Successful FIT conversion/index log evidence. |
| [assets/FIT_02-post-fit-status-api.txt](../assets/FIT_02-post-fit-status-api.txt) | Successful FIT simplified-track/indexer API evidence. |
| [assets/FIT_04-download-original-checksum.txt](../assets/FIT_04-download-original-checksum.txt) | Original FIT download success evidence. |
| [assets/FIT_05-download-gpx-validation.txt](../assets/FIT_05-download-gpx-validation.txt) | Converted GPX download success evidence. |

## Timings

| Step | Timing |
|---|---:|
| FIT_06 applicability review | <1 minute |

## Handoff Notes

- Completed: FIT_06 terminal as `NOT APPLICABLE` for this configured run.
- Remaining unfinished coverage: Continue with `FMT_01`.
- Blocked or not applicable: FIT_06 unavailable-converter branch did not apply because conversion succeeded.
- State left for the next packet: Four tracks remain visible; no app state changed.
