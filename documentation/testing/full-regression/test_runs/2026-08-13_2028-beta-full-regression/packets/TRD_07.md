# Packet: TRD_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_07.
- In scope: track-shape thumbnails in browser, filter, stats, related, and chooser list surfaces.
- Out of scope: full card/table behavior.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_06.
- Required app/data state: 12 visible tracks with varied geometry.
- Required browser context: signed-in desktop sheet navigation.

## Allowed Mutations

- Allowed: navigate between read-only list surfaces.
- Not allowed: alter data or filters beyond restoring the already-paused filter to active all-tracks state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_07 | Counted visible shape-preview elements in Stats Tracks, Filter Review tracks, Stats Overview, and Related; rechecked the overlap chooser evidence. | Small track-shape preview is visible in browser, filters, stats, related, and selection lists. | Previews were visible for all 12 browser/review rows, five overview cards, five related cards, and both chooser rows. | PASS | [preview inventory](../assets/TRD_07-shape-preview-inventory.txt), [chooser](../assets/MAP_09-overlap-chooser.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_07-shape-preview-inventory.txt](../assets/TRD_07-shape-preview-inventory.txt) | Per-surface visible preview counts. |
| [assets/MAP_09-overlap-chooser.webp](../assets/MAP_09-overlap-chooser.webp) | Two distinct selection-list thumbnails. |

## Screenshot Evidence

The compact chooser screenshot is below 85 KB and shows working selection-list previews.

## Timings

| Step | Timing |
|---|---:|
| Each surface inspection | < 1 s after navigation |
| Full inventory | < 2 min |

## Handoff Notes

- Completed: TRD_07.
- Remaining unfinished coverage: TRD_08 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Overview open; filter restored active with all 12 tracks.

