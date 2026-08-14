# Packet: TBS_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_05.
- In scope: opening Track Details from a Track Browser row.
- Out of scope: Statistics Overview contents, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_04.
- Required app/data state: Track 100005 visible in Statistics Tracks.
- Required browser context: desktop table.

## Allowed Mutations

- Allowed: click the visible row.
- Not allowed: edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_05 | Clicked the visible Track 100005 row. | Track Details opens for that row. | Track Details opened for #100005 Walking with all detail tabs and populated Overview fields. | PASS | [result](../assets/TBS_05-row-details.txt), [details](../assets/TBS_05-row-details.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_05-row-details.txt](../assets/TBS_05-row-details.txt) | Exact row and opened detail identity. |
| [assets/TBS_05-row-details.webp](../assets/TBS_05-row-details.webp) | Track Details after row activation. |

## Screenshot Evidence

The compact WebP shows the correct Track Details surface.

## Timings

| Step | Timing |
|---|---:|
| Detail open | < 1 s |

## Handoff Notes

- Completed: TBS_05 is terminal `PASS`.
- Remaining unfinished coverage: TBS_06 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Track Details #100005 open from Statistics Tracks; filter paused.
