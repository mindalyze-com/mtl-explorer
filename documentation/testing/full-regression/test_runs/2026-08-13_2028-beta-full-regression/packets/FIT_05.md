# Packet: FIT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FIT_05.
- In scope: download the FIT-backed record as GPX and validate real trackpoints.
- Out of scope: original FIT integrity, covered by FIT_04.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_04.
- Required app/data state: FIT-backed record #100005 with Download GPX action.
- Required browser context: signed-in in-app browser and local download directory.

## Allowed Mutations

- Allowed: click Download GPX and inspect only the resulting exact download artifact.
- Not allowed: edit the downloaded XML or count waypoint-only content as a track.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_05 | Clicked Download GPX, identified the browser artifact, parsed it with `xmllint`, and counted trackpoint/waypoint/time elements. | A valid GPX downloads with real `trkpt` content, not only waypoints. | Valid GPX 1.1 XML, 479,844 bytes, with 3,601 `trkpt`, zero `wpt`, and 3,601 per-point timestamps plus metadata time. | PASS | [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Exact download artifact, XML validation, size/hash, and element counts. |

## Screenshot Evidence

The Download GPX action is visible in [FIT_02-detail.webp](../assets/FIT_02-detail.webp); exact output validation is recorded above.

## Timings

| Step | Timing |
|---|---:|
| Download and GPX validation | < 1 min |

## Handoff Notes

- Completed: FIT-to-GPX download is valid and contains 3,601 timestamped trackpoints.
- Remaining unfinished coverage: FIT_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: FIT Overview open; two exact test downloads in Downloads must be removed during cleanup.
