# Packet: MCT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_05
- In scope: verify sub-track extraction between two points on a track returns the expected slice.
- Out of scope: visual comparison-map rendering, covered by MCT_04.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_01 through MCT_04.
- Required app/data state: Jura-derived tracks imported and indexed.
- Required browser context: signed-in desktop browser/API session.

## Allowed Mutations

- Allowed: read crossing and sub-track API endpoints; close temporary Segment Analyzer overlays.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Used the crossing endpoint to identify the `JuraRoute72011.nmea` A-B crossing, then requested `/api/tracks/details/get-sub-track?trackDataPointFrom=676448&trackDataPointTo=676536`. | Sub-track extraction between two points on one track returns the expected ordered slice. | Returned 89 ordered points on one `gpsTrackDataId` from index 8 to 96. First/last coordinates matched the requested segment, distance delta was 18,101.42 m, duration delta was 4,017 s, and missing moving-window speed values did not break extraction. | PASS | `assets/MCT_05-subtrack-response.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MCT_05-subtrack-response.txt | Compact endpoint summary for the returned sub-track slice. |

## Timings

| Step | Timing |
|---|---:|
| Sub-track API validation | <5m |

## Handoff Notes

- Completed: MCT_05 terminal PASS.
- Remaining unfinished coverage: continue with AVR_01.
- Blocked or not applicable: none.
- State left for the next packet: Segment Analyzer overlays closed; map returned to normal Jura-region view.
