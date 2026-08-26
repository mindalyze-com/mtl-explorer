# Packet: SRC_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SRC_04
- In scope: Verify clear empty and no-result location-search messages.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_03.
- Required app/data state: Location-search panel available.
- Required browser context: Authenticated main map.

## Allowed Mutations

- Allowed: Enter and clear a synthetic impossible place query.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_04 | Entered an impossible query, inspected the settled state, then cleared it. | Empty and no-result states have clear messages. | The no-result state said `No matches`; clearing showed `Search for a city, peak, or area`, with search/map controls intact. | PASS | [assets/SRC_04-empty-no-result.txt](../assets/SRC_04-empty-no-result.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SRC_04-empty-no-result.txt](../assets/SRC_04-empty-no-result.txt) | Exact no-result and cleared-query guidance. |

## Screenshot Evidence

Live desktop inspection confirmed both states. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| No-result settlement | About 1.3 s |
| Clear/guidance update | About 0.25 s |

## Handoff Notes

- Completed: No-result and empty-query messaging.
- Remaining unfinished coverage: None for SRC_04.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Search panel open with an empty query; map at Zürich 100 m.
