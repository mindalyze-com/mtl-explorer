# Packet: MAP_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_08
- In scope: Click isolated rendered tracks and verify selection/details.
- Out of scope: Multi-track overlap chooser, covered by MAP_09.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07 and MAP_07.
- Required app/data state: Five public GPX-backed tracks retained.
- Required browser context: Same-run signed-in desktop map.

## Allowed Mutations

- Allowed: Reuse durable same-run interaction evidence from the required imported-track flow.
- Not allowed: Substitute list-row navigation for the recorded map-line clicks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_08 | Evaluate the isolated line clicks recorded in IMP_07. | Clicking one rendered track highlights/selects it and opens details. | Three isolated clicks opened the correct direct details for tracks 100004, 100002, and 100000, each with matching identity and embedded map. | PASS | [assets/MAP_08-single-track.txt](../assets/MAP_08-single-track.txt); [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_08-single-track.txt](../assets/MAP_08-single-track.txt) | MAP_08 mapping to the isolated direct line-click cases. |
| [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt) | Original per-track line-click evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the visible line-click outcomes are recorded in same-run text evidence.

## Timings

| Step | Timing |
|---|---:|
| Original five-track map interaction flow | 18 min |

## Handoff Notes

- Completed: Isolated line-click selection/details verification.
- Remaining unfinished coverage: None for MAP_08.
- Blocked or not applicable: None.
- State left for the next packet: Current browser remains at the Bern map state; original durable evidence is unchanged.
