# Packet: FIT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_05
- In scope: Download as GPX through UI and prove valid real trackpoints.
- Out of scope: Original FIT download.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_04.
- Required app/data state: FIT-backed track 100005 open.
- Required browser context: Track Details Overview with download-artifact capture.

## Allowed Mutations

- Allowed: Trigger inbound GPX download and inspect the artifact.
- Not allowed: Count waypoint-only output or server response alone as full end-user artifact evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_05 | Clicked `Download GPX`, waited for a native browser download, and independently fetched/parsed the exposed GPX endpoint. | Browser downloads a valid GPX containing real `trkpt` trackpoints. | The browser exposed no download artifact. The same authenticated endpoint returned `Activity.gpx`, valid GPX 1.1 with 3,601 `trkpt`, zero `wpt`, and 3,602 time elements. End-user artifact verification remains blocked by browser tooling. | BLOCKED | [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | UI attempt, browser constraint, and parsed server GPX corroboration. |

## Screenshot Evidence

Blocked by ACC_04; the separate download-artifact channel is unavailable.

## Timings

| Step | Timing |
|---|---:|
| UI download event wait | 10 s |
| API download and parse | 1.4 s |

## Handoff Notes

- Completed: UI action exercised; server output proven real GPX with 3,601 trackpoints.
- Remaining unfinished coverage: None; terminally blocked for end-user browser artifact inspection.
- Blocked or not applicable: Requires a browser surface exposing completed download artifacts.
- State left for the next packet: FIT-backed track remains present and converted successfully server-side.
