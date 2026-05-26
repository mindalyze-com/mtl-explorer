# Packet: FIT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_06
- In scope: FIT conversion/import/details/download flow.
- Out of scope: non-FIT format coverage.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05, DEL flow complete, app running.
- Required app/data state: Garmin `Activity.fit` staged before FIT_01.
- Required browser context: desktop signed-in browser; API token used only because in-app browser downloads are unsupported.

## Allowed Mutations

- Allowed: copy FIT to watched folder, use visible UI controls, download files from app endpoints.
- Not allowed: product source edits or workarounds that alter conversion behavior.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_06 | Checked converter availability through the FIT import result. | If GPSBabel/FIT conversion is unavailable, UI shows a clear conversion/indexing error and failure is blocking. | GPSBabel/FIT conversion was available and succeeded, so unavailable-converter error handling was not applicable in this run. | NOT APPLICABLE | `assets/FIT_02-index-monitor.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_02-index-monitor.txt | GPSBabel conversion success evidence. |

## Timings

| Step | Timing |
|---|---:|
| FIT step | <1m |

## Handoff Notes

- Completed: FIT_06 terminal NOT APPLICABLE.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none unless stated.
- State left for the next packet: dataset contains three remaining GPX tracks plus FIT track #100005.
