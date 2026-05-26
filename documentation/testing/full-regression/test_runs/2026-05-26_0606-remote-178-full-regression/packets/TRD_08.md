# Packet: TRD_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_08
- In scope: Download original source file for GPX/FIT-backed tracks.
- Out of scope: Download as GPX, covered by TRD_09.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01, FIT_04.
- Required app/data state: GPX #100001 and FIT #100005 imported.
- Required browser context: authenticated installed-app download checks.

## Allowed Mutations

- Allowed: download source files.
- Not allowed: change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_08 | Downloaded original source for GPX track #100001 and reused FIT original download evidence for #100005. | Original source file downloads and matches uploaded one. | GPX original returned HTTP 200, 199,962 bytes, 1,414 trackpoints, and matching SHA-256; FIT original previously matched uploaded checksum. | PASS | `assets/TRD_08-download-original-verification.txt`, `assets/FIT_04_05-download-verification.txt`, `assets/TRD_08-gpx-original-download.gpx` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_08-download-original-verification.txt | GPX source-file download verification plus FIT evidence reference. |
| assets/TRD_08-gpx-original-download.gpx | Downloaded GPX original for #100001. |
| assets/FIT_04_05-download-verification.txt | FIT original checksum verification. |

## Timings

| Step | Timing |
|---|---:|
| Download verification | <1m |

## Handoff Notes

- Completed: TRD_08 terminal PASS.
- Remaining unfinished coverage: continue with TRD_09.
- Blocked or not applicable: browser download events unsupported; authenticated installed-app endpoints used for file verification.
- State left for the next packet: no data changed.
