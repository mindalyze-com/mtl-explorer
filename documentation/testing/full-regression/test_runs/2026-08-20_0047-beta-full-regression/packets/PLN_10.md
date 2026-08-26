# Packet: PLN_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: PLN_10
- In scope: Preserve an existing planned route when fetching a new route fails.
- Out of scope: Mobile touch editing.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_09.
- Required app/data state: Existing 11 m route; disposable BRouter temporarily stopped.
- Required browser context: Desktop Planner.

## Allowed Mutations

- Allowed: Drag an endpoint to request a new route while BRouter is unavailable; restore BRouter.
- Not allowed: Lose the existing planned route or leave service degraded.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_10 | Capture existing route, drag an endpoint during outage, observe failure, restart BRouter. | Existing planned route remains visible and usable while the new fetch fails. | The 11 m/one-leg route, two-point chart, stats, and edit controls remained throughout unavailable/updating/unavailable and after recovery to ready. | PASS | [assets/PLN_10-route-preserved.txt](../assets/PLN_10-route-preserved.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_10-route-preserved.txt](../assets/PLN_10-route-preserved.txt) | Before/during/after outage preservation evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible route/stat/chart states are linked above.

## Timings

| Step | Timing |
|---|---:|
| Failed reroute and preservation check | 1 min |
| Service recovery check | 1 min |

## Handoff Notes

- Completed: Existing-route preservation through fetch trouble.
- Remaining unfinished coverage: None for PLN_10.
- Blocked or not applicable: None.
- State left for the next packet: BRouter ready; existing 11 m route retained.
