# Packet: MED_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_02
- In scope: Media loads for current pan/zoom viewport rather than all world media at once.
- Out of scope: Viewer navigation.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01.
- Required app/data state: Enabled media layer and eight Bern media items.
- Required browser context: Main map at 100 m scale.

## Allowed Mutations

- Allowed: Pan far away, return by location search, and inspect request logs.
- Not allowed: Change media database rows.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_02 | Pan beyond the loaded Bern bounds, probe the empty viewport, then return to Bern. | Bounds requests follow viewport and only current-view media appears. | Far pan triggered new get-media-in-bounds calls and had no photo chooser; returning to Bern restored a chooser with exactly eight current-view photos. | PASS | [assets/MED_02-viewport-loading.txt](../assets/MED_02-viewport-loading.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_02-viewport-loading.txt](../assets/MED_02-viewport-loading.txt) | Pan/request/empty/return evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible chooser and server request evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Far pan and request check | 2 min |
| Return and cluster check | 1 min |

## Handoff Notes

- Completed: Viewport-scoped media loading.
- Remaining unfinished coverage: None for MED_02.
- Blocked or not applicable: None.
- State left for the next packet: Bern Open photos chooser open with eight-item cluster/current-view scopes.
