# Packet: MED_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_01
- In scope: Media-layer photo pin availability for the current quick-install target.
- Out of scope: Preview navigation, HEIC conversion, and broken-photo recovery; covered by later MED IDs.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_04 PASS.
- Required app/data state: Quick-install beta stack running with current imported regression data.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Commit credentials or private media; invent media records without a real watched-folder import.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_01 | Checked authenticated media endpoints, queued a manual MEDIA rescan, rechecked settled indexer/media state, and checked whether media could be staged through an app upload/API path. | Toggling the media layer should show indexed photo pins or clusters. | The target has zero indexed media records, the app exposes no media upload endpoint, and the current saved run cannot write to the documented `data/media` watched folder because the SSH password was rotated during setup and is not stored in artifacts. Photo-pin rendering is therefore blocked until filesystem access is restored and synthetic media can be indexed. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API, rescan, OpenAPI, documented media folder, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no media records exist to render as pins.

## Timings

| Step | Timing |
|---|---:|
| Media availability and rescan check | <1 min |

## Handoff Notes

- Completed: MED_01 is terminal as BLOCKED for this run state.
- Remaining unfinished coverage: MED_02 onward.
- Blocked or not applicable: Blocked by missing current SSH/filesystem access to seed synthetic media into `data/media`.
- State left for the next packet: App data unchanged except a no-op MEDIA rescan request; media endpoints still return zero records.
