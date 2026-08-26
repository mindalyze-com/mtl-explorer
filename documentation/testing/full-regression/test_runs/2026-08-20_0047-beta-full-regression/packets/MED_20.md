# Packet: MED_20

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_20
- In scope: Relevant activity add/replace/delete, old/new time-window queue bounds, persisted correlation updates, resolved-position changes, and no full media scan.
- Out of scope: Large-library query plans/timings, covered by MED_21.

## Prerequisites

- Required previous coverage IDs or run packets: MED_19 cleanup.
- Required app/data state: Eight-media baseline and track 100013.
- Required browser context: Track 100013 Media timeline for final restoration check.

## Allowed Mutations

- Allowed: Add, replace, and remove one fully synthetic GPX with narrow old/new activity windows.
- Not allowed: Rescan or alter the media library.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_20 | Add 08:00:30-08:02:30 activity, replace it with 08:08-08:16 activity, then delete it; inspect job counts, candidate rows, resolved positions, and final UI. | Only media in old/new windows are queued; correlations and resolved projections update; no full media scan occurs. | Add processed exactly 4 media; replace processed the 6-item old/new union then the 2 current-window items; delete processed exactly 2. Persisted candidates/selected points changed and fell back correctly. Media stayed at 8 with no rescan, and cleanup restored exact UI baseline. | PASS | [assets/MED_20-window-queue.txt](../assets/MED_20-window-queue.txt); [assets/MED_20-window-old.gpx](../assets/MED_20-window-old.gpx); [assets/MED_20-window-new.gpx](../assets/MED_20-window-new.gpx) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_20-window-queue.txt](../assets/MED_20-window-queue.txt) | Exact bounded job counts, database candidates/positions, no-rescan proof, and cleanup. |
| [assets/MED_20-window-old.gpx](../assets/MED_20-window-old.gpx) | Fully synthetic four-media old-window activity. |
| [assets/MED_20-window-new.gpx](../assets/MED_20-window-new.gpx) | Fully synthetic two-media replacement-window activity. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact job counts, database projections, and final UI equality are linked above.

## Timings

| Step | Timing |
|---|---:|
| Unique old-window add and correlation | 4 min |
| New-window replacement and correlation | 3 min |
| Delete, queue drain, and UI restoration | 3 min |

## Handoff Notes

- Completed: Bounded add/replace/delete correlation flow, resolved-position update/fallback, no media rescan, and cleanup.
- Remaining unfinished coverage: None for MED_20.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Window GPX is outside watched storage; track 100023 absent; media count 8; correlation queues empty; exact baseline UI restored.
