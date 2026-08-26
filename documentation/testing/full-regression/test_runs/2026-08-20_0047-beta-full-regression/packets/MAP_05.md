# Packet: MAP_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_05
- In scope: Zoom precision on a selected track and visual integrity of rendered lines.
- Out of scope: Direction markers, covered by MAP_07.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_04.
- Required app/data state: Public GPX track #100000 retained and active.
- Required browser context: Matching Tracks table in the in-app browser.

## Allowed Mutations

- Allowed: Select a track and activate visible Zoom in controls.
- Not allowed: Alter track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_05 | Open public track #100000 and zoom from 1000 km through 50 km. | Detail/precision improves with no duplicate or broken lines. | Scale improved at every step; selected details and all three map canvases stayed stable with no loader. ACC_04 prevents reliable visual inspection of line duplication/breakage. | BLOCKED | [assets/MAP_05-zoom.txt](../assets/MAP_05-zoom.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

No product defect is asserted; the remaining visual assertion is blocked by the run's capture limitation.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_05-zoom.txt](../assets/MAP_05-zoom.txt) | Track identity, scale sequence, stable canvas state, and blocked assertion. |

## Screenshot Evidence

BLOCKED by ACC_04. The line-integrity assertion requires visual inspection that the accessible DOM cannot supply.

## Timings

| Step | Timing |
|---|---:|
| Track selection and five zoom steps | <2 min |

## Handoff Notes

- Completed: Track selection, zoom progression, and stable rendered-canvas checks.
- Remaining unfinished coverage: None; terminally blocked for the line-integrity visual assertion.
- Blocked or not applicable: BLOCKED by ACC_04 screenshot/canvas visual capture failure.
- State left for the next packet: Track #100000 selected at 50 km scale; details open.
