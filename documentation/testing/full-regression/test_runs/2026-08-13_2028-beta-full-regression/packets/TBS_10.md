# Packet: TBS_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_10.
- In scope: activation of a navigable Statistics Overview entry.
- Out of scope: highlight drilldowns, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_09.
- Required app/data state: all twelve tracks active; Track 100016 in Recent Activity.
- Required browser context: Statistics Overview.

## Allowed Mutations

- Allowed: click the Recent Activity entry.
- Not allowed: edit the track.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_10 | Activated Track 100016 from Statistics Recent Activity. | Entry navigates to or highlights its expected target. | Track Details opened for #100016 Bicycle with values matching the clicked card. | PASS | [navigation](../assets/TBS_10-stats-entry.txt), [details](../assets/TBS_10-recent-entry.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_10-stats-entry.txt](../assets/TBS_10-stats-entry.txt) | Source entry and destination identity. |
| [assets/TBS_10-recent-entry.webp](../assets/TBS_10-recent-entry.webp) | Correct Track Details destination. |

## Screenshot Evidence

The compact WebP shows the destination reached from the Statistics entry.

## Timings

| Step | Timing |
|---|---:|
| Entry navigation | < 1 s |

## Handoff Notes

- Completed: TBS_10 is terminal `PASS`.
- Remaining unfinished coverage: TBS_11 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Track Details #100016 open from Statistics Overview; all tracks active.
