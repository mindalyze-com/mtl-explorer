# Packet: MCT_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_05
- In scope: Server-side sub-track extraction between two measured crossing points.
- Out of scope: Compare overlay rendering, covered by MCT_04.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04.
- Required app/data state: 12 visible tracks, no active filter.
- Required browser context: Authenticated browser context for API access.

## Allowed Mutations

- Allowed: Read-only API calls.
- Not allowed: Track, planner, filter, or server data mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Called `/mtl/api/tracks/details/get-sub-track?trackDataPointFrom=607105&trackDataPointTo=607373`, using the usable A-B crossing IDs from the MCT_04 Compare overlay. | Sub-track/segment extraction returns the expected slice between the two points. | Endpoint returned HTTP 200 with 269 points. First ID was `607105`, last ID was `607373`, point indexes and timestamps were monotonic, and extracted line distance was `21867.8 m`, close to the crossing metric `21906.7 m`. | PASS | [assets/MCT_05-subtrack-extraction.txt](../assets/MCT_05-subtrack-extraction.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_05-subtrack-extraction.txt](../assets/MCT_05-subtrack-extraction.txt) | Sub-track endpoint response summary, endpoint IDs, monotonicity checks, and distance comparison. |

## Timings

| Step | Timing |
|---|---:|
| API extraction and validation | ~2s |

## Handoff Notes

- Completed: MCT_05 PASS.
- Remaining unfinished coverage: AVR_01 onward.
- Blocked or not applicable: None.
- State left for the next packet: No server data was changed.
