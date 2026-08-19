# Packet: MED_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_11
- In scope: Pan/return, multi-level zoom, hard reload, immediate revisit, duplicate detection, and bounds cache policy.
- Out of scope: Database audit state.

## Prerequisites

- Required previous coverage IDs or run packets: MED_10.
- Required app/data state: Four retained media items after the applied deletion freshness change.
- Required browser context: Signed-in activity Photos and main map.

## Allowed Mutations

- Allowed: Map camera interaction, viewer-to-map navigation, and hard reload.
- Not allowed: Restore or alter media sources.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_11 | Panned away/back, zoomed across activity/main-map levels, hard-reloaded and immediately revisited, then repeated the live Bern bounds request. | Deleted pins never reappear, retained pins are unique, and bounds is not cached. | Every UI state contained the same four retained targets and no deleted name/ID. Both bounds responses were HTTP 200 with `Cache-Control: no-store` and four unique retained IDs. | PASS | [assets/MED_11-cache-resistance.txt](../assets/MED_11-cache-resistance.txt); [assets/MED_11-reload-map.jpg](../assets/MED_11-reload-map.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_11-cache-resistance.txt](../assets/MED_11-cache-resistance.txt) | Interaction sequence, reload inventory, exact bounds IDs, and no-store headers. |
| [assets/MED_11-reload-map.jpg](../assets/MED_11-reload-map.jpg) | Settled activity viewport after hard reload, showing four retained markers. |

## Screenshot Evidence

- The durable post-reload screenshot shows the same activity route with exactly two blue Photo GPS and two brown Estimated retained markers.

## Timings

| Step | Timing |
|---|---:|
| Each pan/zoom settle | Under 1 s |
| Hard reload to first marker inventory | About 3 s |
| Each repeated bounds request | Under 300 ms |

## Handoff Notes

- Completed: Full MED_11 stale-cache and duplicate-marker sequence.
- Remaining unfinished coverage: None for MED_11.
- Blocked or not applicable: None.
- State left for the next packet: Main map is centered on retained media near Bern; deleted source backups remain quarantined.
