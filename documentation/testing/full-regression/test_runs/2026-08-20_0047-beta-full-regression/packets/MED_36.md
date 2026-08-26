# Packet: MED_36

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_36
- In scope: Both generated videos across MEDIA completion, main map, matching activity timeline, media navigation, statistics counts, and poster/indicator behavior.
- Out of scope: Native playback controls and HTTP range semantics covered by MED_37.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08 generated media fixture and MED_06 indexing baseline.
- Required app/data state: Eight-item media baseline including generated MOV 400000 and MP4 400001.
- Required browser context: Authenticated desktop browser.

## Allowed Mutations

- Allowed: Ephemeral viewer, map, activity, and statistics navigation.
- Not allowed: Media metadata, file, correlation, or location mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_36 | Verified MEDIA/database state and JPEG poster responses, then opened both videos from the matching activity, main map cluster/viewer, media navigation, and the populated Statistics media period/filter. | Both videos finish indexing and appear on every required surface; counts are 6 photos/2 videos; every thumbnail is a working JPEG poster with a visible video/play indicator. | Fixed locally: the map filmstrip lazily resolved visible item kinds; both adjacent synthetic videos showed play badges and Open video labels on desktop and mobile. Original indexing/count/poster checks remain valid. | FIXED | [original](../assets/MED_36-video-surfaces.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-017-018-fix-local-desktop.webp); [mobile](../assets/MTL-FR-017-018-fix-local-mobile.webp) |

## Issues

- MTL-FR-017 (P2, FIXED locally): visible filmstrip items with no supplied kind now resolve their metadata and render video labels/badges correctly.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_36-video-surfaces.txt](../assets/MED_36-video-surfaces.txt) | Exact index metadata, poster responses, surface results, counts, and failing MP4 map-filmstrip markup. |

## Screenshot Evidence

![Desktop video filmstrip](../assets/MTL-FR-017-018-fix-local-desktop.webp)

![Mobile video filmstrip](../assets/MTL-FR-017-018-fix-local-mobile.webp)

## Fix Record

- Visible filmstrip items lazily resolve missing media kinds and abort pending work on unmount.
- Full client suite 757/757 and direct desktop/mobile video-label checks pass.
- See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| MOV poster HTTP response | 69.309 ms |
| MP4 poster HTTP response | 55.796 ms |
| Viewer/mosaic transitions | Under 1 s each |

## Handoff Notes

- Completed: MEDIA completion, activity/map/navigation presence, JPEG poster integrity, video indicators, and exact 6-photo/2-video Statistics counts.
- Remaining unfinished coverage: None for MED_36.
- Blocked or not applicable: Durable screenshot saving is unavailable under ACC_04.
- State left for the next packet: Statistics Trends table open; eight-item media fixture unchanged; generated MP4 400001 ready for MED_37 playback/range testing.
