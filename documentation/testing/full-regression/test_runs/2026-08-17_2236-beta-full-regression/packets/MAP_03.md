# Packet: MAP_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MAP_03
- In scope: Map propagation of the required import mutation through the freshness prompt.
- Out of scope: Repeating the import and creating duplicate sources.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_02 and IMP_02 through IMP_05.
- Required app/data state: Preserved evidence from the initial five-public-GPX import.
- Required browser context: The same browser context was kept through the original import flow.

## Allowed Mutations

- Allowed: Reuse the already completed required data-change flow.
- Not allowed: Re-import the fixtures solely to duplicate the mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Reconciled the preserved IMP_05 freshness action and post-action map/browser/filter/statistics evidence with this map assertion. | Newly imported tracks appear after accepting freshness reload, without a browser restart. | In the same browser, `New data available` > `Reload` changed the map from stale 2 Tracks to 5 Tracks and exposed all five imported names; no browser restart occurred. | PASS | [assets/MAP_03-freshness-flow.txt](../assets/MAP_03-freshness-flow.txt), [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MAP_03-freshness-flow.txt](../assets/MAP_03-freshness-flow.txt) | MAP_03 reconciliation of the preserved required mutation. |
| [assets/IMP_05-reload.txt](../assets/IMP_05-reload.txt) | Original live banner action and post-reload cross-view evidence. |

## Screenshot Evidence

Blocked by ACC_04; original direct DOM status evidence is preserved.

## Timings

| Step | Timing |
|---|---:|
| Original freshness reload | 5.4 s |
| Original cross-view verification | About 15 s |

## Handoff Notes

- Completed: Map propagation of newly imported tracks through the required freshness flow.
- Remaining unfinished coverage: None for MAP_03.
- Blocked or not applicable: None.
- State left for the next packet: Current 14-track browser remains unchanged; deletion mutation is still deferred.
