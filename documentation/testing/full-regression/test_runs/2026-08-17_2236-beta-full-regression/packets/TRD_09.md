# Packet: TRD_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_09
- In scope: End-user Download as GPX from a non-GPX source and structural validation of the downloaded file.
- Out of scope: Original-source download.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_05 and TRD_08.
- Required app/data state: FIT-backed track 100005 with 3,601 GPS trackpoints.
- Required browser context: Track Details Download GPX control.

## Allowed Mutations

- Allowed: Trigger inbound download and independently parse the exposed GPX response.
- Not allowed: Treat server-only parsing as complete end-user artifact proof.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_09 | Exercised Download GPX on the FIT-backed track, waited for the browser artifact, and independently fetched and parsed the authenticated GPX endpoint. | The browser downloads valid GPX even though the source was FIT. | The endpoint returned valid GPX 1.1 with 3,601 `trkpt`, zero `wpt`, and 3,602 time elements, and the UI produced no visible error. The browser exposed no completed artifact, so the actual end-user file cannot be parsed in this environment. | BLOCKED | [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | UI attempt, browser limitation, response filename, and parsed GPX trackpoint/time counts. |

## Screenshot Evidence

Blocked by ACC_04; completed download artifacts are independently unavailable from the selected browser.

## Timings

| Step | Timing |
|---|---:|
| UI download-event wait | 10 s |
| GPX response parse | 1.4 s |

## Handoff Notes

- Completed: FIT-to-GPX control exercised and authenticated response structurally proven valid.
- Remaining unfinished coverage: None; terminally blocked for completed browser artifact access.
- Blocked or not applicable: A browser download-artifact channel is required to parse the file actually received by the end user.
- State left for the next packet: Track records remain unchanged.
