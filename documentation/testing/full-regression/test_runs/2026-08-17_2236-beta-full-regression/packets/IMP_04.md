# Packet: IMP_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: IMP_04
- In scope: Completed file status, failure absence, freshness change, and settled background jobs.
- Out of scope: Applying the freshness reload.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_03.
- Required app/data state: Five files indexed and follow-on jobs allowed to settle.
- Required browser context: Admin Processing and Data status.

## Allowed Mutations

- Allowed: Refresh Admin status.
- Not allowed: Apply the freshness reload before recording the out-of-sync token.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_04 | Refreshed Processing until all follow-on work ended, then recorded server/client freshness details before reloading. | All five sources complete with no unexpected GPS failure; freshness changes; Duplicate Finder and Exploration Score settle. | GPS shows 5 completed and no failure; Duplicate Finder, Activity Classifier, and Exploration Score all ended at 5/5. Server freshness changed from r0 to index r16/tracks r30/geometry r30 and Data status reports Out of sync. | PASS | [assets/IMP_04-completion.txt](../assets/IMP_04-completion.txt); [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_04-completion.txt](../assets/IMP_04-completion.txt) | Settled job states and post-import freshness tokens. |
| [assets/IMP_01-baseline.txt](../assets/IMP_01-baseline.txt) | Pre-import token comparison. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM status evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Track import and all follow-on jobs settled | About 75 s |

## Handoff Notes

- Completed: Five files and all import follow-on work reached terminal success states; freshness change captured.
- Remaining unfinished coverage: None for IMP_04.
- Blocked or not applicable: None.
- State left for the next packet: Client intentionally remains out of sync, ready for the freshness reload action.
