# Packet: SRC_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_01
- In scope: Open location search, type a place, and receive clear results.
- Out of scope: Selecting/clearing result and no-result handling covered by SRC_02-04.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_05 with normal map usable.
- Required app/data state: Location-search sidecar ready; filter at 8 tracks.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Open search and enter `Zurich`.
- Not allowed: Select a result in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_01 | Opened Search location and entered `Zurich`; inspected returned result list and controls. | Results appear for a place query. | Results appeared in about 0.8 s with Zürich city first and multiple labelled Zurich neighbourhoods; sort controls and metadata were clear. | PASS | [assets/SRC_01-location-search.txt](../assets/SRC_01-location-search.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_01-location-search.txt](../assets/SRC_01-location-search.txt) | Exact query, controls, first result, and settlement. |

## Screenshot Evidence

Live desktop inspection confirmed populated Zurich results. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Zurich results | About 0.8 s |

## Handoff Notes

- Completed: Search open/query/results.
- Remaining unfinished coverage: None for SRC_01.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Search panel open on populated Zurich results; no result selected yet.
