# Packet: SRC_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_03
- In scope: Clear location search and confirm the marker is removed cleanly.
- Out of scope: Empty/no-result messaging covered by SRC_04.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_02.
- Required app/data state: Selected Zürich marker present.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Clear the active search marker.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_03 | Activated `Clear search marker` and inspected the settled map controls. | Marker is removed cleanly. | Both marker controls disappeared while all normal map/navigation controls and 8-track state remained usable. | PASS | [assets/SRC_03-clear-marker.txt](../assets/SRC_03-clear-marker.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_03-clear-marker.txt](../assets/SRC_03-clear-marker.txt) | Before/after marker state and unaffected controls. |

## Screenshot Evidence

Live desktop inspection confirmed clean marker removal. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Marker clear and settlement | About 0.5 s |

## Handoff Notes

- Completed: Search-marker removal and post-clear control check.
- Remaining unfinished coverage: None for SRC_03.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: No search marker; map remains at the Zürich view.
