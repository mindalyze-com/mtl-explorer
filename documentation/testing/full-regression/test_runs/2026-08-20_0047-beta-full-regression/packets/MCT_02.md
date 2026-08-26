# Packet: MCT_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_02
- In scope: Open a measured result in track details/segment context.
- Out of scope: Compare multiple rows.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_01.
- Required app/data state: Four-row segment result table.
- Required browser context: Desktop Segment Analyzer.

## Allowed Mutations

- Allowed: Open a result row.
- Not allowed: Edit track metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_02 | Click the sample.geojson name link from the measured-result table. | Matching track details/segment view opens. | Track Details opened for #100010, MTL Synthetic Segment Alpha, with overview/map/tabs and 144.59 m distance. | PASS | [assets/MCT_02-open-result.txt](../assets/MCT_02-open-result.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_02-open-result.txt](../assets/MCT_02-open-result.txt) | Click target and opened track identity. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible track-detail state is linked above.

## Timings

| Step | Timing |
|---|---:|
| Open and verify result | 1 min |

## Handoff Notes

- Completed: Measured-result navigation.
- Remaining unfinished coverage: None for MCT_02.
- Blocked or not applicable: None.
- State left for the next packet: Track Details open over the Segment Analyzer result state.
