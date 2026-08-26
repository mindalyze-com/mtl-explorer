# Packet: MED_16

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_16
- In scope: Unsaved camera-offset preview, camera-clock-only changes, immutable EXIF GPS time, database non-mutation, and Reset.
- Out of scope: Saving/clearing a correction, covered by MED_17.

## Prerequisites

- Required previous coverage IDs or run packets: MED_13 through MED_15.
- Required app/data state: Eight-item baseline with zero saved correction rows.
- Required browser context: Track 100013 Media tab and Media tools.

## Allowed Mutations

- Allowed: Preview and reset a +1 hour camera-clock offset.
- Not allowed: Save the correction or alter EXIF metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_16 | Preview +1 hour, compare camera-clock and Photo GPS rows, inspect the database during preview, then Reset. | UI marks an unsaved preview; only camera-clock items change; EXIF GPS time stays fixed; Reset restores baseline without database change. | UI showed ACTIVE/Unsaved preview. All camera-clock items shifted outside the window; four Photo GPS rows kept the exact baseline times/distances/origins. SQL stayed unchanged. Reset restored all eight exact baseline rows and zero correction records. | PASS | [assets/MED_16-preview-reset.txt](../assets/MED_16-preview-reset.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_16-preview-reset.txt](../assets/MED_16-preview-reset.txt) | Baseline, unsaved preview, invariant GPS rows, SQL non-mutation, and exact Reset comparison. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact UI rows and read-only database checks are linked above.

## Timings

| Step | Timing |
|---|---:|
| Preview, database check, and Reset | 3 min |

## Handoff Notes

- Completed: Unsaved preview, GPS invariance, database non-mutation, and exact Reset.
- Remaining unfinished coverage: None for MED_16.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Eight-item baseline restored; Media tools remains open; no saved correction exists.
