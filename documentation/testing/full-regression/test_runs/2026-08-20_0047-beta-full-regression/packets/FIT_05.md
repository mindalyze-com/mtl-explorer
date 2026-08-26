# Packet: FIT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_05
- In scope: Download FIT-backed track as GPX and verify valid real trackpoints.
- Out of scope: Conversion-unavailable error behavior, covered conditionally by FIT_06.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_04.
- Required app/data state: Track 100005 Overview has Download GPX control.
- Required browser context: Signed-in track detail.

## Allowed Mutations

- Allowed: Use visible Download GPX and validate the downloaded payload.
- Not allowed: Repair or synthesize the downloaded XML.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FIT_05 | Click Download GPX; validate XML/GPX root and count trkpt/wpt elements. | Valid GPX contains real trkpt data, not waypoint-only output. | 479,844-byte valid GPX 1.1 contains 3,601 trkpt and 0 wpt elements. | PASS | [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | UI action, XML validation, GPX metadata, checksum, and point counts. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the downloaded GPX is validated directly.

## Timings

| Step | Timing |
|---|---:|
| GPX download and structural validation | 1 min |

## Handoff Notes

- Completed: FIT conversion download is valid GPX with 3,601 trackpoints.
- Remaining unfinished coverage: None for FIT_05.
- Blocked or not applicable: In-app browser kept a temporary `.crdownload` name, but the complete payload was available and valid.
- State left for the next packet: FIT conversion is confirmed available and working.
