# Packet: TRD_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_03.
- In scope: repeated Track Details tab switching, content stability, URL state, and refetch-loop audit.
- Out of scope: graph control mutations.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_02.
- Required app/data state: populated #100000 detail payload already loaded.
- Required browser context: direct #100000 Track Details URL.

## Allowed Mutations

- Allowed: switch tabs repeatedly.
- Not allowed: reload or edit the track during the cycle.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_03 | Switched Overview, Graphs, Quality, Related, and Events in order twice; checked markers, URL, and matching server requests. | Tabs remain populated, keep state, and do not refetch in a loop. | All ten tab selections showed their expected populated marker; URL stayed `/track/100000`; zero detail/chart/related requests occurred after initial load. | PASS | [tab cycles](../assets/TRD_03-tab-cycles.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_03-tab-cycles.txt](../assets/TRD_03-tab-cycles.txt) | Two-cycle content/URL checks and no-refetch server-log interval. |

## Screenshot Evidence

No screenshot is needed for the temporal refetch criterion; the per-cycle markers and server-log interval are more direct.

## Timings

| Step | Timing |
|---|---:|
| Two five-tab cycles | 4.8 s |
| Post-cycle request audit | < 1 s |

## Handoff Notes

- Completed: TRD_03.
- Remaining unfinished coverage: TRD_04 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: #100000 Track Details open on Events.

