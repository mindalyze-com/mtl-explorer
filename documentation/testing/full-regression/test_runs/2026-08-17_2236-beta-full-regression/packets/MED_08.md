# Packet: MED_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_08
- In scope: Recoverable removal of exactly the two designated DAT_08 sources from the watched media folder.
- Out of scope: Rescan, index settlement, freshness reload, or UI absence checks.

## Prerequisites

- Required previous coverage IDs or run packets: MED_07.
- Required app/data state: Both targets verified unchanged and backed up.
- Required browser context: None; this is the prescribed disposable source mutation.

## Allowed Mutations

- Allowed: Move only the two deletion fixtures outside the watched tree.
- Not allowed: Alter the other four media sources or any non-DAT_08 data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_08 | Moved the two exact watched target paths into a run-specific recovery directory under data/logs, then enumerated the watched folder and verified both backup hashes. | Only the designated sources leave the watched folder; exact backups remain outside it. | The watched folder now contains exactly photo-a, photo-b, estimated-a, and estimated-b. Both deleted-target backups match their frozen hashes and are recoverable. | PASS | [assets/MED_08-source-removal.txt](../assets/MED_08-source-removal.txt) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_08-source-removal.txt](../assets/MED_08-source-removal.txt) | Exact mutation scope, remaining watched files, backup locations, and hashes. |

## Screenshot Evidence

- Not needed for the source-folder mutation; the required visual baseline is preserved by MED_07.

## Timings

| Step | Timing |
|---|---:|
| Resolve, move, and verify both files | About 1 s |

## Handoff Notes

- Completed: Exact recoverable removal of both designated media sources.
- Remaining unfinished coverage: None for MED_08.
- Blocked or not applicable: None.
- State left for the next packet: Four sources remain watched; both removed files are recoverably quarantined for MED_09-MED_12.
