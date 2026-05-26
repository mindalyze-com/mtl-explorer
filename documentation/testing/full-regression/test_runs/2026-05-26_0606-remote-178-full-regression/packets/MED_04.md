# Packet: MED_04

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: MED_04
- In scope: Verify HEIC photos display correctly through server-side conversion.
- Out of scope: Broken media handling.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01 through MED_03.
- Required app/data state: Indexed HEIC sample `MED_HEIC_01_IMG_5195.HEIC` with GPS metadata.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Navigate map to the HEIC media point and request the converted media content.
- Not allowed: Modify HEIC media file contents.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_04 | Queried HEIC media info/content, navigated the media layer to Blue Springs, Missouri, and clicked the HEIC media point. | HEIC media displays correctly, converted server-side for browser display. | Content endpoint returned `HTTP 200` with `Content-Type: image/jpeg`; ImageMagick identified the response as a 793x1024 JPEG. The UI preview displayed the converted flower image with filename `MED_HEIC_01_IMG_5195.HEIC` and Apple iPhone metadata. | PASS | assets/MED_04-heic-preview.webp; assets/MED_04-heic-api.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MED_04-heic-preview.webp | UI photo preview rendering the HEIC sample through converted content. |
| assets/MED_04-heic-api.txt | Media info, bounds, response headers, and file identification for server-side HEIC conversion. |

## Timings

| Step | Timing |
|---|---:|
| API conversion checks and UI preview | ~5 min |

## Handoff Notes

- Completed: MED_04.
- Remaining unfinished coverage: MED_05 onward.
- Blocked or not applicable: None.
- State left for the next packet: HEIC media preview remains open; media layer enabled near Blue Springs.
