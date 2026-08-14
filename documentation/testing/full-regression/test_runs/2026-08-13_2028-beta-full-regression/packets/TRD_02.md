# Packet: TRD_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_02.
- In scope: Track Details overview, charts, related list, events, mini-map, and quality information.
- Out of scope: advanced tab interaction and graph controls, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01.
- Required app/data state: public GPX #100000 available.
- Required browser context: #100000 Track Details open from Statistics > Tracks.

## Allowed Mutations

- Allowed: switch detail tabs.
- Not allowed: edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_02 | Opened Overview, Graphs, Quality, Related, and Events on GPX #100000 and checked rendered map canvases. | Overview, charts, related list, events, mini-map, and quality info all load. | All five sections loaded populated content. Overview metrics, interactive charts, detailed quality data, ten next tracks, one detected break, and visible map canvases were present. | PASS | [section verification](../assets/TRD_02-section-verification.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_02-section-verification.txt](../assets/TRD_02-section-verification.txt) | Per-section loaded values and mini-map canvas count. |

## Screenshot Evidence

Existing run screenshots already cover working FIT detail tabs; this packet records fresh GPX section values as durable text.

## Timings

| Step | Timing |
|---|---:|
| Each tab change | < 1 s |
| Full five-section audit | < 5 s |

## Handoff Notes

- Completed: TRD_02.
- Remaining unfinished coverage: TRD_03 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: #100000 Track Details open on Overview.

