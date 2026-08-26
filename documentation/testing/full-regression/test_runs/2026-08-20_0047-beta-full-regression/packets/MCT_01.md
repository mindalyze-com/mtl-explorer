# Packet: MCT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_01
- In scope: Measure two zones and list crossing tracks with speed/time/distance.
- Out of scope: Open a result or compare selected tracks.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_11 and DAT_07.
- Required app/data state: Synthetic Bern fixtures copied into watched mct folder and indexed successfully.
- Required browser context: Desktop Segment Analyzer centered on Bern.

## Allowed Mutations

- Allowed: Import the two frozen synthetic segment fixtures and create temporary measurement zones.
- Not allowed: Use private GPX data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_01 | Place zones A/B over the synthetic local segment, analyze, and switch speed/time/distance metrics. | Crossing-track results appear with all three metrics. | Each zone matched four tracks; analysis returned four shared rows and every row exposed speed, time, and distance values. | PASS | [assets/MCT_01-measure-results.txt](../assets/MCT_01-measure-results.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_01-measure-results.txt](../assets/MCT_01-measure-results.txt) | Fixture import, zone counts, result rows, and metric values. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible status/table evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Activate synthetic fixtures | 2 min |
| Place/analyze zones | 2 min |
| Verify three metrics | 1 min |

## Handoff Notes

- Completed: Measurement result list with speed/time/distance.
- Remaining unfinished coverage: None for MCT_01.
- Blocked or not applicable: None.
- State left for the next packet: Segment Analyzer result table open with four selected tracks.
