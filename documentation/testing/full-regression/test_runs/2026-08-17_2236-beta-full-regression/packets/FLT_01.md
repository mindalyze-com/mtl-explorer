# Packet: FLT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_01
- In scope: Saved filter remains active after leaving/reopening Filter and is shown as an active chip.
- Out of scope: Parameter persistence covered by FLT_04.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_15.
- Required app/data state: Fresh install with fifteen tracks; a Tracks by year view saved in this packet.
- Required browser context: Filter sheet and main map.

## Allowed Mutations

- Allowed: Establish and persist Tracks by year as the FLT-suite baseline.
- Not allowed: Reset the saved filter before subsequent filter packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_01 | Applied Tracks by year, returned to the map, reloaded, and reopened Filter on the fixed build at desktop and mobile sizes. | Saved filter remains active and is identified in the map and Filter UI. | `Tracks by year` persisted and appeared in the map control and Filter Current result chip at both viewports. | FIXED | [details](../assets/FLT_01-remediation.txt); [desktop](../assets/FLT_01-fixed-desktop.webp); [mobile](../assets/FLT_01-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-005 | P2 | Persisted active filter is not shown as a chip. | Apply Tracks by year, leave Filter, then reopen it or inspect the map toolbar. | An active filter chip identifies the saved view. | The configuration card and toolbar icon indicate filtering, but no filter chip is rendered. | [assets/FLT_01-persistence.txt](../assets/FLT_01-persistence.txt) | Active filter is less explicit and fails the frozen filter-state visibility requirement. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_01-persistence.txt](../assets/FLT_01-persistence.txt) | Saved state, legend/count persistence, and visible-chip inventory. |

## Screenshot Evidence

Unavailable under ACC_04. Rendered labels, checked state, counts, and the visible chip-class inventory provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Establish view | About 2 s |
| Map leave/reload/reopen | About 3 s |
| Chip inventory | Under 1 s |

## Handoff Notes

- Completed: Saved-filter persistence and active-chip assertion.
- Remaining unfinished coverage: None for FLT_01; missing chip is terminal and tracked as FR-005.
- Blocked or not applicable: None.
- State left for the next packet: Filter open with Tracks by year active, all four categories included, no criteria.

## Remediation Verification

- Finding FR-005 is `FIXED`: active saved-view identity now remains visible after navigation and reload.
- Automated coverage verifies store hydration, map presentation, and the reopened Filter flow.
