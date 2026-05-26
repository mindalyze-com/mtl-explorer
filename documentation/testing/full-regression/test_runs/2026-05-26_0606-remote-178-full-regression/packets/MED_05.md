# Packet: MED_05

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: MED_05
- In scope: Verify missing/broken photo handling in the media preview.
- Out of scope: General media indexing and HEIC conversion.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01 through MED_04.
- Required app/data state: Photos & Media layer enabled; disposable broken-after-index media file available.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Create and corrupt a disposable media file after indexing.
- Not allowed: Corrupt non-disposable user data. The original duplicate JPEG was restored before creating the disposable broken sample.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_05 | Indexed a fresh duplicate JPEG as `MED_BROKEN_AFTER_INDEX.jpg`, corrupted it after indexing, refreshed map data, and opened it from the media layer preview. | A missing/broken photo shows a recoverable error state instead of a blank sheet. | Preview opened, but the main media area was blank except for a broken-image icon/alt text. No retry/actionable error message appeared. The content endpoint returned `HTTP 200` with `Content-Type: image/jpeg` and a 0-byte body. | FAIL | assets/MED_05-broken-preview.webp; assets/MED_05-broken-media.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MED-01 | P2 | Broken media renders as a blank/broken image instead of a recoverable error. | Index a valid geotagged JPEG, replace the file with invalid bytes, then open it from the Photos & Media layer. | Preview should show an explicit recoverable error with a clear message and a way to dismiss, retry, or navigate away. | Server returns `200 image/jpeg` with an empty body, and the UI displays a blank/broken image area with no actionable error state. | assets/MED_05-broken-preview.webp; assets/MED_05-broken-media.txt | Broken or missing media can look like an empty preview sheet, leaving users without useful recovery guidance. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/MED_05-broken-preview.webp | UI preview showing the blank/broken image for the corrupted indexed media file. |
| assets/MED_05-broken-media.txt | Setup, endpoint response, and UI observation for the broken media test. |

## Timings

| Step | Timing |
|---|---:|
| Create/index/corrupt media and verify UI | ~8 min |

## Handoff Notes

- Completed: MED_05 with FAIL.
- Remaining unfinished coverage: HMO_01 onward.
- Blocked or not applicable: None.
- State left for the next packet: Disposable media `MED_BROKEN_AFTER_INDEX.jpg` remains corrupted and indexed as media ID 400003; media preview is open on the broken item.
