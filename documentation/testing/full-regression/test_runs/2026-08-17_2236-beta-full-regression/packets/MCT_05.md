# Packet: MCT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_05
- In scope: Sub-track extraction between two points on one track.
- Out of scope: Compare rendering and global geometry sanity.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04.
- Required app/data state: Synthetic four-point track 100017.
- Required browser context: Authenticated comparison session; live API check uses the same documented GUI account.

## Allowed Mutations

- Allowed: Read canonical track points and the sub-track endpoint.
- Not allowed: Modify track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Requested the slice from canonical point 637010 to 637012 on track 100017 and compared it with the full canonical sequence. | Extraction returns the expected inclusive slice. | HTTP 200 returned exactly IDs 637010, 637011, and 637012 (indexes 1-3), with monotonic timestamps/duration and local nonzero coordinates. | PASS | [assets/MCT_05-subtrack-slice.txt](../assets/MCT_05-subtrack-slice.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_05-subtrack-slice.txt](../assets/MCT_05-subtrack-slice.txt) | Requested endpoints, canonical/returned IDs, timestamps, durations, and bounds. |

## Screenshot Evidence

Not applicable. This packet validates the live extraction endpoint used by Segment Compare.

## Timings

| Step | Timing |
|---|---:|
| Login and live extraction validation | Under 1 s |

## Handoff Notes

- Completed: Exact inclusive sub-track extraction.
- Remaining unfinished coverage: None for MCT_05.
- Blocked or not applicable: None.
- State left for the next packet: Compare remains open; no data mutation.

