# Packet: MED_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_07
- In scope: Pre-delete identity, checksums, coordinates, visible pins, MEDIA status, freshness token, and compact map screenshot.
- Out of scope: Removing or rescanning either source.

## Prerequisites

- Required previous coverage IDs or run packets: MED_06 and the completed media-correlation checks.
- Required app/data state: Six synthetic media sources and correlated activity 100016.
- Required browser context: Signed-in desktop activity Photos tab.

## Allowed Mutations

- Allowed: Read-only UI, API, filesystem, and database inspection.
- Not allowed: Remove or alter either deletion target.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_07 | Recorded exact watched paths, sizes, SHA-256 values, media/index IDs, EXIF and resolved coordinates, position origins, accessible pins, MEDIA status, freshness token, and screenshot. | Both targets are uniquely recorded before deletion; MEDIA is settled; disposable pins are visible. | Both exact fixture hashes matched. Media IDs 400003/400001 and index IDs 300022/300024 are successful at their expected GPS positions. MEDIA is 6/6 with zero pending/failures/removals, media freshness is r30, and all six pins are visible. | PASS | [assets/MED_07-pre-delete.txt](../assets/MED_07-pre-delete.txt); [assets/MED_07-pre-delete-map.jpg](../assets/MED_07-pre-delete-map.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_07-pre-delete.txt](../assets/MED_07-pre-delete.txt) | Exact identity, checksum, coordinates, index status, and freshness baseline. |
| [assets/MED_07-pre-delete-map.jpg](../assets/MED_07-pre-delete-map.jpg) | Durable 1280 x 720 compact activity-map screenshot containing all six disposable pins. |

## Screenshot Evidence

- The saved screenshot shows the Bern activity mini-map with all six synthetic photo pins before deletion: four blue Photo GPS markers and two brown Estimated markers.

## Timings

| Step | Timing |
|---|---:|
| Read-only source and database inspection | About 3 s |
| UI map load and pin enumeration | About 4 s |
| Screenshot capture | Under 1 s |

## Handoff Notes

- Completed: Full MED_07 pre-delete checkpoint.
- Remaining unfinished coverage: None for MED_07.
- Blocked or not applicable: None.
- State left for the next packet: Both deletion targets still exist unchanged in the watched folder; exact backups remain outside it.
