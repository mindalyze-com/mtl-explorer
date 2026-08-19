# Packet: HMO_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: HMO_03
- In scope: Live heatmap response to a real filter change and filter reset.
- Out of scope: Heatmap toggle/opacity and route overlays.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_02.
- Required app/data state: Heatmap enabled at 52%, filter reset, 13 tracks.
- Required browser context: Signed-in desktop main map at Bern.

## Allowed Mutations

- Allowed: Temporarily restrict categories to WALKING and reset the filter.
- Not allowed: Alter persisted track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_03 | Changed Included categories from all to WALKING only, compared the same viewport, then reset. | Heatmap updates with filtered tracks and restores after reset. | Count changed 13→2/13, track lines and density reduced to the filtered set; Reset returned 13 and repainted the broader density in place. | PASS | [assets/HMO_03-filter-update.txt](../assets/HMO_03-filter-update.txt); [assets/HMO_03-walking-only.jpg](../assets/HMO_03-walking-only.jpg); [assets/HMO_03-all-restored.jpg](../assets/HMO_03-all-restored.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/HMO_03-filter-update.txt](../assets/HMO_03-filter-update.txt) | Exact filter/count/update/reset sequence. |
| [assets/HMO_03-walking-only.jpg](../assets/HMO_03-walking-only.jpg) | Same viewport with WALKING-only 2/13 result and reduced heatmap. |
| [assets/HMO_03-all-restored.jpg](../assets/HMO_03-all-restored.jpg) | Same viewport after reset, with 13 tracks and broader density restored. |

## Screenshot Evidence

- The paired desktop screenshots show the same Bern viewport and media selection before/after Reset, while the track count and density layer change with the filter.

## Timings

| Step | Timing |
|---|---:|
| Filter apply to repaint | Under 1 s |
| Reset to restored repaint | Under 1 s |

## Handoff Notes

- Completed: Full filter-to-heatmap synchronization check.
- Remaining unfinished coverage: None for HMO_03.
- Blocked or not applicable: None.
- State left for the next packet: Filter reset to all 13 tracks; heatmap enabled at 52%; route overlays none.
