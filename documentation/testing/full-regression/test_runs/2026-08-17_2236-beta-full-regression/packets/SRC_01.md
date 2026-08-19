# Packet: SRC_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_01
- In scope: Open location search, enter a place, and receive results.
- Out of scope: Selecting or clearing a result.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_05.
- Required app/data state: Healthy location-search sidecar and signed-in map.
- Required browser context: Desktop main map.

## Allowed Mutations

- Allowed: Presentation-only search text.
- Not allowed: Select a result before the packet checkpoint.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_01 | Opened Search and typed `Bern`. | Results appear. | Three structured visible results appeared, led by Bern, Switzerland; Importance/Near map and clear controls were available. | PASS | [assets/SRC_01-search.txt](../assets/SRC_01-search.txt); [assets/SRC_01-results.jpg](../assets/SRC_01-results.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_01-search.txt](../assets/SRC_01-search.txt) | Query, result labels/types, and controls. |
| [assets/SRC_01-results.jpg](../assets/SRC_01-results.jpg) | Durable search-results screenshot. |

## Screenshot Evidence

- The saved desktop image shows the populated Bern search sheet over the map.

## Timings

| Step | Timing |
|---|---:|
| Query to visible results | Under 1 s |

## Handoff Notes

- Completed: Normal place-name result retrieval.
- Remaining unfinished coverage: None for SRC_01.
- Blocked or not applicable: None.
- State left for the next packet: Search sheet open with Bern result first.
