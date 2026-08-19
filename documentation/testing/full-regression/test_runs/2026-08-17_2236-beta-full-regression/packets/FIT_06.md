# Packet: FIT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_06
- In scope: Error behavior only if GPSBabel/FIT conversion is unavailable.
- Out of scope: Successful conversion already covered by FIT_01-FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01-FIT_05.
- Required app/data state: Installed conversion capability known.
- Required browser context: None when condition does not apply.

## Allowed Mutations

- Allowed: Assess conditional applicability from direct conversion results.
- Not allowed: Disable a working required converter merely to force the condition.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_06 | Verified the installed converter version and successful FIT import/GPX output before evaluating the conditional failure path. | If GPSBabel or FIT conversion is unavailable, a clear blocking UI error is shown. | The condition is false: GPSBabel 1.10.0 is installed, FIT import completed, and server conversion produced 3,601 trackpoints. The unavailable-converter error path does not apply to this run. | NOT APPLICABLE | [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt); [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt); [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) | Converter availability and version. |
| [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt) | Successful installed-app FIT import. |
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Successful server GPX conversion. |

## Screenshot Evidence

Not applicable because the conditional failure state did not occur.

## Timings

| Step | Timing |
|---|---:|
| Applicability audit | <1 s |

## Handoff Notes

- Completed: Conditional row evaluated from direct converter/import evidence.
- Remaining unfinished coverage: None for FIT_06.
- Blocked or not applicable: NOT APPLICABLE because conversion is available and successful.
- State left for the next packet: Six tracks loaded, including FIT track 100005.
