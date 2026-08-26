# Packet: MED_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_10
- In scope: Deleted-pin/card/navigation absence and correct display/navigation of the two remaining anchor photos at the recorded viewport.
- Out of scope: Pan/zoom/hard-reload/cache behavior covered by MED_11.

## Prerequisites

- Required previous coverage IDs or run packets: MED_09 settled rescan and applied freshness token.
- Required app/data state: Six active media rows; deletion backups outside watched tree.
- Required browser context: Authenticated track 100028 Media and recorded Bern main-map viewport.

## Allowed Mutations

- Allowed: UI navigation and read-only bounds inspection.
- Not allowed: Restore/move fixtures or mutate metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_10 | Reopened track Media after freshness apply, compared all marker/card/filmstrip entries, queried the recorded bounds, opened photo-a, used Next to photo-b, and returned via Open on main map. | Both deleted pins are absent/unopenable; two remaining photos still render and navigate correctly. | Timeline/map buttons and bounds contain exactly six remaining IDs with neither deleted ID. Photo-a rendered as 1/6, Next rendered photo-b as 2/6, and Open on main map selected photo-b at the 100 m viewport. | PASS | [assets/MED_10-post-delete-map.txt](../assets/MED_10-post-delete-map.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_10-post-delete-map.txt](../assets/MED_10-post-delete-map.txt) | Exact six-item UI/bounds set, deleted-ID absence, remaining-photo viewer/navigation, and map return. |

## Screenshot Evidence

Live desktop inspection confirmed the six-item activity map/list, both remaining photos, and selected 100 m main-map marker. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Media tab settlement | About 0.45 s |
| Viewer Next navigation | About 0.25 s |
| Open on main map | About 0.65 s |

## Handoff Notes

- Completed: Deleted UI/bounds absence, remaining anchor-photo display/navigation, and recorded viewport return.
- Remaining unfinished coverage: None for MED_10.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Main map at 100 m with photo-b selected; exact six-ID bounds baseline recorded.
