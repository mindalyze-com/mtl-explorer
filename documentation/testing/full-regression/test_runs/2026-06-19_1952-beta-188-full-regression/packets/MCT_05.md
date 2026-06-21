# Packet: MCT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_05
- In scope: Sub-track / segment extraction between two track data points on measured A-B segment tracks.
- Out of scope: Compare chart rendering covered by MCT_04 and geometry-regression visualization covered by MCT_06.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04
- Required app/data state: Current A-B Segment Analyzer response has crossing pairs for tracks `100021` and `100023`.
- Required browser context: Authenticated desktop Playwright context.

## Allowed Mutations

- Allowed: Read live sub-track API responses.
- Not allowed: Modify imported tracks or persisted application data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Used A-B crossing data-point IDs from the current Segment Analyzer response and called `/api/tracks/details/get-sub-track` for tracks `100021` and `100023`. | The API returns the expected inclusive segment slice between the selected points. | Both live requests returned HTTP `200`. Track `100021` returned points `637009..637012` with 4 points; track `100023` returned points `637040..637041` with 2 points. First/last IDs matched, coordinates stayed inside the synthetic Zurich corridor, distance/time were monotonic, and no zero-like coordinates appeared. | PASS | [assets/MCT_05-subtrack-slices.txt](../assets/MCT_05-subtrack-slices.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_05-subtrack-slices.txt](../assets/MCT_05-subtrack-slices.txt) | Live sub-track endpoint requests, returned slice bounds, endpoint IDs, and assertions. |

## Screenshot Evidence

No screenshot required for this API-backed segment extraction check.

## Timings

| Step | Timing |
|---|---:|
| Two live sub-track requests and validation | <1 s |

## Handoff Notes

- Completed: Sub-track extraction returned the expected A-B slices for both selected synthetic tracks.
- Remaining unfinished coverage: MCT_06 onward.
- Blocked or not applicable: None.
- State left for the next packet: Segment Analyzer Compare overlay remains open on `/mtl/segments`.
