# Packet: SRC_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_03
- In scope: Marker cleanup by explicit clear and by selecting another tool.
- Out of scope: Empty/no-result search messages.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_02.
- Required app/data state: Search result marker visible.
- Required browser context: Desktop map with semantic marker clear action.

## Allowed Mutations

- Allowed: Clear marker, repeat search, and select Map tool.
- Not allowed: Alter tracked map data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_03 | Cleared the marker, repeated selection, then switched to Map. | Marker is removed cleanly. | Both branches removed semantic Map marker and Clear search marker; underlying map/tool state stayed healthy. | PASS | [assets/SRC_03-clear.txt](../assets/SRC_03-clear.txt) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_03-clear.txt](../assets/SRC_03-clear.txt) | Both cleanup branches and semantic before/after state. |

## Screenshot Evidence

- SRC_02 preserves the marker-before state; semantic inventory confirms complete removal in both branches.

## Timings

| Step | Timing |
|---|---:|
| Explicit clear | Under 400 ms |
| Tool switch cleanup | Under 500 ms |

## Handoff Notes

- Completed: Both search-marker cleanup paths.
- Remaining unfinished coverage: None for SRC_03.
- Blocked or not applicable: None.
- State left for the next packet: Map tool open; no search marker remains.
