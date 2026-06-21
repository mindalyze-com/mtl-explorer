# Packet: MCT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_05
- In scope: Sub-track/segment extraction between two selected trigger points on tracks.
- Out of scope: Compare chart rendering and map geometry sanity, covered by MCT_04 and MCT_06.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04
- Required app/data state: Synthetic shared-zone tracks `100017` and `100018` are imported.
- Required browser context: Authenticated API/browser state against `http://178.104.209.132:18080/mtl/`.

## Allowed Mutations

- Allowed: Read crossing and sub-track API endpoints.
- Not allowed: Modify imported tracks, filter state, or persisted metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Reused the MCT_04 A/B trigger points, read live crossing point IDs for tracks `100017` and `100018`, then called `/api/tracks/details/get-sub-track` for each A-to-B pair. | The API returns the expected inclusive segment slice between the selected points. | PASS. Both live sub-track requests returned HTTP 200. Track `100017` returned 14 points from `636846..636859`; track `100018` returned 16 points from `636827..636842`. Endpoints matched requested IDs, coordinates stayed in the local Bern-area bounds, distance/time were monotonic, and no zero-like coordinates appeared. | PASS | [assets/MCT_05-subtrack-slices.txt](../assets/MCT_05-subtrack-slices.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_05-subtrack-slices.txt](../assets/MCT_05-subtrack-slices.txt) | Live crossing and sub-track endpoint validation for both synthetic tracks. |

## Screenshot Evidence

Not applicable; this packet validates the extraction endpoint behind the Segment Compare UI.

## Timings

| Step | Timing |
|---|---:|
| Crossing and sub-track API validation | <1 min |

## Handoff Notes

- Completed: MCT_05 passed for inclusive sub-track slices on both synthetic tracks.
- Remaining unfinished coverage: MCT_06 onward.
- Blocked or not applicable: None for MCT_05.
- State left for the next packet: Synthetic tracks remain imported; no data mutation in this packet.
