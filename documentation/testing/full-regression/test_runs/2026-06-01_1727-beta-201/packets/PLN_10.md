# Packet: PLN_10

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_10
- In scope: Existing planned route display while new route recomputation has trouble.
- Out of scope: Initial segment-downloading notice covered by PLN_09.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_09.
- Required app/data state: Saved temporary plan loaded in Planner.
- Required browser context: Authenticated desktop Chromium context.

## Allowed Mutations

- Allowed: Simulate a route recompute failure.
- Not allowed: Leave saved plan or route interception behind.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_10 | Loaded a saved plan, simulated `segment-downloading` on the next route request, and observed the existing route state. | Existing planned routes still display even when fetching new route data has trouble. | The loaded route remained visible with stats 0.83 km / 11 m ascent / 1 leg and the elevation profile stayed rendered while the segment-downloading notice was shown. | PASS | [assets/PLN_desktop-flow.txt](../assets/PLN_desktop-flow.txt), [assets/PLN_10-existing-plan-during-route-error.webp](../assets/PLN_10-existing-plan-during-route-error.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_desktop-flow.txt](../assets/PLN_desktop-flow.txt) | Route stats during simulated route trouble. |
| [assets/PLN_10-existing-plan-during-route-error.webp](../assets/PLN_10-existing-plan-during-route-error.webp) | Loaded route still visible with error notice. |

## Screenshot Evidence

**Loaded route still visible with error notice.**

![Loaded route still visible with error notice.](../assets/PLN_10-existing-plan-during-route-error.webp)

## Timings

| Step | Timing |
|---|---:|
| Existing plan during route trouble | 2026-06-01T23:04:30+0200 |

## Handoff Notes

- Completed: PLN_10 is terminal PASS.
- Remaining unfinished coverage: PLN_11 onward.
- Blocked or not applicable: None.
- State left for the next packet: Temporary plan deleted and final cleanup found no remaining prefixed saved route.
