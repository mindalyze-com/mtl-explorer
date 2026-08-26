# Packet: FIT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_06
- In scope: If FIT conversion is unavailable, verify a clear conversion/indexing error and record FIT support as blocked.
- Out of scope: Simulating removal of a working packaged dependency.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_01-FIT_05.
- Required app/data state: Observe whether packaged GPSBabel/FIT conversion is available.
- Required browser context: FIT import/detail flow.

## Allowed Mutations

- Allowed: Classify the conditional branch from observed conversion state.
- Not allowed: Break or remove GPSBabel to force the fallback branch.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FIT_06 | Evaluate whether the unavailable-converter branch applies. | Run error-clarity check only if GPSBabel/FIT conversion is unavailable. | GPSBabel converted Activity.fit successfully; track 100005 indexed and both original/GPX downloads passed. The conditional branch does not apply. | NOT APPLICABLE | [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt); [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt) | Successful packaged conversion/indexing. |
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Successful converted output. |

## Screenshot Evidence

Not applicable to an unentered conditional branch.

## Timings

| Step | Timing |
|---|---:|
| Conditional classification | <1 min |

## Handoff Notes

- Completed: Confirmed FIT_06 does not apply because conversion is available.
- Remaining unfinished coverage: None for FIT_06.
- Blocked or not applicable: NOT APPLICABLE by the coverage condition.
- State left for the next packet: Six tracks present; proceed to supported-format coverage.
