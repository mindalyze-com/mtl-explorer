# Packet: MCT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MCT_06
- In scope: Selected-segment geometry remains in real Bern bounds with no global line or [0,0]/South Africa jump.
- Out of scope: Animation/race geometry, covered by AVR_04.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_05.
- Required app/data state: Four selected local synthetic segments in Compare.
- Required browser context: Embedded comparison map.

## Allowed Mutations

- Allowed: Inspect the end-user comparison-map viewport and scale after selection.
- Not allowed: Use private tracks or backend geometry probes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_06 | Open A-B comparison and inspect its embedded map viewport after all four local segments load. | Map remains local with no global/off-continent geometry. | Embedded comparison map stayed 879x258 px at 100 m scale while all four Bern cards/series remained loaded; no world-scale refit or off-continent state occurred. | PASS | [assets/MCT_06-geometry-sanity.txt](../assets/MCT_06-geometry-sanity.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_06-geometry-sanity.txt](../assets/MCT_06-geometry-sanity.txt) | Local fixture bounds, comparison-map geometry, and scale evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; viewport scale/geometry and selected-series evidence are linked above.

## Timings

| Step | Timing |
|---|---:|
| Comparison-map geometry audit | 1 min |

## Handoff Notes

- Completed: Selected-segment local geometry sanity.
- Remaining unfinished coverage: None for MCT_06.
- Blocked or not applicable: Pixel screenshot unavailable; local map scale and viewport behavior passed.
- State left for the next packet: Compare sheet open with four selected tracks; its Race action is available for AVR coverage.
