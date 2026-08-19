# Packet: FMT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FMT_02
- In scope: Per non-GPX upload acceptance, GPSBabel conversion, map, details/charts, statistics, original download, and GPX download.
- Out of scope: GPX-format behavior already covered by IMP.

## Prerequisites

- Required previous coverage IDs or run packets: FMT_01, FIT_02-FIT_05.
- Required app/data state: Accepted FIT, TCX, KML, KMZ, IGC, NMEA, GeoJSON, and GDB tracks.
- Required browser context: Signed-in map, Statistics, and Track Details.

## Allowed Mutations

- Allowed: Add spatially distinct replacements for duplicate fixture collisions; open tabs; trigger inbound downloads; inspect read-only download endpoints.
- Not allowed: Treat endpoint bytes alone as complete end-user browser download evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FMT_02 | Validated all eight non-GPX formats through live conversion, map/stat inclusion, per-track detail pages, and interactive charts; exercised download controls; independently checked original and GPX endpoint bytes. | Every non-GPX format completes all listed child checks, including inspectable end-user downloaded artifacts. | Acceptance, conversion, map, details, charts, statistics, byte-exact originals, and real GPX responses passed for all eight. The browser exposed neither a native download event nor artifact path for FIT and repeat TCX actions, so downloaded-file inspection is blocked for the selected browser. | BLOCKED | [assets/FMT_02-per-format.txt](../assets/FMT_02-per-format.txt); [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt); [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FMT_02-per-format.txt](../assets/FMT_02-per-format.txt) | Per-format IDs, UI surfaces, graph, statistics, hashes, GPX structure, and blocker evidence. |
| [assets/FIT_04-original-download.txt](../assets/FIT_04-original-download.txt) | Earlier direct FIT original-download attempt and corroboration. |
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Earlier direct FIT GPX-download attempt and corroboration. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM, route, processing, and byte evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| First seven non-GPX conversions | About 13 s |
| Distinct TCX/GDB/NMEA replacements | About 11 s |
| Per-format detail/chart checks | About 6 min |
| Server download corroboration | Under 4 s |
| Repeat browser download-event waits | 10 s total |

## Handoff Notes

- Completed: All product-side child checks passed and every server download response was structurally correct.
- Remaining unfinished coverage: None; terminally blocked only for browser artifact inspection.
- Blocked or not applicable: Requires a browser surface that exposes completed download files.
- State left for the next packet: Sixteen indexed tracks total; thirteen default-visible tracks plus three duplicate-marked first-pass format tracks.
