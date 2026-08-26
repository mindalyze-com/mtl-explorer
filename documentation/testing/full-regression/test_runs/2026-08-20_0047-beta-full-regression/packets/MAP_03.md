# Packet: MAP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_03
- In scope: New imports appearing after the required freshness/reload flow without a browser restart.
- Out of scope: Re-running the already completed required import flow.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02-IMP_05 and MAP_02.
- Required app/data state: The frozen run's five-public-GPX data-change flow completed in the same browser.
- Required browser context: Same signed-in browser that held the zero-track baseline.

## Allowed Mutations

- Allowed: Reuse durable evidence produced by the required earlier data-change flow.
- Not allowed: Perform a second substitute import flow or restart the browser.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Evaluate the frozen run's IMP_02-IMP_05 flow: live import, visible freshness prompt, in-app Reload, and map result. | Newly imported tracks appear without a full browser restart after accepting the prompt. | The same browser moved from 0 to 5 map tracks after the visible Reload; server/client tokens synchronized and Filter, Review Tracks, and Stats also showed 5. | PASS | [assets/MAP_03-import-freshness.txt](../assets/MAP_03-import-freshness.txt); [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_03-import-freshness.txt](../assets/MAP_03-import-freshness.txt) | MAP_03 mapping to the required data-change flow. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Detailed original freshness and cross-view evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; durable same-run UI state evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Original freshness reload and cross-view check | 3 min |

## Handoff Notes

- Completed: MAP_03 through the required prior data-change flow.
- Remaining unfinished coverage: None for MAP_03.
- Blocked or not applicable: None.
- State left for the next packet: Current nine-track dataset remains intact; deletion flow is still deferred.
