# Packet: FIT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_05
- In scope: Verify Download as GPX for the FIT-backed track returns a valid GPX file containing real `trkpt` trackpoints.
- Out of scope: Original FIT source checksum validation.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_03 and FIT_04.
- Required app/data state: track `100005` imported from `Activity.fit`.
- Required browser context: desktop browser authenticated as the README quick-start user.

## Allowed Mutations

- Allowed: Download/read GPX export.
- Not allowed: Change track files or metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_05 | Verified the FIT detail page exposes the visible `Download GPX` control, then downloaded `/mtl/api/tracks/100005/gpx` with the same authenticated app user and parsed the response as XML. | A valid GPX file downloads and contains real `trkpt` trackpoints, not only waypoints. | Response was `200`, `application/gpx+xml`, filename `Activity.gpx`; XML parsed as GPX 1.1 with one `trk`, one `trkseg`, 3,601 `trkpt` elements, and zero `wpt` elements. | PASS | [assets/FIT_05-gpx-export.txt](../assets/FIT_05-gpx-export.txt); [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_05-gpx-export.txt](../assets/FIT_05-gpx-export.txt) | GPX export headers, XML parse result, and trackpoint counts. |
| [assets/FIT_03-overview.webp](../assets/FIT_03-overview.webp) | FIT detail Overview showing the visible Download GPX control. |

## Screenshot Evidence

![FIT detail overview with GPX download control](../assets/FIT_03-overview.webp)

## Timings

| Step | Timing |
|---|---:|
| GPX export download and parse | <1 min |

## Handoff Notes

- Completed: FIT_05.
- Remaining unfinished coverage: FIT_06 onward.
- Blocked or not applicable: none.
- State left for the next packet: Track `100005` remains imported; no data mutations made.
