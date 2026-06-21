# Packet: TRD_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_09
- In scope: Verify Download as GPX returns a valid GPX file for non-GPX/FIT sources.
- Out of scope: Original source download checksums, covered by TRD_08.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_05 and FMT_02.
- Required app/data state: FIT and non-GPX format tracks indexed.
- Required browser context: authenticated app/API session.

## Allowed Mutations

- Allowed: Reuse completed GPX export evidence from this run.
- Not allowed: Change data or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_09 | Reused FIT_05 and FMT_02 GPX export actions. | Download as GPX returns valid GPX with real trackpoints for FIT/non-GPX sources. | FIT export returned GPX 1.1 with one track, one segment, 3,601 `trkpt`, zero `wpt`; FMT_02 parsed GPX exports for seven non-GPX formats with real `trkpt` data. | PASS | [assets/FIT_05-gpx-export.txt](../assets/FIT_05-gpx-export.txt); [assets/FMT_02-detail-download-summary.txt](../assets/FMT_02-detail-download-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-export.txt](../assets/FIT_05-gpx-export.txt) | FIT GPX export response and XML parse results. |
| [assets/FMT_02-detail-download-summary.txt](../assets/FMT_02-detail-download-summary.txt) | GPX export parse results for seven non-GPX formats. |

## Screenshot Evidence

No screenshot required; XML parse evidence is recorded in text assets.

## Timings

| Step | Timing |
|---|---:|
| GPX export verification | Covered in FIT_05 and FMT_02 |

## Handoff Notes

- Completed: TRD_09.
- Remaining unfinished coverage: TRD_10 onward.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
