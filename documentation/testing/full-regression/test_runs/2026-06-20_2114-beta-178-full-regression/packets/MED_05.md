# Packet: MED_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_05
- In scope: Determine whether missing/broken-photo recovery UI can be exercised in this run.
- Out of scope: General error handling outside media; covered by ERR IDs later.

## Prerequisites

- Required previous coverage IDs or run packets: MED_04 terminal.
- Required app/data state: Indexed media record available that can safely be made missing/broken and then restored.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Corrupt unrelated app data, use private media, or fabricate database rows.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_05 | Used MED_01 live evidence to check whether any indexed media record exists that could safely be made missing/broken, checked upload/API alternatives, and queued a MEDIA rescan. | A missing/broken photo shows a recoverable error instead of a blank sheet. | BLOCKED. No media record exists to open or temporarily break, and current SSH/filesystem access is unavailable to seed and then move a synthetic media file under the documented `data/media` folder. The broken-photo recovery path is blocked until at least one media record can be indexed. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API counts, MEDIA rescan result, indexer status, API write-path check, media folder path, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no media record exists to break or preview.

## Timings

| Step | Timing |
|---|---:|
| Broken-media fixture availability decision | <1 min |

## Handoff Notes

- Completed: MED_05 is terminal BLOCKED for this run state.
- Remaining unfinished coverage: HMO_01 onward.
- Blocked or not applicable: Blocked by missing indexed media and unavailable current filesystem access to seed and manipulate synthetic media in `data/media`.
- State left for the next packet: App data unchanged; media endpoints still return zero records.
