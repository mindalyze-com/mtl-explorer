# Packet: SYN_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_07
- In scope: Indexer-running state is badged and does not block map interaction.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_02, ADM_04, and ADM_11 active-work sequences.
- Required app/data state: Same-run import/rescan activity.
- Required browser context: Admin plus main map.

## Allowed Mutations

- Allowed: No new mutation; consolidate same-run running-state evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_07 | Compared active Processing Live/import/rescan states with map interaction and final settlement. | Running state surfaces as a badge but does not block map interaction. | Processing Live and running/pending progress were explicit; close/zoom/navigation remained usable and reopened state settled normally. | PASS | [assets/SYN_07-running-badge.txt](../assets/SYN_07-running-badge.txt); [assets/ADM_04-rescans.txt](../assets/ADM_04-rescans.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_07-running-badge.txt](../assets/SYN_07-running-badge.txt) | Badge, progress, map interaction, and settlement matrix. |

## Screenshot Evidence

Live desktop inspection is documented in source packets. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Map close/zoom/reopen while work active | About 7 s |

## Handoff Notes

- Completed: Running badge and non-blocking map interaction.
- Remaining unfinished coverage: None for SYN_07.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Admin Data status open; current synchronized eight-track state.
