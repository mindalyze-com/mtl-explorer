# Packet: FIT_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_01
- In scope: Import the validated FIT activity file with GPS positions.
- Out of scope: Indexing and UI visibility, covered by FIT_02.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05.
- Required app/data state: Watched import folder active; five public GPX tracks present.
- Required browser context: None.

## Allowed Mutations

- Allowed: Copy the staged official FIT sample into this run's watched folder.
- Not allowed: Modify or convert the source before import.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_01 | Copy validated Activity.fit into the watched run folder and compare checksum/size. | Byte-identical GPS-bearing FIT file enters the import flow. | 94,096-byte file copied at 23:52:53Z; source and destination SHA-256 match. | PASS | [assets/FIT_01-import.txt](../assets/FIT_01-import.txt); [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_01-import.txt](../assets/FIT_01-import.txt) | Watched-path copy time, byte size, and checksum equality. |
| [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) | Official source and GPS-bearing validation. |

## Screenshot Evidence

Not needed for the watched-folder copy; FIT UI evidence follows in FIT_02.

## Timings

| Step | Timing |
|---|---:|
| FIT watched-folder copy | <1 min |

## Handoff Notes

- Completed: Imported the byte-identical official FIT sample into the watched folder.
- Remaining unfinished coverage: None for FIT_01.
- Blocked or not applicable: None.
- State left for the next packet: FIT indexing is in progress or pending watcher pickup.
