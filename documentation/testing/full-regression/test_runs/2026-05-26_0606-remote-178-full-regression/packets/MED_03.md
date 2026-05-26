# Packet: MED_03

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: MED_03
- In scope: Click a media pin, verify photo preview opens, and exercise previous/next navigation.
- Out of scope: HEIC rendering and broken media handling.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01, MED_02.
- Required app/data state: Photos & Media layer enabled and Arezzo media point visible.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Click media cluster/point and use preview navigation.
- Not allowed: Change media files or media index state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_03 | Clicked the Arezzo cluster, clicked the expanded media point, then used Previous and Next in the photo preview. | Photo preview opens; previous/next navigation works between media items. | Photo sheet opened with image `MED_JPEG_01_DSCN0010.jpg` at `2 / 2`; Previous moved to `MED_JPEG_02_DSCN0010_COPY.jpg` at `1 / 2`; Next returned to `MED_JPEG_01_DSCN0010.jpg` at `2 / 2`. | PASS | assets/MED_03-preview-2of2.webp; assets/MED_03-preview-1of2.webp; assets/MED_03-navigation.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MED_03-preview-2of2.webp | Photo preview open on the second Arezzo image. |
| assets/MED_03-preview-1of2.webp | Photo preview after Previous navigation to the first Arezzo image. |
| assets/MED_03-navigation.txt | DOM-observed preview counters, filenames, and content links before/after navigation. |

## Timings

| Step | Timing |
|---|---:|
| Cluster click, preview open, navigation | ~3 min |

## Handoff Notes

- Completed: MED_03.
- Remaining unfinished coverage: MED_04 onward.
- Blocked or not applicable: None.
- State left for the next packet: Media preview remains open on `MED_JPEG_01_DSCN0010.jpg` at `2 / 2`.
