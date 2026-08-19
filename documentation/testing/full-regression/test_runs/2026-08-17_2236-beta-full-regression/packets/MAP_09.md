# Packet: MAP_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_09
- In scope: Overlapping-track selection list and choosing one track.
- Out of scope: Arbitrary or guessed canvas coordinates.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_08 and DAT_07.
- Required app/data state: Frozen synthetic Bern overlap pair indexed and freshness accepted.
- Required browser context: Signed-in map with location search and 15 visible tracks.

## Allowed Mutations

- Allowed: Copy the two frozen synthetic fixtures to the watched folder and accept the freshness reload.
- Not allowed: Use private GPX data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_09 | Indexed the frozen Bern overlap pair, confirmed both in Review Tracks, centered Bern at 100 m, clicked the known shared zone at map center, then picked Segment B. | An overlap click opens a selection list; choosing one opens its details. | A six-track selection list opened and contained both synthetic A/B entries. Picking B opened `/mtl/track/100017` with the correct identity and metrics. | PASS | [assets/MAP_09-overlap-selection.txt](../assets/MAP_09-overlap-selection.txt), [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_09-overlap-selection.txt](../assets/MAP_09-overlap-selection.txt) | Fixture import, known-zone click, selection-list entries, and chosen detail evidence. |
| [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) | Frozen anonymized overlap-pair provenance and hashes. |

## Screenshot Evidence

Unavailable under ACC_04; the selection list and details were fully exposed in the semantic DOM.

## Timings

| Step | Timing |
|---|---:|
| Stage both frozen fixtures | Under 1 s |
| Processing and freshness acceptance | About 7 s |
| Center, click overlap, and choose B | About 6 s |

## Handoff Notes

- Completed: Controlled overlap-list opening and chosen-track detail navigation.
- Remaining unfinished coverage: None for MAP_09.
- Blocked or not applicable: Screenshot capture only; semantic evidence is complete.
- State left for the next packet: Segment B detail 100017 open at the 100 m Bern map view; current visible count 15.
