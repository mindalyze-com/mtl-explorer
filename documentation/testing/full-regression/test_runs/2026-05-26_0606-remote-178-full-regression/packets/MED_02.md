# Packet: MED_02

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: MED_02
- In scope: Pan/zoom media layer behavior and viewport-scoped loading.
- Out of scope: Pin preview navigation, HEIC conversion, and broken media handling.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01.
- Required app/data state: Photos & Media layer enabled; indexed JPEG media points near Arezzo and one HEIC point outside the current viewport.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Pan, zoom, and use search to return to a known viewport.
- Not allowed: Change indexed media files or media layer implementation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_02 | With Photos & Media enabled, started in the Arezzo viewport, panned and zoomed to a nearby west-Arezzo viewport, then returned to Arezzo. Queried media bounds for the populated and nearby empty viewports. | Media markers load for the current viewport instead of showing every indexed media item globally. | Arezzo showed the red cluster `2`; after pan/zoom away no media marker was visible; returning to Arezzo showed cluster `2` again. API bounds returned the two Arezzo media IDs for Arezzo bounds and `[]` for west-Arezzo bounds, while all indexed regression media globally contained three items. | PASS | assets/MED_02-pan-zoom-away.webp; assets/MED_02-return-arezzo.webp; assets/MED_02-viewport-api.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MED_02-pan-zoom-away.webp | Pan/zoomed west-Arezzo viewport with no media marker visible. |
| assets/MED_02-return-arezzo.webp | Returned Arezzo viewport with media cluster `2` visible again. |
| assets/MED_02-viewport-api.txt | API evidence for all media, populated Arezzo bounds, and empty nearby bounds. |

## Timings

| Step | Timing |
|---|---:|
| Pan/zoom, return, and API checks | ~3 min |

## Handoff Notes

- Completed: MED_02.
- Remaining unfinished coverage: MED_03 onward.
- Blocked or not applicable: None.
- State left for the next packet: Desktop map remains at Arezzo with Photos & Media enabled and the cluster `2` visible.
