# Packet: TRD_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_09
- In scope: Download as GPX for GPX/FIT/non-GPX tracks.
- Out of scope: original source download.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_08, FIT_05, FMT_02.
- Required app/data state: GPX, FIT, and non-GPX tracks imported.
- Required browser context: authenticated installed-app download checks.

## Allowed Mutations

- Allowed: download GPX exports.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_09 | Downloaded GPX export for GPX #100001 and reused FIT/non-GPX export evidence. | A valid GPX downloads even if source was FIT or another format. | GPX #100001 export returned HTTP 200 and 1,414 `trkpt`; FIT export returned 3,601 `trkpt`; successful non-GPX exports returned 180 `trkpt`. GeoJSON remains a known FMT failure with 0 `trkpt`. | PASS | `assets/TRD_09-download-as-gpx-verification.txt`, `assets/FIT_04_05-download-verification.txt`, `assets/FMT_02-unique-download-verification.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_09-download-as-gpx-verification.txt | GPX-backed GPX export verification. |
| assets/TRD_09-gpx-download-as-gpx.gpx | Downloaded GPX export for #100001. |
| assets/FIT_04_05-download-verification.txt | FIT GPX export verification. |
| assets/FMT_02-unique-download-verification.txt | Non-GPX GPX export verification. |

## Timings

| Step | Timing |
|---|---:|
| GPX export checks | <1m |

## Handoff Notes

- Completed: TRD_09 terminal PASS for GPX/FIT/successful non-GPX sources.
- Remaining unfinished coverage: continue with TRD_10.
- Blocked or not applicable: GeoJSON export issue remains tracked under FMT, not counted as a pass.
- State left for the next packet: no data changed.
