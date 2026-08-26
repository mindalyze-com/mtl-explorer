# Packet: MAP_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_09
- In scope: Multi-track overlap chooser and opening a chosen detail.
- Out of scope: Closing/deselecting the result, covered by MAP_10.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_07 and MAP_08.
- Required app/data state: Public Mosel and Voie Verte tracks retained.
- Required browser context: Same-run signed-in desktop map.

## Allowed Mutations

- Allowed: Reuse durable same-run overlap interaction evidence.
- Not allowed: Infer overlap behavior from a list-only navigation path.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_09 | Evaluate the Bussang overlap click and chooser selections recorded in IMP_07. | A selection list appears; choosing one track opens its details. | The chooser listed exactly Mosel and Voie Verte once each; choosing each in turn opened `/track/100001` and `/track/100003` respectively. | PASS | [assets/MAP_09-overlap.txt](../assets/MAP_09-overlap.txt); [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_09-overlap.txt](../assets/MAP_09-overlap.txt) | Overlap location, exact chooser contents, and selected detail routes. |
| [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt) | Original line-click and overlap evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact accessible chooser contents and selected routes are preserved in text evidence.

## Timings

| Step | Timing |
|---|---:|
| Original five-track map interaction flow | 18 min |

## Handoff Notes

- Completed: Overlap chooser and chosen-detail verification.
- Remaining unfinished coverage: None for MAP_09.
- Blocked or not applicable: None.
- State left for the next packet: Current browser remains at the Bern map state.
