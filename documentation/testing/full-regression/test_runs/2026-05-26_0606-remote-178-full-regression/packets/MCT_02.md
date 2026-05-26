# Packet: MCT_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_02
- In scope: clicking a segment analyzer result.
- Out of scope: segment analyzer setup and cleanup.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_01.
- Required app/data state: Segment Analyzer result table open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: click a result row.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_02 | Clicked the Lannion result row in the Segment Analyzer table. | Clicking a result opens that track's details or segment view. | Track Details opened for `#100003` with Lannion overview metrics while Segment Analyzer remained available behind it. | PASS | `assets/MCT_02-result-click.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MCT_02-result-click.webp | Result click opened Track Details `#100003`. |

## Timings

| Step | Timing |
|---|---:|
| Result click | <2m |

## Handoff Notes

- Completed: MCT_02 terminal PASS.
- Remaining unfinished coverage: continue with MCT_03.
- Blocked or not applicable: none.
- State left for the next packet: Track Details open above Segment Analyzer.
