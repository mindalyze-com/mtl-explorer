# Packet: MED_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_04
- In scope: Determine whether server-side HEIC display/conversion can be exercised in this run.
- Out of scope: Non-HEIC media preview behavior, covered by MED_03.

## Prerequisites

- Required previous coverage IDs or run packets: MED_03 BLOCKED.
- Required app/data state: Quick-install beta stack running; no indexed HEIC media record available.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Fabricate database rows or use private media.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_04 | Checked indexed media availability and available media ingestion paths before attempting HEIC content conversion. | HEIC photos display correctly after server-side conversion. | No HEIC media record exists because the target has zero indexed media records. The app exposes media content read endpoints but no media upload endpoint, and this saved run lacks current filesystem access to place a synthetic HEIC under the documented `data/media` folder. HEIC conversion is blocked until an indexed HEIC fixture can be staged. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API, rescan, OpenAPI, documented media folder, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no HEIC media record exists to request or render.

## Timings

| Step | Timing |
|---|---:|
| HEIC fixture availability check | <1 min |

## Handoff Notes

- Completed: MED_04 is terminal as BLOCKED for this run state.
- Remaining unfinished coverage: MED_05 onward.
- Blocked or not applicable: Blocked by missing current SSH/filesystem access to seed a synthetic HEIC into `data/media`.
- State left for the next packet: App data unchanged except a no-op MEDIA rescan request; media endpoints still return zero records.
