# Packet: MCT_05

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: MCT_05.
- In scope: extracting a measured sub-track between two selected zones.
- Out of scope: continent/global geometry sanity, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_04.
- Required app/data state: A and B zones across timed Lannion tracks; Compare open.
- Required browser context: desktop Segment Analyzer.

## Allowed Mutations

- Allowed: switch from A-A to A-B and inspect cards, chart, and map.
- Not allowed: alter track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_05 | Selected A-B for source GPX and GDB tracks, then inspected table deltas, comparison cards, charts, and map geometry. | The sub-track between the two crossings returns the expected populated slice. | Both timed tracks showed 9m09s but 0.00 m and 0.0 km/h; the table exposed -655 m, charts used a negative x range with zero values, and the map reduced the slice to a direct endpoint line. | FAIL | [invalid extraction](../assets/MCT_05-subtrack.webp), [exact values](../assets/MCT_05-subtrack.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MCT-05-P1 | P1 | A-B comparison extracts a zero-distance endpoint line from a timed segment. | Measure two Lannion zones, Analyze, select source GPX and GDB, Compare, then choose A-B. | A positive-distance recorded-track slice with consistent duration, speed, chart axes, and geometry. | 9m09s pairs with 0.00 m/0.0 km/h, -655 m table delta, negative chart x-axis, and endpoint-only line. | [screenshot](../assets/MCT_05-subtrack.webp), [values](../assets/MCT_05-subtrack.txt) | Segment comparison gives materially wrong user-visible metrics and shape. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MCT_05-subtrack.webp](../assets/MCT_05-subtrack.webp) | A-B cards and endpoint line. |
| [assets/MCT_05-subtrack.txt](../assets/MCT_05-subtrack.txt) | Exact table, chart, card, and control values. |

## Screenshot Evidence

![Invalid A-to-B sub-track](../assets/MCT_05-subtrack.webp)

## Timings

| Step | Timing |
|---|---:|
| A-B reload | 1.2 s |

## Handoff Notes

- Completed: MCT_05 is terminal `FAIL`; issue `MCT-05-P1` is open.
- Remaining unfinished coverage: MCT_06 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Compare open on the invalid local A-B segment.
