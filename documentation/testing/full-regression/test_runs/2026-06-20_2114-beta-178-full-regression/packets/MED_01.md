# Packet: MED_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_01
- In scope: Media-layer photo pin availability for the current quick-install target.
- Out of scope: Preview navigation, HEIC conversion, and broken-photo recovery; covered by later MED IDs.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_04 terminal.
- Required app/data state: Indexed geotagged media records available.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Commit credentials or private media; invent media records without a real watched-folder import.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_01 | Checked authenticated media endpoints, queued a manual MEDIA rescan, rechecked settled MEDIA indexer state, checked available media write paths, and checked current target filesystem access. | Toggling the media layer should show indexed photo pins or clusters. | BLOCKED. The target has zero indexed media records: `get-media-with-location-info=[]`, world bounds returns `[]`, and MEDIA indexer total is `0`. The app exposes read/content media endpoints and rescan only, with no media upload/create API. The watched folder is `/root/mtl-full-regression-2026-06-20_2114-beta-178-full-regression/data/media`, but current root SSH access is unavailable because the supplied setup access note is rejected after setup-time password rotation. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API counts, MEDIA rescan result, indexer status, API write-path check, media folder path, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no media records exist to render as pins.

## Timings

| Step | Timing |
|---|---:|
| Media availability and rescan check | <1 min |

## Handoff Notes

- Completed: MED_01 is terminal BLOCKED for this run state.
- Remaining unfinished coverage: MED_02 onward.
- Blocked or not applicable: Blocked by missing indexed media and unavailable current filesystem access to seed synthetic media into `data/media`.
- State left for the next packet: App data unchanged except a no-op MEDIA rescan request; media endpoints still return zero records.
