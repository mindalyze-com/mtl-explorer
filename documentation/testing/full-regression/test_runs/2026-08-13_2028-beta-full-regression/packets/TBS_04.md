# Packet: TBS_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_04.
- In scope: Statistics Tracks quick-view presets and browser-state retention.
- Out of scope: opening details, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_03.
- Required app/data state: no excluded or activity-less tracks; all twelve available.
- Required browser context: Walking search and Exploration sort active.

## Allowed Mutations

- Allowed: switch All, Excluded, Stats excluded, and No activity presets.
- Not allowed: change exclusion/activity data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_04 | Switched through every quick view and returned to All while monitoring search and sort. | Presets select the correct subset and preserve usable sorting/search. | Three expected empty subsets reported 0/0. All restored the Walking row at 1/12. Walking and Exploration remained active throughout. | PASS | [views](../assets/TBS_04-quick-views.txt), [retained browser state](../assets/TBS_03-visible-summary.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_04-quick-views.txt](../assets/TBS_04-quick-views.txt) | Preset counts, pressed state, and retained search/sort. |
| [assets/TBS_03-visible-summary.webp](../assets/TBS_03-visible-summary.webp) | Returned All view with the retained Walking result. |

## Screenshot Evidence

The shared browser screenshot shows the preserved searched result after returning to All.

## Timings

| Step | Timing |
|---|---:|
| Each preset switch | < 1 s |

## Handoff Notes

- Completed: TBS_04 is terminal `PASS`.
- Remaining unfinished coverage: TBS_05 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Statistics Tracks All view; Walking search; Exploration sort; one visible row.
