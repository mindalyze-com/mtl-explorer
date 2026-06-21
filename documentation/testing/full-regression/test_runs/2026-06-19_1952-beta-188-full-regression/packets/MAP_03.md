# Packet: MAP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_03
- In scope: Verify newly imported tracks from the required data-change flow appear without a full browser restart after accepting the freshness/reload prompt.
- Out of scope: Re-running the import mutation after later delete/FIT/FMT packets.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_05.
- Required app/data state: Freshness banner appeared after the five-GPX import.
- Required browser context: authenticated desktop browser context from the import flow.

## Allowed Mutations

- Allowed: Reuse the completed IMP_05 freshness reload evidence from this run.
- Not allowed: Add new tracks or recreate the import flow after the shared dataset has advanced.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Reused the completed IMP_05 action: clicked the visible `New data available` banner Reload action after the required five-GPX import, then checked map, stats, browser, filter, and API counts. | Newly imported tracks appear on the map without a full browser restart after accepting freshness/reload. | The banner Reload refreshed the existing browser session: map changed to `5 Tracks`; Stats, browser, Filter, and API counts all showed the five imported tracks. | PASS | [assets/IMP_05-refresh-surfaces.txt](../assets/IMP_05-refresh-surfaces.txt); [assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_05-refresh-surfaces.txt](../assets/IMP_05-refresh-surfaces.txt) | UI/API summary after accepting the freshness reload prompt. |
| [assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp) | Map count after freshness reload. |
| [assets/IMP_05-after-banner-reload.webp](../assets/IMP_05-after-banner-reload.webp) | Immediate state after the banner Reload action. |

## Screenshot Evidence

![Map after freshness reload](../assets/IMP_05-map-after-reload.webp)

## Timings

| Step | Timing |
|---|---:|
| Freshness reload map verification | Covered in IMP_05 (~3 min) |

## Handoff Notes

- Completed: MAP_03.
- Remaining unfinished coverage: MAP_04 onward.
- Blocked or not applicable: none.
- State left for the next packet: Current dataset is the later 11-track post-delete/post-FIT/post-FMT state; MAP_03 evidence comes from the earlier import flow packet.
