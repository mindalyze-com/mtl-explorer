# Packet: TBS_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_07.
- In scope: Statistics correctness for many, single, and empty resolved sets.
- Out of scope: import/delete freshness timeline, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_06.
- Required app/data state: twelve tracks with BICYCLE and WALKING categories.
- Required browser context: Filter, map, and Statistics Overview.

## Allowed Mutations

- Allowed: activate exact WALKING, clear categories, and restore all categories.
- Not allowed: change tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_07 | Compared Overview for all twelve, exact Track 100005, and no selected categories; restored All. | Statistics are correct for many, one, and zero tracks without stale sections. | Totals matched 12 and 1 exactly. The zero set showed a clean empty message without old totals. Restore returned to 12. | PASS | [cardinalities](../assets/TBS_07-cardinalities.txt), [many](../assets/TBS_06-overview.webp), [single](../assets/FLT_19-mobile-resumed.webp) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_07-cardinalities.txt](../assets/TBS_07-cardinalities.txt) | Exact map and Statistics values for all cardinalities. |
| [assets/TBS_06-overview.webp](../assets/TBS_06-overview.webp) | Many-track overview. |
| [assets/FLT_19-mobile-resumed.webp](../assets/FLT_19-mobile-resumed.webp) | One-track filtered state. |

## Screenshot Evidence

Representative many- and single-track working states are retained; the empty state is captured textually.

## Timings

| Step | Timing |
|---|---:|
| Each cardinality transition | < 1 s |

## Handoff Notes

- Completed: TBS_07 is terminal `PASS`.
- Remaining unfinished coverage: TBS_08 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: desktop Filter open; all categories active; twelve tracks.
