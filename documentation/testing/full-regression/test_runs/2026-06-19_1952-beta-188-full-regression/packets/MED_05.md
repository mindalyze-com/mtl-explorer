# Packet: MED_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_05
- In scope: Determine whether missing/broken-photo recovery UI can be exercised in this run.
- Out of scope: General error handling outside media; covered by ERR IDs later.

## Prerequisites

- Required previous coverage IDs or run packets: MED_04 BLOCKED.
- Required app/data state: Quick-install beta stack running; no indexed media record available to break safely.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Corrupt unrelated app data, use private media, or fabricate database rows.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_05 | Checked whether any indexed media record exists that could safely be made missing/broken, checked upload/API alternatives, and queued a MEDIA rescan. | A missing/broken photo shows a recoverable error instead of a blank sheet. | No media record exists to open or temporarily break, and the saved run lacks current SSH/filesystem access to seed and then move a synthetic media file under the documented `data/media` folder. The broken-photo recovery path is blocked until filesystem access is restored and at least one media record is indexed. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API, rescan, OpenAPI, documented media folder, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no media record exists to break or preview.

## Timings

| Step | Timing |
|---|---:|
| Broken-media fixture availability check | <1 min |

## Handoff Notes

- Completed: MED_05 is terminal as BLOCKED for this run state.
- Remaining unfinished coverage: HMO_01 onward.
- Blocked or not applicable: Blocked by missing current SSH/filesystem access to seed and manipulate synthetic media in `data/media`.
- State left for the next packet: App data unchanged except a no-op MEDIA rescan request; media endpoints still return zero records.
