# Packet: MED_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_10
- In scope: Deleted-pin absence and remaining-photo display/navigation in the recorded viewport.
- Out of scope: Cache, pan/zoom, hard-reload, and database checks.

## Prerequisites

- Required previous coverage IDs or run packets: MED_09.
- Required app/data state: Browser has applied media freshness r32 after two removals.
- Required browser context: Signed-in desktop activity 100016 Photos tab.

## Allowed Mutations

- Allowed: Map-marker selection and photo-viewer navigation.
- Not allowed: Restore sources or alter media state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_10 | Returned to the MED_07 activity viewport, enumerated marker/timeline targets, opened a retained photo, and navigated through the full viewer selection. | Both deleted pins are absent and cannot open; retained photos display and navigate correctly. | Exactly four retained marker buttons/rows remain; deleted IDs 400003/400001 have no map, row, or Nearby target. Viewer traversed retained IDs 400005, 400002, 400000, and 400004 as 1/4 through 4/4. | PASS | [assets/MED_10-map-absence.txt](../assets/MED_10-map-absence.txt); [assets/MED_10-post-delete-map.jpg](../assets/MED_10-post-delete-map.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_10-map-absence.txt](../assets/MED_10-map-absence.txt) | Exact remaining marker, timeline, Nearby, and viewer navigation inventory. |
| [assets/MED_10-post-delete-map.jpg](../assets/MED_10-post-delete-map.jpg) | Durable same-viewport screenshot showing only retained photo markers. |

## Screenshot Evidence

- The post-delete activity screenshot can be compared directly with MED_07: the same Bern route remains, with four accessible retained-photo markers and neither deleted source.

## Timings

| Step | Timing |
|---|---:|
| Activity and Photos load | About 3 s |
| Each viewer navigation | Under 300 ms |

## Handoff Notes

- Completed: Recorded-viewport absence and retained-photo behavior.
- Remaining unfinished coverage: None for MED_10.
- Blocked or not applicable: None.
- State left for the next packet: Track 100016 Photos is open at the four-item retained state.
