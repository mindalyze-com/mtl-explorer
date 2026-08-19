# Packet: IMP_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_02
- In scope: Import the five public GPX files through the README-documented watched folder.
- Out of scope: Index completion and UI refresh.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01.
- Required app/data state: Baseline captured; source fixtures preserved outside watcher.
- Required browser context: Signed-in browser may remain open.

## Allowed Mutations

- Allowed: Create one run-specific subfolder under `data/gpx/` and copy exactly the five GPX positives.
- Not allowed: Copy FIT, media, or segment fixtures in this step.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_02 | Copied exactly five public GPX files from preserved staging into a run-specific subfolder of the README-documented `./data/gpx/` watcher. | The five GPX sources enter the installed app's watched import folder without unrelated fixtures. | Watched file count changed from 0 to 5; names and byte sizes match the staged sources. No other fixture was copied. | PASS | [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt); [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_02-copy.txt](../assets/IMP_02-copy.txt) | Before/after watched file count and exact copied set. |
| [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) | Source hashes and structure. |

## Screenshot Evidence

Not applicable to the filesystem mutation; UI processing evidence follows in IMP_03-IMP_05.

## Timings

| Step | Timing |
|---|---:|
| Watched-folder copy | <1 s observed |

## Handoff Notes

- Completed: Exactly five GPX files entered the watched folder.
- Remaining unfinished coverage: None for IMP_02.
- Blocked or not applicable: None.
- State left for the next packet: Live watcher is expected to index five new files; do not rescan unless needed.
