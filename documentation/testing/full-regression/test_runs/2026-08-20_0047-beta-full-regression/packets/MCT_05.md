# Packet: MCT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_05
- In scope: Extract the A-B sub-track slice between two measurement zones.
- Out of scope: Pixel-level geometry sanity.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04.
- Required app/data state: Four-track comparison with A-B sector selected.
- Required browser context: Segment Analyzer and comparison cards/charts.

## Allowed Mutations

- Allowed: Compare measured A-B distances/durations to each full synthetic track extent.
- Not allowed: Treat a whole-track result as a segment slice.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Select A-B and compare returned segment distance/time against each full route extent. | Each result is a non-empty bounded slice between the two zones. | Four non-empty A-B distances were shorter than their full track extents; timed fixtures also returned bounded durations shorter than full duration. | PASS | [assets/MCT_05-subtrack-slice.txt](../assets/MCT_05-subtrack-slice.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_05-subtrack-slice.txt](../assets/MCT_05-subtrack-slice.txt) | Sector selection and full-versus-slice comparisons. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; numeric slice bounds are linked above.

## Timings

| Step | Timing |
|---|---:|
| Validate extracted slices | 1 min |

## Handoff Notes

- Completed: Bounded A-B sub-track extraction.
- Remaining unfinished coverage: None for MCT_05.
- Blocked or not applicable: None.
- State left for the next packet: A-B four-track comparison remains open.
