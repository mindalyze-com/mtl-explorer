# Packet: MED_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_08
- In scope: Move exactly two disposable deletion photos out of the watched folder while retaining exact recoverable backups.
- Out of scope: Rescan/index settlement and UI removal.

## Prerequisites

- Required previous coverage IDs or run packets: MED_07 exact baseline.
- Required app/data state: Both deletion targets present under the run's watched fixture subdirectory.
- Required browser context: None; filesystem-only mutation prescribed by the frozen flow.

## Allowed Mutations

- Allowed: Recoverably move only the two named DAT_08 fixtures to an exact directory outside the watched tree.
- Not allowed: Move/delete any other media, rescan, or alter backup bytes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_08 | Created an outside-watched backup directory and moved only delete-a/delete-b into it; rehashed backups and enumerated remaining watched fixture names. | Both watched sources are absent, exact backups exist, and every other fixture remains untouched. | Both targets are absent from watched storage, both backup hashes match MED_07, and exactly the six expected unaffected media files remain. | PASS | [assets/MED_08-source-removal.txt](../assets/MED_08-source-removal.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_08-source-removal.txt](../assets/MED_08-source-removal.txt) | Exact moved names, recoverable location, checksums, absent watched paths, and remaining inventory. |

## Screenshot Evidence

Not applicable to this prescribed filesystem-only step.

## Timings

| Step | Timing |
|---|---:|
| Two recoverable moves and verification | Under 1 s |

## Handoff Notes

- Completed: Exact two-file recoverable move and unaffected-inventory verification.
- Remaining unfinished coverage: None for MED_08.
- Blocked or not applicable: None.
- State left for the next packet: Backups are in `data/logs/med08-delete-backup/`; watched tree has six fixture media; no rescan yet.
