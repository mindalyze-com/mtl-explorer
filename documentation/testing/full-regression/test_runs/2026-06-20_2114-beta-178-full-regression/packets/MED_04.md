# Packet: MED_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_04
- In scope: Determine whether server-side HEIC display/conversion can be exercised in this run.
- Out of scope: Non-HEIC media preview behavior, covered by MED_03.

## Prerequisites

- Required previous coverage IDs or run packets: MED_03 terminal.
- Required app/data state: Indexed HEIC media record available.
- Required browser context: Authenticated app/API context using README credentials.

## Allowed Mutations

- Allowed: Read media APIs, queue manual MEDIA rescan, update packet/run-state.
- Not allowed: Fabricate database rows or use private media.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_04 | Used MED_01 live evidence to check indexed media availability and available media ingestion paths before attempting HEIC content conversion. | HEIC photos display correctly after server-side conversion. | BLOCKED. No HEIC media record exists because the target has zero indexed media records. The app exposes media content read endpoints but no media upload endpoint, and current filesystem access is unavailable to place a synthetic HEIC under the documented `data/media` folder. | BLOCKED | [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED-media-setup-blocked.txt](../assets/MED-media-setup-blocked.txt) | Authenticated media API counts, MEDIA rescan result, indexer status, API write-path check, media folder path, and SSH/filesystem blocker evidence. |

## Screenshot Evidence

Not captured; no HEIC media record exists to request or render.

## Timings

| Step | Timing |
|---|---:|
| HEIC fixture availability decision | <1 min |

## Handoff Notes

- Completed: MED_04 is terminal BLOCKED for this run state.
- Remaining unfinished coverage: MED_05 onward.
- Blocked or not applicable: Blocked by missing indexed media and unavailable current filesystem access to seed a synthetic HEIC into `data/media`.
- State left for the next packet: App data unchanged; media endpoints still return zero records.
