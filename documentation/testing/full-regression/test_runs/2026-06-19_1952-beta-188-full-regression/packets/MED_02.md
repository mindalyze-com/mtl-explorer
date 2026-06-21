# Packet: MED_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_02
- In scope: Determine whether viewport-scoped media loading can be exercised in this run.
- Out of scope: Non-media pan/zoom behavior, already covered by MAP IDs.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01 BLOCKED.
- Required app/data state: Quick-install beta stack running; no indexed media records available.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Add media without documented watched-folder filesystem access.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_02 | Checked world-bounds and with-location media APIs, queued MEDIA rescan, and verified the app has no media upload path. | Pan/zoom should load media only for the current viewport when indexed media exists. | Viewport loading cannot be exercised: the media APIs return zero records for all checked bounds, and the run cannot currently seed synthetic media into the documented `data/media` folder because current SSH/filesystem access is unavailable after setup password rotation. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API, rescan, OpenAPI, documented media folder, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no media records exist to load for any viewport.

## Timings

| Step | Timing |
|---|---:|
| Media viewport availability check | <1 min |

## Handoff Notes

- Completed: MED_02 is terminal as BLOCKED for this run state.
- Remaining unfinished coverage: MED_03 onward.
- Blocked or not applicable: Blocked by missing current SSH/filesystem access to seed synthetic media into `data/media`.
- State left for the next packet: App data unchanged except a no-op MEDIA rescan request; media endpoints still return zero records.
