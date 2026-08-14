# Packet: FIT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FIT_06.
- In scope: when GPSBabel or FIT conversion is unavailable, verify a clear UI error and record the block.
- Out of scope: force-disable a working required dependency in this healthy quick installation.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02 and FIT_05.
- Required app/data state: outcome of the real public FIT import and conversion.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: evaluate the conditional prerequisite from durable conversion evidence.
- Not allowed: break or remove GPSBabel solely to manufacture the unavailable condition.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FIT_06 | Evaluated whether the conditional unavailable-converter path applied after completing the real FIT flow. | If GPSBabel/FIT conversion is unavailable, a clear UI error is verified and FIT support is blocked. | The prerequisite is false: GPSBabel converted Activity.fit successfully, indexing completed, Track Details rendered, and GPX export passed. No unavailable-converter error path was present to test. | NOT APPLICABLE | [assets/FIT_02-processing.txt](../assets/FIT_02-processing.txt); [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_02-processing.txt](../assets/FIT_02-processing.txt) | Confirms installed GPSBabel conversion success. |
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Confirms working FIT-to-GPX conversion output. |

## Screenshot Evidence

Not applicable because the explicit unavailable-converter condition did not occur.

## Timings

| Step | Timing |
|---|---:|
| Conditional applicability review | < 1 min |

## Handoff Notes

- Completed: conditional converter-unavailable coverage closed as not applicable with positive working-converter evidence.
- Remaining unfinished coverage: FMT_01 onward.
- Blocked or not applicable: FIT_06 is terminal `NOT APPLICABLE`; converter is available.
- State left for the next packet: four-track synchronized state; FIT details remain open.
