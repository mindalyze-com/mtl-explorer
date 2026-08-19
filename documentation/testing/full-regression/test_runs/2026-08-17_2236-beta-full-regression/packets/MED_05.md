# Packet: MED_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_05
- In scope: Recoverable viewer behavior for a missing media source.
- Out of scope: Library deletion and rescan behavior, covered by MED_07-MED_12.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08 and MED_13-compatible correlated media data.
- Required app/data state: Six-photo activity 100016 and media 400005.
- Required browser context: Track Details Photos tab and activity mini-map.

## Allowed Mutations

- Allowed: Recoverably move one disposable media source, restore it, and retry the preview.
- Not allowed: Rescan or permanently delete the source.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_05 | Moved media 400005 out of the watched folder, opened it from the activity mini-map, restored the exact source, and selected Retry. | Missing/broken media shows an actionable error and recovers without a blank sheet. | Viewer retained metadata/navigation and showed Preview unavailable with Retry/Download; after hash-verified restoration, Retry decoded the image in place. | PASS | [assets/MED_05-missing-photo-recovery.txt](../assets/MED_05-missing-photo-recovery.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_05-missing-photo-recovery.txt](../assets/MED_05-missing-photo-recovery.txt) | Controlled missing-file state, visible error, restoration verification, and retry result. |

## Screenshot Evidence

Unavailable under ACC_04. Exact accessible alert/action/image states and the restored file hash provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Missing-source preview | About 1.3 s |
| Retry after restoration | About 1.5 s |

## Handoff Notes

- Completed: Missing-source recovery passed and the original media file is restored.
- Remaining unfinished coverage: None for MED_05.
- Blocked or not applicable: Screenshot evidence only, tracked by ACC_04.
- State left for the next packet: Viewer open on restored media 400005; source set intact.
