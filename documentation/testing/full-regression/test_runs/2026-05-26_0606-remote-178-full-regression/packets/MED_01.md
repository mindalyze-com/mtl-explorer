# Packet: MED_01

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: MED_01
- In scope: Toggle the media layer and verify photo/media pins appear in the map view.
- Out of scope: Media viewport loading behavior, preview navigation, HEIC conversion, and broken media handling.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP through AVR_03.
- Required app/data state: Three geotagged media files indexed in the remote media folder; map logged in at the target app.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Toggle map data layers and pan/search the map.
- Not allowed: Change indexed media files or delete test media.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_01 | Opened the map settings sheet near Arezzo, toggled Photos & Media off, then toggled it back on. | Photo/media pins or clusters appear in the map view when the layer is enabled. | With the layer disabled no red media marker was visible; after enabling Photos & Media, the red cluster marker `2` appeared over the indexed Arezzo media coordinate. | PASS | assets/MED_01-media-layer-on.webp; assets/MED_01-media-api.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MED_01-media-layer-on.webp | Map settings sheet with Photos & Media enabled and the red media cluster visible in the map. |
| assets/MED_01-media-api.txt | API confirmation of the two in-bounds media points used for the visible cluster. |

## Timings

| Step | Timing |
|---|---:|
| Toggle off/on and verify cluster | ~1 min |

## Handoff Notes

- Completed: MED_01.
- Remaining unfinished coverage: MED_02 onward.
- Blocked or not applicable: None.
- State left for the next packet: Desktop map remains centered on Arezzo with Photos & Media enabled and map settings open.
