# Packet: HMO_01

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: HMO_01
- In scope: Toggle heatmap, verify it draws over the map without hiding tracks, and verify opacity control behavior.
- Out of scope: Per-overlay Swiss/worldwide route layer coverage and filter-driven heatmap updates.

## Prerequisites

- Required previous coverage IDs or run packets: MED_05.
- Required app/data state: Track geometry loaded and visible near Lannion; map settings available.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Change map layer toggles and opacity settings.
- Not allowed: Change track or media data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_01 | Navigated to Lannion track geometry, captured heatmap enabled, toggled Heatmap off, then re-enabled it and lowered the opacity to about 38%. | Heatmap draws over the map without hiding tracks and opacity changes are reflected. | Heatmap density rendered around the track while the track line stayed visible above it. Turning Heatmap off removed the density overlay. Re-enabling with lower opacity restored a weaker overlay without hiding the track. | PASS | assets/HMO_01-heatmap-full.webp; assets/HMO_01-heatmap-off.webp; assets/HMO_01-heatmap-low-opacity.webp; assets/HMO_01-actions.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/HMO_01-heatmap-full.webp | Heatmap enabled at full opacity over visible Lannion track. |
| assets/HMO_01-heatmap-off.webp | Heatmap toggled off; track remains visible. |
| assets/HMO_01-heatmap-low-opacity.webp | Heatmap re-enabled at reduced opacity; track remains visible above it. |
| assets/HMO_01-actions.txt | Action sequence and state notes. |

## Timings

| Step | Timing |
|---|---:|
| Heatmap toggle and opacity checks | ~7 min |

## Handoff Notes

- Completed: HMO_01.
- Remaining unfinished coverage: HMO_02 onward.
- Blocked or not applicable: None.
- State left for the next packet: Lannion map viewport; Heatmap enabled at reduced opacity; worldwide MTB overlay is enabled and available for HMO_02 overlay coverage.
