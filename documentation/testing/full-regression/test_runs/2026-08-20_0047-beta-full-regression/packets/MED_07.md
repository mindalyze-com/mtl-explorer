# Packet: MED_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_07
- In scope: Pre-delete identities, IDs, checksums, coordinates, visible pin paths, MEDIA status, freshness token, and required compact map screenshot for the two disposable deletion photos.
- Out of scope: Moving or rescanning the files.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08 and MED_06; MED_42 cleanup restored the eight-item baseline.
- Required app/data state: Both exact synthetic JPEG fixtures present and indexed.
- Required browser context: Authenticated activity 100028 and main map.

## Allowed Mutations

- Allowed: Read-only UI, checksum, API, and database inspection.
- Not allowed: Move/delete fixtures or mutate media metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_07 | Recorded both files/IDs/checksums/coordinates/origins/index status/token, inspected their named activity pins/cards, opened each viewer, and used Open on main map at the 100 m viewport. | Complete before-state plus a compact screenshot showing the disposable pins. | All functional/identity evidence is exact: 400004 and 400007 are successful EXIF photos, both named marker/card paths are visible, and each opens its selected main-map pin. The required durable screenshot cannot be saved because the run's screenshot capability is blocked by ACC_04. | BLOCKED | [assets/MED_07-delete-baseline.txt](../assets/MED_07-delete-baseline.txt); [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) |

## Issues

No product issue. The only blocked child is the required durable screenshot, already owned by ACC_04.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_07-delete-baseline.txt](../assets/MED_07-delete-baseline.txt) | Exact identities, locations, checksums, UI pin/card evidence, status/counts, and freshness token. |
| [assets/ACC_04-screenshot-block.txt](../assets/ACC_04-screenshot-block.txt) | Exhausted screenshot capability and unblock conditions. |

## Screenshot Evidence

None: ACC_04 prevents durable capture. Live desktop inspection directly confirmed both named activity markers and each selected main-map pin at 100 m.

## Timings

| Step | Timing |
|---|---:|
| Activity Media load | About 0.4 s |
| Each Open on main map transition | About 0.7 s |

## Handoff Notes

- Completed: Every required identity/status/token and both visible UI pin paths.
- Remaining unfinished coverage: None for MED_07; terminal BLOCKED only for the required screenshot.
- Blocked or not applicable: Durable screenshot under ACC_04.
- State left for the next packet: Both exact watched files still present; main map centered at the delete-b point, 100 m scale; baseline 8/8/8 and queues 0/0.
