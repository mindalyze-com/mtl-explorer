# Packet: FIT_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_06
- In scope: Conditional FIT-conversion-unavailable behavior.
- Out of scope: Positive FIT import and download behavior; covered by FIT_02, FIT_04, and FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02, FIT_04, FIT_05.
- Required app/data state: `Activity.fit` imported and conversion available.
- Required browser context: not required for this conditional assessment.

## Allowed Mutations

- Allowed: none.
- Not allowed: disable conversion tools or mutate runtime configuration to force an artificial failure.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_06 | Evaluated whether the conditional unavailable-conversion case applies after positive FIT import, original download, and GPX conversion coverage. | If GPSBabel or FIT conversion is unavailable, the UI shows a clear conversion/indexing error and the failure is recorded as blocking for FIT support. | NOT APPLICABLE: FIT conversion was available in this run. `Activity.fit` imported and indexed successfully, original FIT downloaded with matching checksum, and GPX conversion downloaded with 3,601 `trkpt` elements. | NOT APPLICABLE | [assets/FIT_06-conversion-available.txt](../assets/FIT_06-conversion-available.txt); [packets/FIT_02.md](FIT_02.md); [packets/FIT_04.md](FIT_04.md); [packets/FIT_05.md](FIT_05.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_06-conversion-available.txt](../assets/FIT_06-conversion-available.txt) | Conditional applicability decision with supporting packet references. |

## Screenshot Evidence

No new screenshot required; this packet is a conditional applicability decision backed by FIT_02, FIT_04, and FIT_05.

## Timings

| Step | Timing |
|---|---:|
| Applicability decision | ~2 seconds |

## Handoff Notes

- Completed: FIT_06 is terminal as NOT APPLICABLE.
- Remaining unfinished coverage: FMT_01 onward.
- Blocked or not applicable: FIT_06 not applicable because conversion is available.
- State left for the next packet: FIT-backed track remains imported; no data mutations were made.
