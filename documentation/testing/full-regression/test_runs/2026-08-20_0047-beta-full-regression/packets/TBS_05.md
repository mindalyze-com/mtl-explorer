# Packet: TBS_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_05
- In scope: Track-browser row activation and matching details.
- Out of scope: Details content depth, covered by TRD packets.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_04.
- Required app/data state: Mosel track visible in Stats Tracks.
- Required browser context: Statistics track browser.

## Allowed Mutations

- Allowed: Activate one row.
- Not allowed: Edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_05 | Activate the Mosel track row. | Matching track details opens. | Details opened at track 100001 with matching name, Overview, and maps. | PASS | [assets/TBS_05-row-details.txt](../assets/TBS_05-row-details.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_05-row-details.txt](../assets/TBS_05-row-details.txt) | Source row and destination identity. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible route and details state are linked above.

## Timings

| Step | Timing |
|---|---:|
| Row activation and identity check | 1 min |

## Handoff Notes

- Completed: Row-to-details navigation.
- Remaining unfinished coverage: None for TBS_05.
- Blocked or not applicable: None.
- State left for the next packet: Mosel Track Details open over Statistics.
